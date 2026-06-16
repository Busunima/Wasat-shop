import http from "k6/http";
import { check } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

/**
 * Нагрузочный тест чекаута (ТЗ §14 Load, §12: чекаут < 3с). POST /api/checkout —
 * транзакционный, требует Firebase ID-token покупателя. idempotencyKey уникален на
 * итерацию (сервер идемпотентен по ключу — повтор вернёт тот же заказ).
 *
 * Запуск:
 *   k6 run -e BASE_URL=https://staging.api -e STORE_ID=<id> \
 *          -e PRODUCT_ID=<pid> -e ID_TOKEN=<firebase-id-token> load/checkout.js
 *
 * Примечание: на проде сток ограничен — для устойчивой нагрузки используйте
 * тестовый магазин с большим запасом или товар без вариантов с высоким totalStock.
 */

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const STORE = __ENV.STORE_ID || "demo-store";
const PRODUCT = __ENV.PRODUCT_ID || "demo-product";
const TOKEN = __ENV.ID_TOKEN;

export const options = {
  scenarios: {
    checkout: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 100 },
        { duration: "1m", target: 500 },
        { duration: "1m", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.02"],
    http_req_duration: ["p(95)<3000"], // §12: чекаут < 3с
  },
};

export default function () {
  if (!TOKEN) throw new Error("ID_TOKEN обязателен для нагрузки чекаута");
  const body = JSON.stringify({
    storeId: STORE,
    items: [{ productId: PRODUCT, qty: 1 }],
    delivery: { method: "pickup" },
    idempotencyKey: uuidv4(),
  });
  const res = http.post(`${BASE}/api/checkout`, body, {
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${TOKEN}` },
  });
  check(res, { "created or replayed": (r) => r.status === 200 || r.status === 201 });
}
