import assert from "node:assert/strict";
import { test } from "node:test";
import { pushTokenSchema } from "../../src/schemas/push.ts";
import { buildProductNotification } from "../../src/services/push.ts";

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
