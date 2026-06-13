import assert from "node:assert/strict";
import { before, test } from "node:test";
import { db } from "../../src/lib/firebase.ts";
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
} from "../../src/services/categories.ts";
import { categoryCreateSchema, categoryUpdateSchema } from "../../src/schemas/category.ts";
import { ApiError } from "../../src/middleware/errorHandler.ts";

/**
 * Интеграционные тесты FR-A01 (категории) против эмулятора Firestore.
 * Запуск: npm run test:integration.
 */

const STORE_ID = "categories-store";

before(async () => {
  await db().collection("stores").doc(STORE_ID).set({
    id: STORE_ID,
    slug: "categories-shop",
    name: "Categories Shop",
    ownerUid: "owner-cat",
    currency: "USD",
    isPublic: true,
    isBlocked: false,
  });
});

test("createCategory: дерево, сортировка по order, уникальность slug", async () => {
  const shoes = await createCategory(
    STORE_ID,
    categoryCreateSchema.parse({ name: "Обувь", slug: "shoes", order: 1 }),
  );
  const sneakers = await createCategory(
    STORE_ID,
    categoryCreateSchema.parse({ name: "Кеды", slug: "sneakers", parentId: shoes.id, order: 0 }),
  );
  assert.equal(sneakers.parentId, shoes.id);

  // дубликат slug — CONFLICT
  await assert.rejects(
    () => createCategory(STORE_ID, categoryCreateSchema.parse({ name: "X", slug: "shoes" })),
    (e: unknown) => e instanceof ApiError && e.code === "CONFLICT",
  );
  // несуществующий родитель — NOT_FOUND
  await assert.rejects(
    () =>
      createCategory(
        STORE_ID,
        categoryCreateSchema.parse({ name: "Y", slug: "cat-y", parentId: "ghost" }),
      ),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );

  const all = await listCategories(STORE_ID);
  assert.deepEqual(all.map((c) => c.slug), ["sneakers", "shoes"]); // order 0,1
});

test("updateCategory: переименование, перенос в корень, защита от цикла", async () => {
  const a = await createCategory(STORE_ID, categoryCreateSchema.parse({ name: "A", slug: "cat-a" }));
  const b = await createCategory(
    STORE_ID,
    categoryCreateSchema.parse({ name: "B", slug: "cat-b", parentId: a.id }),
  );

  const renamed = await updateCategory(STORE_ID, a.id, categoryUpdateSchema.parse({ name: "A2" }));
  assert.equal(renamed.name, "A2");

  // цикл: a → b → a запрещён
  await assert.rejects(
    () => updateCategory(STORE_ID, a.id, categoryUpdateSchema.parse({ parentId: b.id })),
    (e: unknown) => e instanceof ApiError && e.code === "CONFLICT",
  );
  // сам себе родитель — VALIDATION_ERROR
  await assert.rejects(
    () => updateCategory(STORE_ID, a.id, categoryUpdateSchema.parse({ parentId: a.id })),
    (e: unknown) => e instanceof ApiError && e.code === "VALIDATION_ERROR",
  );

  // перенос b в корень
  const moved = await updateCategory(STORE_ID, b.id, categoryUpdateSchema.parse({ parentId: null }));
  assert.equal(moved.parentId, null);
});

test("deleteCategory: с подкатегориями — CONFLICT; лист — удаляется", async () => {
  const parent = await createCategory(
    STORE_ID,
    categoryCreateSchema.parse({ name: "P", slug: "del-parent" }),
  );
  const child = await createCategory(
    STORE_ID,
    categoryCreateSchema.parse({ name: "C", slug: "del-child", parentId: parent.id }),
  );

  await assert.rejects(
    () => deleteCategory(STORE_ID, parent.id),
    (e: unknown) => e instanceof ApiError && e.code === "CONFLICT",
  );

  await deleteCategory(STORE_ID, child.id);
  await deleteCategory(STORE_ID, parent.id); // теперь лист
  const remaining = await listCategories(STORE_ID);
  assert.ok(!remaining.some((c) => c.id === parent.id || c.id === child.id));

  // удаление несуществующей — NOT_FOUND
  await assert.rejects(
    () => deleteCategory(STORE_ID, "ghost"),
    (e: unknown) => e instanceof ApiError && e.code === "NOT_FOUND",
  );
});
