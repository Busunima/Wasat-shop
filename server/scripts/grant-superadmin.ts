/**
 * Выдать/снять роль суперадмина (Custom Claim superadmin: true) — ТЗ §7.
 * Claim задаёт ТОЛЬКО сервер (§13); эндпоинта для этого нет намеренно.
 *
 * Запуск (нужны Application Default Credentials или GOOGLE_APPLICATION_CREDENTIALS,
 * и GOOGLE_CLOUD_PROJECT — реальный проект, НЕ эмулятор):
 *   npm run grant-superadmin -- <uid> [--revoke]
 */
import { auth } from "../src/lib/firebase.js";

async function main(): Promise<void> {
  const uid = process.argv[2];
  const revoke = process.argv.includes("--revoke");
  if (!uid || uid.startsWith("--")) {
    console.error("Использование: npm run grant-superadmin -- <uid> [--revoke]");
    process.exit(1);
  }

  const user = await auth().getUser(uid);
  const claims = { ...(user.customClaims ?? {}) };
  if (revoke) delete claims["superadmin"];
  else claims["superadmin"] = true;

  await auth().setCustomUserClaims(uid, claims);
  console.log(
    `${revoke ? "Снята" : "Выдана"} роль superadmin для ${uid} (${user.email ?? "без email"}).`,
  );
  console.log("Пользователю нужно перелогиниться / обновить ID-токен (getIdToken(true)).");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
