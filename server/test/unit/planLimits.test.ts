import assert from "node:assert/strict";
import { test } from "node:test";
import { PLAN_LIMITS, canAdd, limitsFor } from "../../src/config/planLimits.ts";

test("limitsFor: известные тарифы и фолбэк на free", () => {
  assert.equal(limitsFor("free").maxProducts, 25);
  assert.equal(limitsFor("pro").maxStaff, 15);
  assert.equal(limitsFor("enterprise").maxProducts, null);
  // неизвестный тариф → free (безопасный минимум)
  assert.deepEqual(limitsFor("ghost"), PLAN_LIMITS.free);
});

test("canAdd: лимит исчерпывается при достижении предела", () => {
  assert.equal(canAdd(25, 24), true);
  assert.equal(canAdd(25, 25), false);
  assert.equal(canAdd(25, 26), false);
  // free не допускает сотрудников вовсе
  assert.equal(canAdd(0, 0), false);
  // null (enterprise) — без ограничения
  assert.equal(canAdd(null, 100000), true);
});
