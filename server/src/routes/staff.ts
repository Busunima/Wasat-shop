import { Router } from "express";
import { requireAuth, requireStoreRole, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { staffInviteSchema, staffRoleUpdateSchema } from "../schemas/staff.js";
import { addStaff, listStaff, removeStaff, updateStaffRole } from "../services/staff.js";

/**
 * Сотрудники магазина (ТЗ §6 FR-A09): /api/stores/:storeId/staff/*.
 * Управление — только владелец (requireStoreRole). mergeParams — :storeId.
 */
export const staffRouter: Router = Router({ mergeParams: true });

staffRouter.use(verifyAppCheck, requireAuth, requireStoreRole);

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function actorUid(req: AuthedRequest): string {
  const uid = req.uid;
  if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
  return uid;
}

staffRouter.get("/", async (req: AuthedRequest, res, next) => {
  try {
    res.json({ items: await listStaff(param(req, "storeId")) });
  } catch (err) {
    next(err);
  }
});

staffRouter.post("/", async (req: AuthedRequest, res, next) => {
  try {
    const { email, role } = staffInviteSchema.parse(req.body);
    res.status(201).json(await addStaff(param(req, "storeId"), actorUid(req), email, role));
  } catch (err) {
    next(err);
  }
});

staffRouter.patch("/:uid", async (req: AuthedRequest, res, next) => {
  try {
    const { role } = staffRoleUpdateSchema.parse(req.body);
    res.json(await updateStaffRole(param(req, "storeId"), actorUid(req), param(req, "uid"), role));
  } catch (err) {
    next(err);
  }
});

staffRouter.delete("/:uid", async (req: AuthedRequest, res, next) => {
  try {
    await removeStaff(param(req, "storeId"), actorUid(req), param(req, "uid"));
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});
