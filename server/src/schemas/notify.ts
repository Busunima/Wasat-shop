import { z } from "zod";

/** Сегменты адресатов рассылки (FR-A07): все / с заказами / без заказов. */
export const BROADCAST_SEGMENTS = ["all", "with_orders", "no_orders"] as const;

/** Рассылка владельца (ТЗ §6 FR-A07): заголовок + текст + сегмент адресатов. */
export const broadcastSchema = z.object({
  title: z.string().min(1).max(120),
  body: z.string().min(1).max(500),
  segment: z.enum(BROADCAST_SEGMENTS).default("all"),
});
export type Broadcast = z.infer<typeof broadcastSchema>;
