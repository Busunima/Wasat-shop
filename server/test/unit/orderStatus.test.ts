import assert from "node:assert/strict";
import { test } from "node:test";
import {
  ORDER_STATUS,
  isCancellableByBuyer,
  orderStatusSchema,
} from "../../src/schemas/orderStatus.ts";

test("канонический enum содержит ровно 10 статусов (ТЗ §FR-A04)", () => {
  assert.equal(ORDER_STATUS.length, 10);
});

test("happy path и терминальные статусы присутствуют", () => {
  for (const s of ["NEW", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "COMPLETED"]) {
    assert.ok(ORDER_STATUS.includes(s as (typeof ORDER_STATUS)[number]));
  }
  for (const s of ["CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"]) {
    assert.ok(ORDER_STATUS.includes(s as (typeof ORDER_STATUS)[number]));
  }
});

test("orderStatusSchema отвергает неизвестный статус", () => {
  assert.equal(orderStatusSchema.safeParse("NEW").success, true);
  assert.equal(orderStatusSchema.safeParse("UNKNOWN").success, false);
});

test("отмена покупателем разрешена только в NEW/CONFIRMED/PROCESSING (ТЗ §FR-B06)", () => {
  assert.equal(isCancellableByBuyer("NEW"), true);
  assert.equal(isCancellableByBuyer("PROCESSING"), true);
  assert.equal(isCancellableByBuyer("SHIPPED"), false);
  assert.equal(isCancellableByBuyer("COMPLETED"), false);
});
