# REST API — ключевые эндпоинты (ТЗ §9)

Сервер выполняет операции, недоступные клиенту: транзакционный чекаут, агрегаты,
рассылки, вебхуки, биллинг. **Все запросы** (кроме вебхуков Stripe и cron) —
`Authorization: Bearer {Firebase ID Token}` + App Check token.

## Эндпоинты

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| POST | `/api/stores/init` | Создание магазина, storeId, claim, Stripe-онбординг | Authenticated |
| GET | `/api/stores/:id/analytics` | Аналитика за период | Owner/Staff |
| POST | `/api/checkout` | Транзакц. заказ + сток + Stripe PaymentIntent + налог | Any (+App Check) |
| POST | `/api/stores/:id/orders/:oid/status` | Смена статуса + уведомление | Owner/Staff |
| POST | `/api/stores/:id/returns/:rid/resolve` | Решение по возврату + Stripe Refund | Owner/Staff |
| POST | `/api/stores/:id/notify` | Push-рассылка | Owner/Staff |
| POST | `/api/stores/:id/staff/invite` | Приглашение сотрудника | Owner |
| GET | `/api/stores/:id/stripe/onboard-link` | Ссылка онбординга Stripe Connect | Owner |
| POST | `/api/webhooks/stripe` | Вебхук Stripe (платежи, возвраты) | Stripe signature |
| POST | `/api/webhooks/stripe-billing` | Вебхук подписок SaaS | Stripe signature |
| POST | `/api/search/reindex` | Переиндексация товаров (Algolia) | Owner (internal) |
| GET | `/api/admin/stores` | Список магазинов | Superadmin |
| PATCH | `/api/admin/stores/:id/block` | Блокировка | Superadmin |
| POST | `/api/cron/cleanup` | Очистка устаревших данных | `CRON_SECRET` |

## Контракт ошибок

Единый формат ответа об ошибке (JSON):

```json
{ "error": { "code": "STRING_ENUM", "message": "человекочитаемое описание", "details": {} } }
```

| HTTP | `code` (примеры) | Когда |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Невалидный body (Zod) — `details` содержит issues |
| 401 | `UNAUTHENTICATED` | Нет/невалиден Firebase ID Token |
| 401 | `APP_CHECK_FAILED` | Отсутствует/невалиден App Check token |
| 403 | `FORBIDDEN` | Нет требуемой роли (Owner/Staff/Superadmin) |
| 404 | `NOT_FOUND` | Ресурс не найден / нет доступа (без утечки факта существования) |
| 409 | `CONFLICT` | Идемпотентный повтор / гонка стока |
| 422 | `OUT_OF_STOCK` / `PROMO_INVALID` | Бизнес-валидация чекаута |
| 429 | `RATE_LIMITED` | Превышен лимит (Memorystore) |
| 500 | `INTERNAL` | Непредвиденная ошибка |

## Ключевые контракты

- **`/api/checkout`** — единственный путь создания заказа: серверная транзакция с
  атомарным списанием стока, расчётом налога (Stripe Tax) и валидацией промокода.
  **Идемпотентность** по `idempotencyKey` (защита от двойного списания, §FR-B05).
- **Смена статуса** заказа использует канонический enum — см.
  [`order-status.md`](order-status.md); после смены сервер шлёт push покупателю.
- **Вебхуки Stripe** аутентифицируются проверкой подписи (без Firebase-токена);
  секрет — без fallback в production (§13).
- **Custom Claims** (`владелец storeId`, `superadmin`) выставляет **только сервер**.
