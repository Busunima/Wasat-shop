import { Router } from "express";
import {
  optionalAuth,
  requireAuth,
  requireStoreRole,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { categoryCreateSchema, categoryUpdateSchema } from "../schemas/category.js";
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
} from "../services/categories.js";

/**
 * Категории магазина (FR-A01): /api/stores/:storeId/categories/*.
 * Чтение — публичное (витрина строит фильтр каталога); CRUD — только владелец.
 * mergeParams — :storeId из родительского роутера.
 */
export const categoriesRouter: Router = Router({ mergeParams: true });

function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

// Публичный список категорий (для фильтра каталога)
categoriesRouter.get("/", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    res.json({ items: await listCategories(param(req, "storeId")) });
  } catch (err) {
    next(err);
  }
});

// Дальше — только владелец магазина
categoriesRouter.use(verifyAppCheck, requireAuth, requireStoreRole);

categoriesRouter.post("/", async (req: AuthedRequest, res, next) => {
  try {
    const body = categoryCreateSchema.parse(req.body);
    res.status(201).json(await createCategory(param(req, "storeId"), body));
  } catch (err) {
    next(err);
  }
});

categoriesRouter.patch("/:cid", async (req: AuthedRequest, res, next) => {
  try {
    const patch = categoryUpdateSchema.parse(req.body);
    res.json(await updateCategory(param(req, "storeId"), param(req, "cid"), patch));
  } catch (err) {
    next(err);
  }
});

categoriesRouter.delete("/:cid", async (req: AuthedRequest, res, next) => {
  try {
    await deleteCategory(param(req, "storeId"), param(req, "cid"));
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});
