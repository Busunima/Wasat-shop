import assert from "node:assert/strict";
import { test } from "node:test";
import { requireStoreMember, requireStoreStaff, type AuthedRequest } from "../../src/middleware/auth.ts";
import { staffInviteSchema, staffRoleUpdateSchema } from "../../src/schemas/staff.ts";

function runMiddleware(
  mw: (req: AuthedRequest, res: unknown, next: (e?: unknown) => void) => void,
  claims: Record<string, unknown> | undefined,
  storeId = "s1",
): unknown {
  const req = { params: { storeId }, claims } as unknown as AuthedRequest;
  let captured: unknown = "PASS";
  mw(req, {}, (err?: unknown) => {
    captured = err ?? "PASS";
  });
  return captured;
}

test("requireStoreMember: владелец проходит, чужой и иной магазин — FORBIDDEN", () => {
  const owner = requireStoreMember("owner");
  assert.equal(runMiddleware(owner, { storeId: "s1", role: "owner" }), "PASS");
  assert.notEqual(runMiddleware(owner, { storeId: "s1", role: "staff" }), "PASS"); // не та роль
  assert.notEqual(runMiddleware(owner, { storeId: "s2", role: "owner" }), "PASS"); // другой магазин
  assert.notEqual(runMiddleware(owner, undefined), "PASS"); // нет claims
});

test("requireStoreStaff: владелец и сотрудник проходят, гость — нет", () => {
  assert.equal(runMiddleware(requireStoreStaff, { storeId: "s1", role: "owner" }), "PASS");
  assert.equal(runMiddleware(requireStoreStaff, { storeId: "s1", role: "manager" }), "PASS");
  assert.equal(runMiddleware(requireStoreStaff, { storeId: "s1", role: "staff" }), "PASS");
  assert.notEqual(runMiddleware(requireStoreStaff, { storeId: "s1", role: "customer" }), "PASS");
  assert.notEqual(runMiddleware(requireStoreStaff, {}), "PASS");
});

test("staffInviteSchema: email + роль по умолчанию staff", () => {
  assert.deepEqual(staffInviteSchema.parse({ email: "a@b.com" }), {
    email: "a@b.com",
    role: "staff",
  });
  assert.equal(staffInviteSchema.parse({ email: "a@b.com", role: "manager" }).role, "manager");
  assert.throws(() => staffInviteSchema.parse({ email: "not-an-email" }));
  assert.throws(() => staffInviteSchema.parse({ email: "a@b.com", role: "owner" })); // owner не назначается
});

test("staffRoleUpdateSchema: только допустимые роли", () => {
  assert.equal(staffRoleUpdateSchema.parse({ role: "manager" }).role, "manager");
  assert.throws(() => staffRoleUpdateSchema.parse({ role: "ghost" }));
});
