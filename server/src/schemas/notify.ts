import { z } from "zod";

/** Рассылка владельца (ТЗ §6 FR-A07): заголовок + текст. Сегменты — Post-MVP. */
export const broadcastSchema = z.object({
  title: z.string().min(1).max(120),
  body: z.string().min(1).max(500),
});
export type Broadcast = z.infer<typeof broadcastSchema>;
