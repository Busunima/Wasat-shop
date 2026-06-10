import { Router } from "express";
import {
  optionalAuth,
  requireAuth,
  requireStoreRole,
  type AuthedRequest,
} from "../middleware/auth.js";
import { verifyAppCheck } from "../middleware/appCheck.js";
import { productCreateSchema, productUpdateSchema } from "../schemas/product.js";
import {
  createProduct,
  deleteProduct,
  getProduct,
  listProducts,
  updateProduct,
} from "../services/products.js";

/**
 * Товары магазина (FR-A02 + чтение витрины): /api/stores/:storeId/products.
 * Запись — только владелец (Custom Claims); чтение публичное: посетитель видит
 * active, владелец — все статусы (optionalAuth).
 * mergeParams: true — доступ к :storeId родительского роутера.
 */
export const productsRouter: Router = Router({ mergeParams: true });

/** Path-параметр строкой (типы params допускают string[] для wildcard-роутов). */
function param(req: AuthedRequest, name: string): string {
  const value = req.params[name];
  return Array.isArray(value) ? (value[0] ?? "") : (value ?? "");
}

function isStoreOwner(req: AuthedRequest): boolean {
  return (
    req.claims?.["storeId"] === param(req, "storeId") && req.claims?.["role"] === "owner"
  );
}

productsRouter.get("/", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    const items = await listProducts(param(req, "storeId"), isStoreOwner(req));
    res.json({ items });
  } catch (err) {
    next(err);
  }
});

productsRouter.get("/:productId", optionalAuth, async (req: AuthedRequest, res, next) => {
  try {
    const product = await getProduct(
      param(req, "storeId"),
      param(req, "productId"),
      isStoreOwner(req),
    );
    res.json(product);
  } catch (err) {
    next(err);
  }
});

productsRouter.post(
  "/",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      const body = productCreateSchema.parse(req.body);
      const product = await createProduct(param(req, "storeId"), body);
      res.status(201).json(product);
    } catch (err) {
      next(err);
    }
  },
);

productsRouter.patch(
  "/:productId",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      const body = productUpdateSchema.parse(req.body);
      const product = await updateProduct(
        param(req, "storeId"),
        param(req, "productId"),
        body,
      );
      res.json(product);
    } catch (err) {
      next(err);
    }
  },
);

productsRouter.delete(
  "/:productId",
  verifyAppCheck,
  requireAuth,
  requireStoreRole,
  async (req: AuthedRequest, res, next) => {
    try {
      await deleteProduct(param(req, "storeId"), param(req, "productId"));
      res.status(204).end();
    } catch (err) {
      next(err);
    }
  },
);
