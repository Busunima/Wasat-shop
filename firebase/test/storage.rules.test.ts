import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";

/**
 * Тесты Storage Security Rules (storage.rules, ТЗ §4.3, §13).
 * Правила используют cross-service firestore.get/exists — поэтому нужны оба
 * эмулятора: npm run test:emulator (emulators:exec --only firestore,storage).
 */

const here = dirname(fileURLToPath(import.meta.url));
const firestoreRules = readFileSync(join(here, "..", "firestore.rules"), "utf8");
const storageRules = readFileSync(join(here, "..", "storage.rules"), "utf8");

const STORE_ID = "store-1";
const OWNER = "owner-uid";
const STAFF = "staff-uid";
const OUTSIDER = "outsider-uid";

const PNG_BYTES = new Uint8Array([0x89, 0x50, 0x4e, 0x47]);
const IMAGE_META = { contentType: "image/png" };

function parseHost(value: string | undefined, defaultPort: number): { host: string; port: number } {
  const raw = (value ?? `127.0.0.1:${defaultPort}`).replace(/^https?:\/\//, "");
  const [host, port] = raw.split(":");
  return { host: host ?? "127.0.0.1", port: Number(port ?? defaultPort) };
}

let env: RulesTestEnvironment;

before(async () => {
  const fs = parseHost(process.env["FIRESTORE_EMULATOR_HOST"], 8080);
  const st = parseHost(process.env["FIREBASE_STORAGE_EMULATOR_HOST"], 9199);
  env = await initializeTestEnvironment({
    projectId: "demo-wasat",
    firestore: { rules: firestoreRules, host: fs.host, port: fs.port },
    storage: { rules: storageRules, host: st.host, port: st.port },
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearStorage();
  await env.clearFirestore();
  // Сидируем магазин/сотрудника (для cross-service isOwner/isStaff) в обход правил.
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "stores", STORE_ID), { ownerUid: OWNER, isPublic: true });
    await setDoc(doc(db, "stores", STORE_ID, "staff", STAFF), { role: "manager" });
    await uploadBytes(
      ref(ctx.storage(), `stores/${STORE_ID}/products/seed.png`),
      PNG_BYTES,
      IMAGE_META,
    );
  });
});

test("медиа витрины читаются публично (неаутентифицированный)", async () => {
  const storage = env.unauthenticatedContext().storage();
  await assertSucceeds(getBytes(ref(storage, `stores/${STORE_ID}/products/seed.png`)));
});

test("владелец может загрузить изображение", async () => {
  const storage = env.authenticatedContext(OWNER).storage();
  await assertSucceeds(
    uploadBytes(ref(storage, `stores/${STORE_ID}/products/p1.png`), PNG_BYTES, IMAGE_META),
  );
});

test("сотрудник может загрузить изображение", async () => {
  const storage = env.authenticatedContext(STAFF).storage();
  await assertSucceeds(
    uploadBytes(ref(storage, `stores/${STORE_ID}/products/p2.png`), PNG_BYTES, IMAGE_META),
  );
});

test("посторонний не может загрузить файл в чужой магазин", async () => {
  const storage = env.authenticatedContext(OUTSIDER).storage();
  await assertFails(
    uploadBytes(ref(storage, `stores/${STORE_ID}/products/x.png`), PNG_BYTES, IMAGE_META),
  );
});

test("не-изображение отклоняется даже у владельца", async () => {
  const storage = env.authenticatedContext(OWNER).storage();
  await assertFails(
    uploadBytes(ref(storage, `stores/${STORE_ID}/products/doc.txt`), PNG_BYTES, {
      contentType: "text/plain",
    }),
  );
});

test("файл больше 10 МБ отклоняется", async () => {
  const storage = env.authenticatedContext(OWNER).storage();
  const big = new Uint8Array(10 * 1024 * 1024 + 1);
  await assertFails(
    uploadBytes(ref(storage, `stores/${STORE_ID}/products/big.png`), big, IMAGE_META),
  );
});

test("запись вне stores/** запрещена", async () => {
  const storage = env.authenticatedContext(OWNER).storage();
  await assertFails(uploadBytes(ref(storage, "misc/x.png"), PNG_BYTES, IMAGE_META));
});
