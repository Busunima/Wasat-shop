import { getApps, initializeApp, type App } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";
import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { getMessaging, type Messaging } from "firebase-admin/messaging";

/**
 * Инициализация Firebase Admin SDK (ТЗ §2, слой 2).
 * Локально подхватывает эмуляторы через FIRESTORE_EMULATOR_HOST / FIREBASE_AUTH_EMULATOR_HOST.
 * В Cloud Run используются Application Default Credentials.
 */
let app: App | undefined;

export function firebaseApp(): App {
  if (!app) {
    app = getApps()[0] ?? initializeApp();
  }
  return app;
}

export function db(): Firestore {
  return getFirestore(firebaseApp());
}

export function auth(): Auth {
  return getAuth(firebaseApp());
}

/** FCM (FR-B10/FR-A07). Эмулятора FCM нет — отправка best-effort (см. services/push). */
export function messaging(): Messaging {
  return getMessaging(firebaseApp());
}
