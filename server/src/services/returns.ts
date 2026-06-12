import { randomUUID } from "node:crypto";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../lib/firebase.js";
import { env } from "../config/env.js";
import { ApiError } from "../middleware/errorHandler.js";
import {
  canTransitionReturn,
  computeRefundAmount,
  type ReturnCreate,
  type ReturnStatus,
} from "../schemas/return.js";
import { canTransition } from "../schemas/order.js";
import type { OrderStatus } from "../schemas/orderStatus.js";
import { computeTotalStock, type ProductVariant } from "../schemas/product.js";
import type { OrderItem } from "./orders.js";
import { sendToUsers } from "./push.js";
import { logger } from "../lib/logger.js";

/**
 * Возвраты (ТЗ §6 FR-B09/A11). Заявка по доставленному заказу → решение владельца →
 * приём (ресток) → возмещение. Каждый переход атомарно двигает и документ возврата,
 * и статус заказа (canTransition по docs/order-status.md). Реальный Stripe Refund —
 * env-gated: без STRIPE_SECRET_KEY статус становится REFUNDED со `stripeRefundId=null`
 * и пометкой deferred (фактическое возмещение проводится при подключении ключей).
 */

export interface ApiReturn {
  id: string;
  orderId: string;
  customerUid: string;
  items: Array<{ productId: string; qty: number }>;
  reason: string;
  status: ReturnStatus;
  refundAmount: number;
  stripeRefundId: string | null;
  refundDeferred: boolean;
  comment: string | null;
  createdAt: number | null;
}

function returnsCol(storeId: string) {
  return db().collection("stores").doc(storeId).collection("returns");
}

function orderRef(storeId: string, orderId: string) {
  return db().collection("stores").doc(storeId).collection("orders").doc(orderId);
}

function productRef(storeId: string, productId: string) {
  return db().collection("stores").doc(storeId).collection("products").doc(productId);
}

function toApiReturn(data: FirebaseFirestore.DocumentData): ApiReturn {
  const createdAt = data["createdAt"];
  return {
    id: data["id"] as string,
    orderId: data["orderId"] as string,
    customerUid: data["customerUid"] as string,
    items: (data["items"] as ApiReturn["items"]) ?? [],
    reason: (data["reason"] as string) ?? "",
    status: data["status"] as ReturnStatus,
    refundAmount: (data["refundAmount"] as number) ?? 0,
    stripeRefundId: (data["stripeRefundId"] as string | null) ?? null,
    refundDeferred: data["refundDeferred"] === true,
    comment: (data["comment"] as string | null) ?? null,
    createdAt: createdAt instanceof Timestamp ? createdAt.toMillis() : null,
  };
}

function findVariant(
  variants: ProductVariant[],
  selector: NonNullable<OrderItem["variant"]>,
): ProductVariant | undefined {
  if (selector.sku !== undefined) return variants.find((v) => v.sku === selector.sku);
  return variants.find(
    (v) =>
      (selector.size === undefined || v.size === selector.size) &&
      (selector.color === undefined || v.color === selector.color),
  );
}

/** Возврат стока по принятым позициям (вариант берётся из позиции заказа). */
function restock(
  tx: FirebaseFirestore.Transaction,
  snaps: FirebaseFirestore.DocumentSnapshot[],
  orderItems: OrderItem[],
  returnItems: Array<{ productId: string; qty: number }>,
): void {
  returnItems.forEach((ret) => {
    const orderItem = orderItems.find((it) => it.productId === ret.productId);
    if (!orderItem) return;
    const qty = Math.min(ret.qty, orderItem.qty);
    const snap = snaps.find((s) => s.id === ret.productId);
    const product = snap?.data();
    if (!snap || !product) return; // товар удалён — сток не возвращаем
    const variants = ((product["variants"] as ProductVariant[]) ?? []).map((v) => ({ ...v }));
    if (variants.length === 0 || !orderItem.variant) {
      tx.update(snap.ref, { totalStock: ((product["totalStock"] as number) ?? 0) + qty });
    } else {
      const target = findVariant(variants, orderItem.variant);
      if (!target) return;
      target.stock += qty;
      tx.update(snap.ref, { variants, totalStock: computeTotalStock(variants) });
    }
  });
}

/** Заявка на возврат (FR-B09). Заказ доставлен/завершён, свой, содержит позиции. */
export async function createReturn(
  storeId: string,
  uid: string,
  input: ReturnCreate,
): Promise<ApiReturn> {
  const id = randomUUID();
  const ref = returnsCol(storeId).doc(id);
  const oRef = orderRef(storeId, input.orderId);

  const data = await db().runTransaction(async (tx) => {
    const orderSnap = await tx.get(oRef);
    const order = orderSnap.data();
    if (!order || order["customerUid"] !== uid) {
      throw new ApiError("NOT_FOUND", "Заказ не найден");
    }
    const status = order["status"] as OrderStatus;
    if (!canTransition(status, "RETURN_REQUESTED")) {
      throw new ApiError("CONFLICT", `Возврат недоступен для статуса ${status}`);
    }
    const orderItems = (order["items"] as OrderItem[]) ?? [];
    for (const it of input.items) {
      if (!orderItems.some((oi) => oi.productId === it.productId)) {
        throw new ApiError("VALIDATION_ERROR", `Заказ не содержит товар ${it.productId}`);
      }
    }

    const returnData = {
      id,
      orderId: input.orderId,
      customerUid: uid,
      items: input.items,
      reason: input.reason,
      status: "REQUESTED" satisfies ReturnStatus,
      refundAmount: 0,
      stripeRefundId: null,
      refundDeferred: false,
      comment: null,
      createdAt: FieldValue.serverTimestamp(),
    };
    tx.set(ref, returnData);
    tx.update(oRef, { status: "RETURN_REQUESTED" });
    return returnData;
  });

  // Push владельцу — best-effort (ownerUid живёт в документе магазина).
  void notifyOwnerNewReturn(storeId, id).catch(() => undefined);
  logger.info("Возврат запрошен", { storeId, orderId: input.orderId });
  return toApiReturn(data);
}

async function notifyOwnerNewReturn(storeId: string, returnId: string): Promise<void> {
  const storeSnap = await db().collection("stores").doc(storeId).get();
  const ownerUid = storeSnap.data()?.["ownerUid"] as string | undefined;
  if (!ownerUid) return;
  await sendToUsers(
    storeId,
    [ownerUid],
    { title: "Запрос возврата", body: "Покупатель запросил возврат", data: { type: "return" } },
    { returnId },
  );
}

/** Решение владельца (FR-A11): approve → APPROVED; reject → REJECTED + заказ к COMPLETED. */
export async function resolveReturn(
  storeId: string,
  returnId: string,
  action: "approve" | "reject",
  comment: string | undefined,
): Promise<ApiReturn> {
  const ref = returnsCol(storeId).doc(returnId);
  const next: ReturnStatus = action === "approve" ? "APPROVED" : "REJECTED";

  const data = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Возврат не найден");
    const ret = snap.data()!;
    if (!canTransitionReturn(ret["status"] as ReturnStatus, next)) {
      throw new ApiError("CONFLICT", `Недопустимый переход возврата: ${ret["status"]} → ${next}`);
    }
    tx.update(ref, { status: next, comment: comment ?? null });
    // Отклонение возвращает заказ в COMPLETED (RETURN_REQUESTED → COMPLETED).
    if (next === "REJECTED") {
      tx.update(orderRef(storeId, ret["orderId"] as string), { status: "COMPLETED" });
    }
    return { ...ret, status: next, comment: comment ?? null };
  });

  logger.info("Решение по возврату", { storeId, returnId, action });
  return toApiReturn(data);
}

/** Приём возврата (FR-A11): APPROVED → RECEIVED, ресток, заказ → RETURNED. */
export async function receiveReturn(storeId: string, returnId: string): Promise<ApiReturn> {
  const ref = returnsCol(storeId).doc(returnId);

  const data = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Возврат не найден");
    const ret = snap.data()!;
    if (!canTransitionReturn(ret["status"] as ReturnStatus, "RECEIVED")) {
      throw new ApiError("CONFLICT", `Возврат не в статусе APPROVED (${ret["status"]})`);
    }
    const oRef = orderRef(storeId, ret["orderId"] as string);
    const orderSnap = await tx.get(oRef);
    const orderItems = (orderSnap.data()?.["items"] as OrderItem[]) ?? [];
    const returnItems = (ret["items"] as Array<{ productId: string; qty: number }>) ?? [];

    const prodSnaps = await Promise.all(
      returnItems.map((it) => tx.get(productRef(storeId, it.productId))),
    );
    restock(tx, prodSnaps, orderItems, returnItems);

    tx.update(ref, { status: "RECEIVED" });
    tx.update(oRef, { status: "RETURNED" });
    return { ...ret, status: "RECEIVED" };
  });

  logger.info("Возврат принят (ресток)", { storeId, returnId });
  return toApiReturn(data);
}

/**
 * Возмещение (FR-A11): RECEIVED → REFUNDED. Сумма считается по позициям заказа.
 * Stripe Refund env-gated: без ключа `refundDeferred=true`, фактический рефанд —
 * при подключении (паттерн как с PaymentIntent в чекауте).
 */
export async function refundReturn(storeId: string, returnId: string): Promise<ApiReturn> {
  const ref = returnsCol(storeId).doc(returnId);
  const deferred = !env.STRIPE_SECRET_KEY;

  const data = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new ApiError("NOT_FOUND", "Возврат не найден");
    const ret = snap.data()!;
    if (!canTransitionReturn(ret["status"] as ReturnStatus, "REFUNDED")) {
      throw new ApiError("CONFLICT", `Возврат не принят (${ret["status"]})`);
    }
    const oRef = orderRef(storeId, ret["orderId"] as string);
    const orderSnap = await tx.get(oRef);
    const orderItems = (orderSnap.data()?.["items"] as OrderItem[]) ?? [];
    const returnItems = (ret["items"] as Array<{ productId: string; qty: number }>) ?? [];
    const refundAmount = computeRefundAmount(orderItems, returnItems);

    tx.update(ref, {
      status: "REFUNDED",
      refundAmount,
      stripeRefundId: null, // фактический рефанд — при подключении Stripe
      refundDeferred: deferred,
    });
    tx.update(oRef, { status: "REFUNDED" });
    return { ...ret, status: "REFUNDED", refundAmount, refundDeferred: deferred };
  });

  logger.info("Возврат возмещён", { storeId, returnId, deferred });
  return toApiReturn(data);
}

export async function listReturns(
  storeId: string,
  status: ReturnStatus | undefined,
  limit: number,
): Promise<ApiReturn[]> {
  let query: FirebaseFirestore.Query = returnsCol(storeId)
    .orderBy("createdAt", "desc")
    .limit(limit);
  if (status) query = returnsCol(storeId).where("status", "==", status).limit(limit);
  const snap = await query.get();
  return snap.docs.map((doc) => toApiReturn(doc.data()));
}

export async function listMyReturns(
  storeId: string,
  uid: string,
  limit: number,
): Promise<ApiReturn[]> {
  const snap = await returnsCol(storeId).where("customerUid", "==", uid).limit(limit).get();
  return snap.docs
    .map((doc) => toApiReturn(doc.data()))
    .sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));
}

export async function getReturn(storeId: string, returnId: string): Promise<ApiReturn> {
  const snap = await returnsCol(storeId).doc(returnId).get();
  if (!snap.exists) throw new ApiError("NOT_FOUND", "Возврат не найден");
  return toApiReturn(snap.data()!);
}
