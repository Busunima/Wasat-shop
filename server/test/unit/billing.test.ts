import assert from "node:assert/strict";
import { test } from "node:test";
import { createSubscriptionCheckout } from "../../src/services/billing.ts";

// Без STRIPE_SECRET_KEY / price ID подписка в режиме deferred.
test("Billing env-gate: без ключа/price — checkout deferred", async () => {
  const result = await createSubscriptionCheckout({
    storeId: "s1",
    ownerEmail: "owner@example.com",
    plan: "pro",
  });
  assert.equal(result.deferred, true);
});
