import assert from "node:assert/strict";
import { test } from "node:test";
import { createPaymentIntent, getStripe } from "../../src/services/stripe.ts";

// В тестовом окружении STRIPE_SECRET_KEY не задан → Stripe в режиме deferred.
test("Stripe env-gate: без ключа getStripe=null, createPaymentIntent=null", async () => {
  assert.equal(getStripe(), null);
  const intent = await createPaymentIntent({
    amountMinor: 1000,
    currency: "USD",
    idempotencyKey: "k1",
    metadata: { storeId: "s", orderId: "o" },
  });
  assert.equal(intent, null);
});
