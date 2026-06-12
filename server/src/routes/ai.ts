import { Router } from "express";
import { requireAuth, requireStoreStaff, type AuthedRequest } from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { rateLimit } from "../middleware/rateLimit.js";
import { aiDescribeSchema } from "../schemas/ai.js";
import { generateDescription } from "../services/ai.js";
import { db } from "../lib/firebase.js";

/**
 * AI-ассист контента (ТЗ §6 FR-A12): POST /api/stores/:storeId/ai/describe.
 * Доступ — владелец/сотрудник (те, кто редактирует товары). Без ANTHROPIC_API_KEY
 * сервис отвечает NOT_IMPLEMENTED. mergeParams — :storeId.
 */
export const aiRouter: Router = Router({ mergeParams: true });

aiRouter.use(verifyAppCheck, requireAuth, requireStoreStaff);

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

// Платный внешний вызов (Anthropic) — отдельный жёсткий лимит на ключ.
aiRouter.post("/describe", rateLimit({ max: 20 }), async (req: AuthedRequest, res, next) => {
  try {
    const input = aiDescribeSchema.parse(req.body);
    const storeSnap = await db().collection("stores").doc(param(req, "storeId")).get();
    const storeName = (storeSnap.data()?.["name"] as string) ?? "Wasat Shop";
    res.json(await generateDescription(input, storeName));
  } catch (err) {
    next(err);
  }
});
