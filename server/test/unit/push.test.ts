import assert from "node:assert/strict";
import { test } from "node:test";
import { pushTokenSchema } from "../../src/schemas/push.ts";
import {
  buildOrderStatusNotification,
  buildProductNotification,
} from "../../src/services/push.ts";

test("pushTokenSchema: токен и платформа по умолчанию", () => {
  const ok = pushTokenSchema.parse({ token: "x".repeat(32) });
  assert.equal(ok.platform, "android");
  assert.equal(pushTokenSchema.parse({ token: "y".repeat(20), platform: "web" }).platform, "web");
  assert.throws(() => pushTokenSchema.parse({ token: "short" })); // < 10
  assert.throws(() => pushTokenSchema.parse({ token: "z".repeat(20), platform: "ios" }));
});

test("buildProductNotification: тексты и data.type", () => {
  const restock = buildProductNotification("back_in_stock", "Кеды и Ко", "Air Max");
  assert.equal(restock.title, "Кеды и Ко");
  assert.ok(restock.body.includes("Air Max"));
  assert.ok(restock.body.includes("снова в наличии"));
  assert.equal(restock.data["type"], "back_in_stock");

  const drop = buildProductNotification("price_drop", "Кеды и Ко", "Air Max");
  assert.ok(drop.body.includes("снижена"));
  assert.equal(drop.data["type"], "price_drop");
});

test("buildOrderStatusNotification: номер, подпись статуса, трек для SHIPPED", () => {
  const shipped = buildOrderStatusNotification("abcdef1234567890", "SHIPPED", "TRK-42");
  assert.equal(shipped.title, "Заказ №ABCDEF12");
  assert.ok(shipped.body.includes("отправлен"));
  assert.ok(shipped.body.includes("TRK-42"));
  assert.equal(shipped.data["type"], "order_status");
  assert.equal(shipped.data["status"], "SHIPPED");

  // трек не подмешивается в другие статусы; неизвестный статус — как есть
  const delivered = buildOrderStatusNotification("abcdef1234567890", "DELIVERED", "TRK-42");
  assert.ok(!delivered.body.includes("TRK-42"));
  assert.ok(delivered.body.includes("доставлен"));
  const unknown = buildOrderStatusNotification("abcdef1234567890", "X_STATUS", null);
  assert.ok(unknown.body.includes("X_STATUS"));
});
