import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import { createProduct } from "../../src/services/products.ts";
import {
  adjustStock,
  importStockCsv,
  listInventoryLog,
} from "../../src/services/inventory.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";

/** Интеграционные тесты инвентаря (FR-A03) против эмулятора Firestore. */

const STORE_ID = "inventory-store";
const UID = "owner-inv";
let variantProductId = "";
let simpleProductId = "";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "inventory",
    name: "Inventory",
    ownerUid: UID,
    currency: "USD",
    isPublic: true,
  });

  const variantProduct = await createProduct(STORE_ID, {
    name: "Кеды",
    price: 1000,
    images: [],
    tags: [],
    variants: [
      { size: "41", stock: 5, sku: "KEDY-41" },
      { size: "42", stock: 3, sku: "KEDY-42" },
    ],
    status: "active",
  });
  variantProductId = variantProduct.id;

  const simpleProduct = await createProduct(STORE_ID, {
    name: "Носки",
    price: 100,
    images: [],
    tags: [],
    sku: "SOCKS",
    variants: [],
    status: "active",
  });
  simpleProductId = simpleProduct.id;
  // У товара без вариантов totalStock задаётся инвентарём
  await adjustStock(STORE_ID, simpleProductId, UID, { delta: 10, reason: "initial" });
});

test("adjustStock: вариант по sku, totalStock пересчитан, лог записан", async () => {
  const result = await adjustStock(STORE_ID, variantProductId, UID, {
    variant: { sku: "KEDY-41" },
    delta: -2,
    reason: "продажа офлайн",
  });
  assert.equal(result.variants.find((v) => v.sku === "KEDY-41")?.stock, 3);
  assert.equal(result.totalStock, 6); // 3 + 3

  const log = await listInventoryLog(STORE_ID, variantProductId, 10);
  const entry = log.find((e) => e.variant === "KEDY-41");
  assert.ok(entry);
  assert.equal(entry!.delta, -2);
  assert.equal(entry!.byUid, UID);
  assert.equal(entry!.reason, "продажа офлайн");
});

test("adjustStock: уход ниже нуля — CONFLICT, сток не меняется", async () => {
  await assert.rejects(
    () =>
      adjustStock(STORE_ID, variantProductId, UID, {
        variant: { sku: "KEDY-42" },
        delta: -100,
        reason: "manual",
      }),
    (e: unknown) => e instanceof ApiError && e.code === "CONFLICT",
  );
});

test("adjustStock: вариант по size/color и ошибки адресации", async () => {
  const result = await adjustStock(STORE_ID, variantProductId, UID, {
    variant: { size: "42" },
    delta: 1,
    reason: "manual",
  });
  assert.equal(result.variants.find((v) => v.sku === "KEDY-42")?.stock, 4);

  await assert.rejects(
    () =>
      adjustStock(STORE_ID, variantProductId, UID, {
        variant: { sku: "NOPE" },
        delta: 1,
        reason: "manual",
      }),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
  // вариантному товару нужен селектор
  await assert.rejects(
    () => adjustStock(STORE_ID, variantProductId, UID, { delta: 1, reason: "manual" }),
    (e: unknown) => e instanceof ApiError && e.code === "VALIDATION_ERROR",
  );
});

test("importStockCsv: абсолютные значения, отчёт об ошибках", async () => {
  const csv = ["sku,stock", "KEDY-41,20", "SOCKS,50", "UNKNOWN,5", "битая"].join("\n");
  const report = await importStockCsv(STORE_ID, UID, csv);

  assert.equal(report.applied, 2);
  assert.equal(report.errors.length, 2); // UNKNOWN + битая

  const variantSnap = await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("products")
    .doc(variantProductId)
    .get();
  const variants = variantSnap.data()!["variants"] as Array<{ sku?: string; stock: number }>;
  assert.equal(variants.find((v) => v.sku === "KEDY-41")?.stock, 20);

  const simpleSnap = await db()
    .collection("stores")
    .doc(STORE_ID)
    .collection("products")
    .doc(simpleProductId)
    .get();
  assert.equal(simpleSnap.data()!["totalStock"], 50);

  // обе операции в логе с reason csv-import
  const log = await listInventoryLog(STORE_ID, undefined, 50);
  assert.ok(log.filter((e) => e.reason === "csv-import").length >= 2);
});

test("adjustStock: идемпотентность по ключу — повтор не задваивает дельту (offline-first)", async () => {
  const key = "stock-key-abc12345";
  const first = await adjustStock(STORE_ID, simpleProductId, UID, {
    delta: 5,
    reason: "manual",
    idempotencyKey: key,
  });
  const after = first.totalStock;
  // повтор с тем же ключом — дельта НЕ применяется повторно
  const replay = await adjustStock(STORE_ID, simpleProductId, UID, {
    delta: 5,
    reason: "manual",
    idempotencyKey: key,
  });
  assert.equal(replay.totalStock, after);
  // другой ключ — применяется как обычно
  const third = await adjustStock(STORE_ID, simpleProductId, UID, {
    delta: 5,
    reason: "manual",
    idempotencyKey: "stock-key-different9",
  });
  assert.equal(third.totalStock, after + 5);
});
