import assert from "node:assert/strict";
import { test } from "node:test";
import { storeUpdateSchema } from "../../src/schemas/store.ts";

test("storeUpdateSchema: пустой PATCH допустим (ничего не меняется)", () => {
  const parsed = storeUpdateSchema.parse({});
  assert.deepEqual(
    Object.values(parsed).filter((v) => v !== undefined),
    [],
  );
});

test("storeUpdateSchema: тема — строгий #RRGGBB", () => {
  const ok = storeUpdateSchema.parse({ theme: { primary: "#2D4A7C", secondary: "#aabbcc" } });
  assert.equal(ok.theme?.primary, "#2D4A7C");

  assert.throws(() => storeUpdateSchema.parse({ theme: { primary: "2D4A7C", secondary: "#aabbcc" } }));
  assert.throws(() => storeUpdateSchema.parse({ theme: { primary: "#2D4A7", secondary: "#aabbcc" } }));
  assert.throws(() => storeUpdateSchema.parse({ theme: { primary: "#GGGGGG", secondary: "#aabbcc" } }));
});

test("storeUpdateSchema: logoUrl — url, '' очищает (null)", () => {
  assert.equal(
    storeUpdateSchema.parse({ logoUrl: "https://cdn.test/logo.png" }).logoUrl,
    "https://cdn.test/logo.png",
  );
  assert.equal(storeUpdateSchema.parse({ logoUrl: "" }).logoUrl, null);
  assert.equal(storeUpdateSchema.parse({}).logoUrl, undefined);
  assert.throws(() => storeUpdateSchema.parse({ logoUrl: "не-урл" }));
});

test("storeUpdateSchema: contact и deliveryCost", () => {
  const parsed = storeUpdateSchema.parse({
    contact: { email: "shop@example.com", phone: " +1 234 ", address: "" },
    deliveryCost: 500,
  });
  assert.equal(parsed.contact?.phone, "+1 234");
  assert.equal(parsed.contact?.address, null);
  assert.equal(parsed.deliveryCost, 500);

  assert.equal(storeUpdateSchema.parse({ deliveryCost: null }).deliveryCost, null);
  assert.throws(() => storeUpdateSchema.parse({ deliveryCost: -1 }));
  assert.throws(() => storeUpdateSchema.parse({ contact: { email: "плохой" } }));
});

test("storeUpdateSchema: slug/currency/plan не принимаются (strip)", () => {
  const parsed = storeUpdateSchema.parse({ slug: "new-slug", currency: "EUR", plan: "pro" });
  assert.ok(!("slug" in parsed));
  assert.ok(!("currency" in parsed));
  assert.ok(!("plan" in parsed));
});
