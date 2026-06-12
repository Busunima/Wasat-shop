import { createHash } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  RESTOCK_ON_CANCEL,
  canTransition,
  computeTotals,
  type CheckoutInput,
} from "../schemas/order.js";
import { isCancellableByBuyer, type OrderStatus } from "../schemas/orderStatus.js";
import { applyPromo, type PromoEvaluable } from "../schemas/promocode.js";
import { computeTotalStock, type ProductVariant } from "../schemas/product.js";
import { recordCustomerType, recordEvent } from "./analytics.js";
import { buildOrderStatusNotification, sendToUsers } from "./push.js";
import { crossedLowStock, notifyLowStock, type LowStockAlert } from "./lowStock.js";
import { DEFAULT_LOW_STOCK_THRESHOLD } from "../schemas/store.js";
import { logger } from "../lib/logger.js";

/**
 * Ядро заказов (ТЗ §FR-B05/A04/B06, §10.1). Чекаут — одна транзакция Firestore:
 * серверный пересчёт цен → проверка и атомарное списание стока → создание заказа →
 * учёт промокода. Идемпотентность: id заказа детерминирован от uid+idempotencyKey,
 * повтор возвращает существующий заказ. Оплата deferred — Stripe PaymentIntent
 * встраивается в эту же транзакцию при появлении ключей (ТЗ §10.1).
 */

export interface OrderItem {
  productId: string;
  name: string;
  qty: number;
  /** Цена за единицу на момент заказа (минорные) — серверный снапшот. */
  price: number;
  variant: {
    sku?: string | undefined;
    size?: string | undefined;
    color?: string | undefined;
  } | null;
}

export interface ApiOrder {
  id: string;
  customerUid: string;
  customerEmail: string;
  items: OrderItem[];
  subtotal: number;
  tax: number;
  discount: number;
  total: number;
  currency: string;
  promoCode: string | null;
  status: OrderStatus;
  delivery: { method: string; address: string | null; cost: number; trackingNo: string | null };
  payment: { method: string; paidAt: number | null };
  createdAt: number | null;
}

function ordersCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("orders");
}

function productsCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("products");
}

function toApiOrder(data: FirebaseFirestore.DocumentData): ApiOrder {
  const createdAt = data["createdAt"];
  const paidAt = data["payment"]?.["paidAt"];
  return {
    id: data["id"] as string,
    customerUid: data["customerUid"] as string,
    customerEmail: (data["customerEmail"] as string) ?? "",
    items: (data["items"] as OrderItem[]) ?? [],
    subtotal: (data["subtotal"] as number) ?? 0,
    tax: (data["tax"] as number) ?? 0,
    discount: (data["discount"] as number) ?? 0,
    total: (data["total"] as number) ?? 0,
    currency: data["currency"] as string,
    promoCode: (data["promoCode"] as string | null) ?? null,
    status: data["status"] as OrderStatus,
    delivery: {
      method: (data["delivery"]?.["method"] as string) ?? "pickup",
      address: (data["delivery"]?.["address"] as string | null) ?? null,
      cost: (data["delivery"]?.["cost"] as number) ?? 0,
      trackingNo: (data["delivery"]?.["trackingNo"] as string | null) ?? null,
    },
    payment: {
      method: (data["payment"]?.["method"] as string) ?? "deferred",
      paidAt: paidAt instanceof Timestamp ? paidAt.toMillis() : null,
    },
    createdAt: createdAt instanceof Timestamp ? createdAt.toMillis() : null,
  };
}

/** Селектор варианта позиции → вариант товара (та же семантика, что в инвентаре). */
function findVariant(
  variants: ProductVariant[],
  selector: NonNullable<OrderItem["variant"]>,
): ProductVariant | undefined {
  if (selector.sku !== undefined) return variants.find((v) => v.sku === selector.sku);
  return variants.find(
    (v) =>
      (selector.size === undefined || v.size === selector.size) &&
      (selector.color === undefined || v.color === selector.color),
  );
}

/** Детерминированный id заказа: повтор чекаута с тем же ключом → тот же документ. */
export function orderIdFor(uid: string, idempotencyKey: string): string {
  return createHash("sha256").update(`${uid}:${idempotencyKey}`).digest("hex").slice(0, 32);
}

/** Транзакционный чекаут (FR-B05). Возвращает заказ; повтор по ключу — тот же заказ. */
export async function createOrder(
  uid: string,
  email: string,
  input: CheckoutInput,
): Promise<{ order: ApiOrder; replay: boolean }> {
  const { storeId } = input;
  const orderId = orderIdFor(uid, input.idempotencyKey);
  const orderRef = ordersCol(storeId).doc(orderId);

  const result = await db().runTransaction(async (tx) => {
    // Идемпотентный повтор — вернуть существующий заказ без побочных эффектов
    const existing = await tx.get(orderRef);
    if (existing.exists) {
      return {
        data: existing.data()!,
        replay: true,
        ownerUid: null as string | null,
        lowStock: [] as LowStockAlert[],
      };
    }

    const storeSnap = await tx.get(db().collection("stores").doc(storeId));
    const store = storeSnap.data();
    if (!store || store["isBlocked"] === true) {
      throw new ApiError("NOT_FOUND", "Магазин не найден");
    }

    // Снапшоты товаров: цена и сток читаются сервером (клиентским не доверяем, §13)
    const productSnaps = await Promise.all(
      input.items.map((item) => tx.get(productsCol(storeId).doc(item.productId))),
    );

    let promo: PromoEvaluable | null = null;
    if (input.promoCode) {
      const promoSnap = await tx.get(
        db().collection("stores").doc(storeId).collection("promocodes").doc(input.promoCode),
      );
      const p = promoSnap.data();
      if (!p) throw new ApiError("PROMO_INVALID", "Промокод не найден");
      promo = {
        type: p["type"],
        value: (p["value"] as number) ?? 0,
        minAmount: (p["minAmount"] as number) ?? 0,
        startsAtMs: p["startsAt"] instanceof Timestamp ? p["startsAt"].toMillis() : null,
        expiresAtMs: p["expiresAt"] instanceof Timestamp ? p["expiresAt"].toMillis() : null,
        usageLimit: (p["usageLimit"] as number | undefined) ?? null,
        usedCount: (p["usedCount"] as number) ?? 0,
        scope: (p["scope"] as PromoEvaluable["scope"]) ?? null,
        active: p["active"] === true,
      };
    }

    // Пересчёт позиций + списание стока
    const items: OrderItem[] = [];
    const itemCategories: string[] = [];
    const lowStock: LowStockAlert[] = [];
    const threshold =
      (store["lowStockThreshold"] as number | undefined) ?? DEFAULT_LOW_STOCK_THRESHOLD;
    let subtotal = 0;

    input.items.forEach((item, i) => {
      const snap = productSnaps[i]!;
      const product = snap.data();
      if (!product || product["status"] !== "active") {
        throw new ApiError("NOT_FOUND", `Товар недоступен: ${item.productId}`);
      }
      const price = product["price"] as number;
      const name = product["name"] as string;
      const variants = ((product["variants"] as ProductVariant[]) ?? []).map((v) => ({ ...v }));
      if (product["category"]) itemCategories.push(product["category"] as string);

      if (variants.length === 0) {
        const stock = (product["totalStock"] as number) ?? 0;
        if (stock < item.qty) {
          throw new ApiError("OUT_OF_STOCK", `Недостаточно остатка: ${name}`, {
            productId: item.productId,
            available: stock,
          });
        }
        const remaining = stock - item.qty;
        tx.update(snap.ref, { totalStock: remaining });
        if (crossedLowStock(stock, remaining, threshold)) {
          lowStock.push({ productId: item.productId, name, remaining });
        }
      } else {
        if (!item.variant) {
          throw new ApiError("VALIDATION_ERROR", `Укажите вариант товара: ${name}`);
        }
        const target = findVariant(variants, item.variant);
        if (!target) throw new ApiError("NOT_FOUND", `Вариант не найден: ${name}`);
        if (target.stock < item.qty) {
          throw new ApiError("OUT_OF_STOCK", `Недостаточно остатка: ${name}`, {
            productId: item.productId,
            available: target.stock,
          });
        }
        const before = (product["totalStock"] as number) ?? computeTotalStock(variants);
        target.stock -= item.qty;
        const remaining = computeTotalStock(variants);
        tx.update(snap.ref, { variants, totalStock: remaining });
        if (crossedLowStock(before, remaining, threshold)) {
          lowStock.push({ productId: item.productId, name, remaining });
        }
      }

      subtotal += price * item.qty;
      items.push({
        productId: item.productId,
        name,
        qty: item.qty,
        price,
        variant: item.variant ?? null,
      });
    });

    // Промокод: валидация на серверных суммах + учёт применения
    let discount = 0;
    let freeShipping = false;
    if (promo && input.promoCode) {
      const promoResult = applyPromo(promo, {
        subtotal,
        itemProductIds: items.map((it) => it.productId),
        itemCategories,
        nowMs: Date.now(),
      });
      if (!promoResult.valid) {
        throw new ApiError("PROMO_INVALID", promoResult.reason ?? "Промокод не применим");
      }
      discount = promoResult.discount;
      freeShipping = promoResult.freeShipping;
      tx.update(
        db().collection("stores").doc(storeId).collection("promocodes").doc(input.promoCode),
        { usedCount: FieldValue.increment(1) },
      );
    }

    const { delivery, total } = computeTotals({
      subtotal,
      discount,
      freeShipping,
      method: input.delivery.method,
      storeDeliveryCost: (store["deliveryCost"] as number | undefined) ?? null,
    });

    const orderData = {
      id: orderId,
      customerUid: uid,
      customerEmail: input.customerEmail ?? email,
      items,
      subtotal,
      tax: 0, // Stripe Tax — при подключении ключей (ТЗ §10.1)
      discount,
      total,
      currency: store["currency"] as string,
      promoCode: input.promoCode ?? null,
      status: "NEW" satisfies OrderStatus,
      delivery: {
        method: input.delivery.method,
        address: input.delivery.address?.trim() || null,
        cost: delivery,
        trackingNo: null,
      },
      payment: { method: "deferred", paidAt: null },
      idempotencyKey: input.idempotencyKey,
      createdAt: FieldValue.serverTimestamp(),
    };
    tx.set(orderRef, orderData);
    return { data: orderData, replay: false, ownerUid: store["ownerUid"] as string, lowStock };
  });

  const order = toApiOrder(result.data);
  if (!result.replay) {
    // Побочные эффекты вне транзакции (fire-and-forget): аналитика + push владельцу
    void recordEvent(storeId, { type: "purchase", value: order.total }).catch(() => undefined);
    // FR-A05 new-vs-returning: первый заказ покупателя в магазине → новый клиент
    void ordersCol(storeId)
      .where("customerUid", "==", uid)
      .limit(2)
      .get()
      .then((snap) => recordCustomerType(storeId, snap.size <= 1))
      .catch(() => undefined);
    if (result.ownerUid) {
      void sendToUsers(
        storeId,
        [result.ownerUid],
        {
          title: "Новый заказ",
          body: `Заказ на ${order.items.length} поз., сумма ${order.total}`,
          data: { type: "new_order" },
        },
        { orderId: order.id },
      ).catch(() => undefined);
      // FR-A03: push о низком остатке — только при пересечении порога этой продажей
      void notifyLowStock(storeId, result.ownerUid, result.lowStock).catch(() => undefined);
    }
    logger.info("Заказ создан", { storeId, orderId: order.id, total: order.total });
  }
  return { order, replay: result.replay };
}

/** Восстановление стока по позициям заказа (отмена до отгрузки, оплата deferred). */
function restockItems(
  tx: FirebaseFirestore.Transaction,
  snaps: FirebaseFirestore.DocumentSnapshot[],
  items: OrderItem[],
): void {
  items.forEach((item, i) => {
    const snap = snaps[i]!;
    const product = snap.data();
    if (!product) return; // товар удалён — сток не возвращаем
    const variants = ((product["variants"] as ProductVariant[]) ?? []).map((v) => ({ ...v }));
    if (variants.length === 0 || !item.variant) {
      tx.update(snap.ref, { totalStock: ((product["totalStock"] as number) ?? 0) + item.qty });
    } else {
      const target = findVariant(variants, item.variant);
      if (!target) return;
      target.stock += item.qty;
      tx.update(snap.ref, { variants, totalStock: computeTotalStock(variants) });
    }
  });
}

/** Смена статуса (FR-A04, владелец/сотрудник). CANCELLED до отгрузки — ресток. */
export async function updateOrderStatus(
  storeId: string,
  orderId: string,
  next: OrderStatus,
  trackingNo: string | undefined,
): Promise<ApiOrder> {
  const ref = ordersCol(storeId).doc(orderId);

  const data = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Заказ не найден");
    const order = snap.data()!;
    const current = order["status"] as OrderStatus;

    if (!canTransition(current, next)) {
      throw new ApiError("CONFLICT", `Недопустимый переход статуса: ${current} → ${next}`);
    }

    if (next === "CANCELLED" && RESTOCK_ON_CANCEL.includes(current)) {
      const items = (order["items"] as OrderItem[]) ?? [];
      const snaps = await Promise.all(
        items.map((it) => tx.get(productsCol(storeId).doc(it.productId))),
      );
      restockItems(tx, snaps, items);
    }

    const patch: Record<string, unknown> = { status: next };
    if (trackingNo !== undefined) patch["delivery.trackingNo"] = trackingNo;
    tx.update(ref, patch);

    const updated: Record<string, unknown> = { ...order, status: next };
    if (trackingNo !== undefined) {
      updated["delivery"] = { ...(order["delivery"] as Record<string, unknown>), trackingNo };
    }
    return updated;
  });

  const order = toApiOrder(data);
  // FR-B06: push покупателю о смене статуса (fire-and-forget, best-effort)
  void sendToUsers(
    storeId,
    [order.customerUid],
    buildOrderStatusNotification(orderId, next, order.delivery.trackingNo),
    { orderId },
  ).catch(() => undefined);

  logger.info("Статус заказа изменён", { storeId, orderId, status: next });
  return order;
}

/** Отмена покупателем (FR-B06): только свой заказ в NEW/CONFIRMED/PROCESSING. */
export async function cancelOrderByBuyer(
  storeId: string,
  orderId: string,
  uid: string,
): Promise<ApiOrder> {
  const ref = ordersCol(storeId).doc(orderId);

  const data = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Заказ не найден");
    const order = snap.data()!;
    if (order["customerUid"] !== uid) throw new ApiError("FORBIDDEN", "Это не ваш заказ");

    const current = order["status"] as OrderStatus;
    if (!isCancellableByBuyer(current)) {
      throw new ApiError("CONFLICT", `Заказ в статусе ${current} нельзя отменить`);
    }

    const items = (order["items"] as OrderItem[]) ?? [];
    const snaps = await Promise.all(
      items.map((it) => tx.get(productsCol(storeId).doc(it.productId))),
    );
    restockItems(tx, snaps, items);
    tx.update(ref, { status: "CANCELLED" });
    return { ...order, status: "CANCELLED" };
  });

  logger.info("Заказ отменён покупателем", { storeId, orderId });
  return toApiOrder(data);
}

/** Заказы магазина (FR-A04), новые сверху, опц. фильтр по статусу. */
export async function listOrders(
  storeId: string,
  status: OrderStatus | undefined,
  limit: number,
): Promise<ApiOrder[]> {
  let query: FirebaseFirestore.Query = ordersCol(storeId)
    .orderBy("createdAt", "desc")
    .limit(limit);
  if (status) query = ordersCol(storeId).where("status", "==", status).limit(limit);
  const snap = await query.get();
  return snap.docs.map((doc) => toApiOrder(doc.data()));
}

/** Заказы покупателя в магазине (FR-B06), новые сверху. */
export async function listMyOrders(
  storeId: string,
  uid: string,
  limit: number,
): Promise<ApiOrder[]> {
  const snap = await ordersCol(storeId)
    .where("customerUid", "==", uid)
    .limit(limit)
    .get();
  // Сортировка в памяти: where+orderBy потребовал бы композитный индекс
  return snap.docs
    .map((doc) => toApiOrder(doc.data()))
    .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));
}

/** Один заказ — доступ проверяет роут (владелец/сотрудник или сам покупатель). */
export async function getOrder(storeId: string, orderId: string): Promise<ApiOrder> {
  const snap = await ordersCol(storeId).doc(orderId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Заказ не найден");
  return toApiOrder(snap.data()!);
}
