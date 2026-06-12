import { sendToUsers } from "./push.js";

/**
 * Низкий остаток (ТЗ §6 FR-A03): при продаже, опустившей остаток товара до порога
 * и ниже, владелец получает push. Срабатывает только на пересечении порога
 * (before > threshold >= after) — повторные продажи ниже порога не спамят.
 * Порог — stores/{id}.lowStockThreshold (по умолчанию 3).
 */

export interface LowStockAlert {
  productId: string;
  name: string;
  remaining: number;
}

/** Чистая проверка пересечения порога (под unit-тестом). */
export function crossedLowStock(before: number, after: number, threshold: number): boolean {
  return before > threshold && after <= threshold;
}

/** Текст push-уведомления по списку задетых товаров (pure). */
export function buildLowStockBody(alerts: LowStockAlert[]): string {
  return alerts.map((a) => `«${a.name}»: осталось ${a.remaining}`).join("; ");
}

/** Push владельцу о низком остатке (best-effort, вызывается fire-and-forget). */
export async function notifyLowStock(
  storeId: string,
  ownerUid: string,
  alerts: LowStockAlert[],
): Promise<void> {
  if (alerts.length === 0) return;
  await sendToUsers(
    storeId,
    [ownerUid],
    {
      title: "Низкий остаток",
      body: buildLowStockBody(alerts),
      data: { type: "low_stock" },
    },
    { productId: alerts[0]!.productId },
  );
}
