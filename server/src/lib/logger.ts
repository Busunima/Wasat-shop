/**
 * Минимальный структурированный логгер. Маскирует email в выводе (ТЗ §13).
 * В Фазе 1 может быть заменён на pino/winston без смены вызовов.
 */
type Level = "debug" | "info" | "warn" | "error";

const EMAIL_RE = /([a-zA-Z0-9._%+-])[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/g;

export function maskEmail(text: string): string {
  return text.replace(EMAIL_RE, (_m, first: string, domain: string) => `${first}***${domain}`);
}

function emit(level: Level, msg: string, meta?: Record<string, unknown>): void {
  const line = {
    ts: new Date().toISOString(),
    level,
    msg: maskEmail(msg),
    ...(meta ? { meta } : {}),
  };
  const out = JSON.stringify(line);
  if (level === "error") console.error(out);
  else if (level === "warn") console.warn(out);
  else console.log(out);
}

export const logger = {
  debug: (msg: string, meta?: Record<string, unknown>) => emit("debug", msg, meta),
  info: (msg: string, meta?: Record<string, unknown>) => emit("info", msg, meta),
  warn: (msg: string, meta?: Record<string, unknown>) => emit("warn", msg, meta),
  error: (msg: string, meta?: Record<string, unknown>) => emit("error", msg, meta),
};
