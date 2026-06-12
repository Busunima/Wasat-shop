import { Router } from "express";
import {
  requireAuth,
  requireStoreStaff,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  returnCreateSchema,
  returnResolveSchema,
  returnsListQuerySchema,
} from "../schemas/return.js";
import {
  createReturn,
  getReturn,
  listMyReturns,
  listReturns,
  receiveReturn,
  refundReturn,
  resolveReturn,
} from "../services/returns.js";

/**
 * Возвраты (ТЗ §6 FR-B09/A11): /api/stores/:storeId/returns/*.
 * Покупатель создаёт заявку и видит свои; владелец/сотрудник рассматривает,
 * принимает (ресток) и оформляет возмещение (Stripe Refund — deferred).
 */
export const returnsRouter: Router = Router({ mergeParams: true });

returnsRouter.use(verifyAppCheck, requireAuth);

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function actorUid(req: AuthedRequest): string {
  const uid = req.uid;
  if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");
  return uid;
}

function isStoreMember(req: AuthedRequest): boolean {
  return (
    req.claims?.["storeId"] === param(req, "storeId") &&
    ["owner", "manager", "staff"].includes(req.claims?.["role"] as string)
  );
}

// FR-B09: заявка покупателя
returnsRouter.post("/", async (req: AuthedRequest, res, next) => {
  try {
    const input = returnCreateSchema.parse(req.body);
    res.status(201).json(await createReturn(param(req, "storeId"), actorUid(req), input));
  } catch (err) {
    next(err);
  }
});

// FR-B09: свои возвраты (до /:returnId)
returnsRouter.get("/my", async (req: AuthedRequest, res, next) => {
  try {
    const { limit } = returnsListQuerySchema.parse(req.query);
    res.json({ items: await listMyReturns(param(req, "storeId"), actorUid(req), limit) });
  } catch (err) {
    next(err);
  }
});

// FR-A11: очередь возвратов магазина (владелец/сотрудник)
returnsRouter.get("/", requireStoreStaff, async (req: AuthedRequest, res, next) => {
  try {
    const { status, limit } = returnsListQuerySchema.parse(req.query);
    res.json({ items: await listReturns(param(req, "storeId"), status, limit) });
  } catch (err) {
    next(err);
  }
});

// Карточка возврата: member или сам покупатель
returnsRouter.get("/:returnId", async (req: AuthedRequest, res, next) => {
  try {
    const ret = await getReturn(param(req, "storeId"), param(req, "returnId"));
    if (!isStoreMember(req) && ret.customerUid !== actorUid(req)) {
      throw new ApiError("FORBIDDEN", "Нет доступа к этому возврату");
    }
    res.json(ret);
  } catch (err) {
    next(err);
  }
});

// FR-A11: решение / приём / возмещение (владелец/сотрудник)
returnsRouter.post("/:returnId/resolve", requireStoreStaff, async (req: AuthedRequest, res, next) => {
  try {
    const { action, comment } = returnResolveSchema.parse(req.body);
    res.json(await resolveReturn(param(req, "storeId"), param(req, "returnId"), action, comment));
  } catch (err) {
    next(err);
  }
});

returnsRouter.post("/:returnId/receive", requireStoreStaff, async (req: AuthedRequest, res, next) => {
  try {
    res.json(await receiveReturn(param(req, "storeId"), param(req, "returnId")));
  } catch (err) {
    next(err);
  }
});

returnsRouter.post("/:returnId/refund", requireStoreStaff, async (req: AuthedRequest, res, next) => {
  try {
    res.json(await refundReturn(param(req, "storeId"), param(req, "returnId")));
  } catch (err) {
    next(err);
  }
});
