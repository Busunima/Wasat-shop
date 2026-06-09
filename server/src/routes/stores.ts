import { randomUUID } from "node:crypto";
import { Router } from "express";
import { requireAuth, type AuthedRequest } from "../middleware/auth.js";
import { ApiError } from "../middleware/errorHandler.js";
import { storeInitSchema } from "../schemas/store.js";
import { logger } from "../lib/logger.js";

export const storesRouter: Router = Router();

/**
 * POST /api/stores/init — создание магазина (ТЗ §4.1 шаг 4, §9).
 * Каркас: валидация входа + генерация storeId/slug. Транзакционная запись в Firestore,
 * выставление Custom Claim и старт Stripe Connect добавляются в Шаге 2.
 */
storesRouter.post("/init", requireAuth, async (req: AuthedRequest, _res, next) => {
  try {
    const body = storeInitSchema.parse(req.body);
    const uid = req.uid;
    if (!uid) throw new ApiError("UNAUTHENTICATED", "Нет uid после аутентификации");

    const storeId = randomUUID();

    // TODO(Шаг 2): транзакция —
    //   1) проверить уникальность slug (коллекция slugs/{slug})
    //   2) создать stores/{storeId} с ownerUid/ownerEmail/plan='free'/isPublic=false
    //   3) записать обратный индекс slugs/{slug} -> storeId и users/{uid}
    //   4) auth().setCustomUserClaims(uid, { storeId, role: 'owner' })
    //   5) создать Stripe Connect Express account + onboard-link
    logger.info("Запрошено создание магазина (каркас)", { storeId, slug: body.slug });

    throw new ApiError(
      "NOT_IMPLEMENTED",
      "stores/init: каркас готов, бизнес-логика подключается в Шаге 2",
      { storeId, accepted: body },
    );
  } catch (err) {
    next(err);
  }
});
