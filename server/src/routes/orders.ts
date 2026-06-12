import { Router } from "express";
import {
  requireAuth,
  requireStoreStaff,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { ApiError } from "../middleware/errorHandler.js";
import { ordersListQuerySchema, orderStatusUpdateSchema } from "../schemas/order.js";
import {
  cancelOrderByBuyer,
  getOrder,
  listMyOrders,
  listOrders,
  updateOrderStatus,
} from "../services/orders.js";
import { renderInvoice } from "../services/invoice.js";

/**
 * Заказы магазина: /api/stores/:storeId/orders (создание — POST /api/checkout).
 * FR-A04 — владелец/сотрудник: список + смена статуса; FR-B06 — покупатель:
 * свои заказы, карточка, отмена до отгрузки. mergeParams — :storeId.
 */
export const ordersRouter: Router = Router({ mergeParams: true });

ordersRouter.use(verifyAppCheck, requireAuth);

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

// FR-B06: заказы покупателя (объявлен ДО /:orderId)
ordersRouter.get("/my", async (req: AuthedRequest, res, next) => {
  try {
    const { limit } = ordersListQuerySchema.parse(req.query);
    res.json({ items: await listMyOrders(param(req, "storeId"), actorUid(req), limit) });
  } catch (err) {
    next(err);
  }
});

// FR-A04: список заказов магазина — владелец/сотрудник
ordersRouter.get("/", requireStoreStaff, async (req: AuthedRequest, res, next) => {
  try {
    const { status, limit } = ordersListQuerySchema.parse(req.query);
    res.json({ items: await listOrders(param(req, "storeId"), status, limit) });
  } catch (err) {
    next(err);
  }
});

// Карточка заказа: владелец/сотрудник или сам покупатель
ordersRouter.get("/:orderId", async (req: AuthedRequest, res, next) => {
  try {
    const order = await getOrder(param(req, "storeId"), param(req, "orderId"));
    if (!isStoreMember(req) && order.customerUid !== actorUid(req)) {
      throw new ApiError("FORBIDDEN", "Нет доступа к этому заказу");
    }
    res.json(order);
  } catch (err) {
    next(err);
  }
});

// FR-A04: HTML-инвойс заказа (клиент печатает в PDF). Доступ как у карточки заказа.
ordersRouter.get("/:orderId/invoice", async (req: AuthedRequest, res, next) => {
  try {
    const storeId = param(req, "storeId");
    const order = await getOrder(storeId, param(req, "orderId"));
    if (!isStoreMember(req) && order.customerUid !== actorUid(req)) {
      throw new ApiError("FORBIDDEN", "Нет доступа к этому заказу");
    }
    res.type("html").send(await renderInvoice(storeId, order));
  } catch (err) {
    next(err);
  }
});

// FR-A04: смена статуса (валидация переходов в сервисе; авто-push — FR-A07 далее)
ordersRouter.post(
  "/:orderId/status",
  requireStoreStaff,
  async (req: AuthedRequest, res, next) => {
    try {
      const { status, trackingNo } = orderStatusUpdateSchema.parse(req.body);
      res.json(
        await updateOrderStatus(param(req, "storeId"), param(req, "orderId"), status, trackingNo),
      );
    } catch (err) {
      next(err);
    }
  },
);

// FR-B06: отмена покупателем (NEW/CONFIRMED/PROCESSING) с рестоком
ordersRouter.post("/:orderId/cancel", async (req: AuthedRequest, res, next) => {
  try {
    res.json(await cancelOrderByBuyer(param(req, "storeId"), param(req, "orderId"), actorUid(req)));
  } catch (err) {
    next(err);
  }
});
