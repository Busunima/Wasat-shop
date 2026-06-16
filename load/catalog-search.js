import http from "k6/http";
import { check, sleep } from "k6";

/**
 * Нагрузочный тест каталога/поиска (ТЗ §14 Load, §12: поиск < 300мс).
 * Эндпоинт публичный (optionalAuth) — токен не нужен.
 *
 * Запуск:  k6 run -e BASE_URL=https://staging.api -e STORE_ID=<id> load/catalog-search.js
 */

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const STORE = __ENV.STORE_ID || "demo-store";
const QUERIES = ["", "shirt", "red", "shoes", "dress"];

export const options = {
  scenarios: {
    browse: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: 200 },
        { duration: "1m", target: 1000 }, // §12: 1000 одновременных без деградации
        { duration: "2m", target: 1000 },
        { duration: "30s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<300"], // §12: поиск < 300мс
  },
};

export default function () {
  const q = QUERIES[Math.floor(Math.random() * QUERIES.length)];
  const url = `${BASE}/api/stores/${STORE}/products?limit=20${q ? `&q=${encodeURIComponent(q)}` : ""}`;
  const res = http.get(url);
  check(res, { "status 200": (r) => r.status === 200 });
  sleep(1);
}
