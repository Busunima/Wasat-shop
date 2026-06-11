import { z } from "zod";

/**
 * Push-уведомления (ТЗ §6 FR-B10): регистрация FCM-токена устройства покупателя.
 * Токены хранятся в stores/{storeId}/fcmTokens/{uid} (docs/data-model.md).
 */
export const PUSH_PLATFORMS = ["android", "web"] as const;

export const pushTokenSchema = z.object({
  token: z.string().min(10).max(4096),
  platform: z.enum(PUSH_PLATFORMS).default("android"),
});
export type PushToken = z.infer<typeof pushTokenSchema>;
