import { initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";

/**
 * Инициализация Firebase Web SDK. Конфиг — из VITE_* env (см. .env.example).
 * Без конфигурации приложение собирается и показывает «не сконфигурировано»
 * (тот же подход, что в Android-клиенте: сборка/CI без секретов).
 */
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const isFirebaseConfigured = Boolean(config.apiKey && config.projectId);

let app: FirebaseApp | null = null;
let authInstance: Auth | null = null;

export function firebaseAuth(): Auth | null {
  if (!isFirebaseConfigured) return null;
  if (!authInstance) {
    app = initializeApp({
      apiKey: config.apiKey,
      authDomain: config.authDomain,
      projectId: config.projectId,
      appId: config.appId,
    });
    authInstance = getAuth(app);
  }
  return authInstance;
}

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/";
