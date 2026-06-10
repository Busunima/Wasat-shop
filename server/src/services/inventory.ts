import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import { computeTotalStock, type ProductVariant } from "../schemas/product.js";
import {
  parseInventoryCsv,
  type ImportRowError,
  type StockAdjust,
} from "../schemas/inventory.js";
import { logger } from "../lib/logger.js";

/**
 * Инвентарь (ТЗ §6 FR-A03): атомарные корректировки стока с историей inventoryLog
 * (stores/{storeId}/inventoryLog/{id}) и bulk CSV-импорт с отчётом об ошибках.
 * Уведомления о низком остатке — вместе с FCM (Фаза 4).
 */

export interface StockResult {
  productId: string;
  totalStock: number;
  variants: ProductVariant[];
}

function productRef(storeId: string, productId: string) {
  return db().collection("stores").doc(storeId).collection("products").doc(productId);
}

function logCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("inventoryLog");
}

function matchesSelector(
  variant: ProductVariant,
  selector: NonNullable<StockAdjust["variant"]>,
): boolean {
  if (selector.sku !== undefined) return variant.sku === selector.sku;
  return (
    (selector.size === undefined || variant.size === selector.size) &&
    (selector.color === undefined || variant.color === selector.color)
  );
}

/** Относительная корректировка остатка (FR-A03) — транзакция + inventoryLog. */
export async function adjustStock(
  storeId: string,
  productId: string,
  byUid: string,
  input: StockAdjust,
): Promise<StockResult> {
  const ref = productRef(storeId, productId);

  const result = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");
    const data = snap.data()!;
    const variants = ((data["variants"] as ProductVariant[]) ?? []).map((v) => ({ ...v }));

    let variantLabel: string | null = null;
    let totalStock: number;

    if (variants.length === 0) {
      // Товар без вариантов: totalStock корректируется напрямую
      if (input.variant) {
        throw new ApiError("VALIDATION_ERROR", "У товара нет вариантов");
      }
      const current = (data["totalStock"] as number) ?? 0;
      totalStock = current + input.delta;
      if (totalStock < 0) {
        throw new ApiError("CONFLICT", `Остаток не может стать отрицательным (${current})`);
      }
      tx.update(ref, { totalStock });
    } else {
      if (!input.variant) {
        throw new ApiError("VALIDATION_ERROR", "Укажите вариант (sku или size/color)");
      }
      const target = variants.find((v) => matchesSelector(v, input.variant!));
      if (!target) throw new ApiError("NOT_FOUND", "Вариант не найден");
      const next = target.stock + input.delta;
      if (next < 0) {
        throw new ApiError("CONFLICT", `Остаток не может стать отрицательным (${target.stock})`);
      }
      target.stock = next;
      totalStock = computeTotalStock(variants);
      variantLabel = target.sku ?? [target.size, target.color].filter(Boolean).join("/");
      tx.update(ref, { variants, totalStock });
    }

    tx.set(logCol(storeId).doc(randomUUID()), {
      productId,
      variant: variantLabel,
      delta: input.delta,
      reason: input.reason,
      byUid,
      at: FieldValue.serverTimestamp(),
    });

    return { productId, totalStock, variants };
  });

  logger.info("Сток скорректирован", { storeId, productId, delta: input.delta });
  return result;
}

export interface ImportReport {
  applied: number;
  /** Ошибки парсинга CSV и применения (sku не найден и т.п.). */
  errors: ImportRowError[];
}

/**
 * Bulk CSV-импорт остатков «sku,stock» (FR-A03): абсолютные значения по sku вариантов
 * (и sku товара без вариантов). Каждая строка применяется отдельной транзакцией,
 * ошибки собираются в отчёт; валидные строки применяются независимо от невалидных.
 */
export async function importStockCsv(
  storeId: string,
  byUid: string,
  csv: string,
): Promise<ImportReport> {
  const { rows, errors } = parseInventoryCsv(csv);
  const allErrors: ImportRowError[] = [...errors];
  let applied = 0;

  // Индекс sku -> productId по всем товарам магазина (масштаб MVP: <=10k товаров)
  const productsSnap = await db()
    .collection("stores")
    .doc(storeId)
    .collection("products")
    .get();
  const bySku = new Map<string, { productId: string; isVariant: boolean }>();
  for (const doc of productsSnap.docs) {
    const data = doc.data();
    const productSku = data["sku"] as string | null;
    if (productSku) bySku.set(productSku, { productId: doc.id, isVariant: false });
    for (const v of (data["variants"] as ProductVariant[]) ?? []) {
      if (v.sku) bySku.set(v.sku, { productId: doc.id, isVariant: true });
    }
  }

  for (const row of rows) {
    const found = bySku.get(row.sku);
    if (!found) {
      allErrors.push({ line: row.line, raw: row.sku, message: "sku не найден" });
      continue;
    }
    try {
      // Абсолютное значение через дельту внутри транзакции
      await db().runTransaction(async (tx) => {
        const ref = productRef(storeId, found.productId);
        const snap = await tx.get(ref);
        if (!snap.exists) throw new ApiError("NOT_FOUND", "Товар не найден");
        const data = snap.data()!;

        if (!found.isVariant) {
          const current = (data["totalStock"] as number) ?? 0;
          tx.update(ref, { totalStock: row.stock });
          tx.set(logCol(storeId).doc(randomUUID()), {
            productId: found.productId,
            variant: null,
            delta: row.stock - current,
            reason: "csv-import",
            byUid,
            at: FieldValue.serverTimestamp(),
          });
        } else {
          const variants = ((data["variants"] as ProductVariant[]) ?? []).map((v) => ({
            ...v,
          }));
          const target = variants.find((v) => v.sku === row.sku);
          if (!target) throw new ApiError("NOT_FOUND", "Вариант не найден");
          const delta = row.stock - target.stock;
          target.stock = row.stock;
          tx.update(ref, { variants, totalStock: computeTotalStock(variants) });
          tx.set(logCol(storeId).doc(randomUUID()), {
            productId: found.productId,
            variant: row.sku,
            delta,
            reason: "csv-import",
            byUid,
            at: FieldValue.serverTimestamp(),
          });
        }
      });
      applied += 1;
    } catch (err) {
      allErrors.push({
        line: row.line,
        raw: row.sku,
        message: err instanceof ApiError ? err.message : "ошибка применения",
      });
    }
  }

  logger.info("CSV-импорт остатков", { storeId, applied, errors: allErrors.length });
  return { applied, errors: allErrors };
}

export interface InventoryLogEntry {
  productId: string;
  variant: string | null;
  delta: number;
  reason: string;
  byUid: string;
  at: number | null;
}

/** История изменений остатков (FR-A03). */
export async function listInventoryLog(
  storeId: string,
  productId: string | undefined,
  limit: number,
): Promise<InventoryLogEntry[]> {
  let query: FirebaseFirestore.Query = logCol(storeId).orderBy("at", "desc").limit(limit);
  if (productId) query = logCol(storeId).where("productId", "==", productId).limit(limit);

  const snap = await query.get();
  return snap.docs.map((doc) => {
    const data = doc.data();
    const at = data["at"];
    return {
      productId: data["productId"] as string,
      variant: (data["variant"] as string | null) ?? null,
      delta: data["delta"] as number,
      reason: data["reason"] as string,
      byUid: data["byUid"] as string,
      at: at instanceof Timestamp ? at.toMillis() : null,
    };
  });
}
