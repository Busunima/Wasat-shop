# REST API — ключевые эндпоинты (ТЗ §9)

Сервер выполняет операции, недоступные клиенту: транзакционный чекаут, агрегаты,
рассылки, вебхуки, биллинг. **Все запросы** (кроме вебхуков Stripe и cron) —
`Authorization: Bearer {Firebase ID Token}` + App Check token.

## Эндпоинты

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| POST | `/api/stores/init` | Создание магазина, storeId, claim, Stripe-онбординг | Authenticated |
| GET | `/api/stores/:id` | Публичная карточка магазина (slug, name, currency) | Public¹ |
| GET | `/api/stores/:id/products` | Листинг товаров (`{items}`) | Public¹ |
| GET | `/api/stores/:id/products/:pid` | Карточка товара | Public¹ |
| POST | `/api/stores/:id/products` | Создание товара (201) | Owner |
| PATCH | `/api/stores/:id/products/:pid` | Частичное обновление товара | Owner |
| DELETE | `/api/stores/:id/products/:pid` | Удаление товара (204) | Owner |
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
| GET | `/api/admin/stores` | Список магазинов (поиск q/plan/blocked, курсор) | Superadmin |
| PATCH | `/api/admin/stores/:id/block` | Блокировка/разблокировка + auditLog | Superadmin |
| PATCH | `/api/admin/stores/:id/plan` | Смена тарифа + auditLog | Superadmin |
| POST | `/api/cron/cleanup` | Очистка устаревших данных | `CRON_SECRET` |

¹ **Public с повышением прав** (`optionalAuth`): анонимный запрос видит только
`active`-товары и магазин с `isPublic && !isBlocked`; запрос с owner-токеном
(claims.storeId = :id, role = owner) видит все статусы и свой непубличный магазин.

> Чтение каталога идёт через REST (а не клиентский Firestore SDK, как предполагал §15
> ТЗ) — причины см. `decisions.md`. Прямое чтение Firestore с офлайн-кэшем добавляется
> треком «Офлайн» Фазы 2 как оптимизация, REST остаётся канонической поверхностью.

## Семантика partial-PATCH

Правило для всех PATCH-эндпоинтов (реализовано в товарах):

| Во входном JSON | Действие сервера |
| --- | --- |
| Поле **отсутствует** | поле не изменяется |
| Опциональное строковое поле = `""` (или `null`) | поле **очищается** (хранится `null`) |
| `originalPrice: null` | старая цена сбрасывается |
| `variants` присутствует | `totalStock` пересчитывается сервером |

Клиент-форма владеет полным состоянием и поэтому кодирует все поля всегда
(см. `ProductUpsertRequest` — null/пустые значения не опускаются).

## Границы валидации (единый источник: zod-схемы `server/src/schemas/`)

| Поле | Ограничение |
| --- | --- |
| `slug` магазина | 3–40, `^[a-z0-9]+(?:-[a-z0-9]+)*$` |
| `name` магазина / товара | 1–120 / 1–200 |
| `description` магазина / товара | ≤2000 / ≤5000 |
| `price`, `originalPrice` | целые ≥0, **минорные единицы** валюты магазина |
| `currency` | ISO-4217, 3 заглавные буквы |
| `sku`, `barcode` (товар и вариант) | ≤64 |
| `category` | ≤80 |
| `tags` | ≤20 шт., каждый 1–40 |
| `images` | ≤10, валидные URL |
| `variants` | ≤100; `stock` целое ≥0 |
| Количество позиции в корзине (клиент) | 1–99 |

Android-зеркала: `StoreValidation`, `ProductFormValidation`, `TagsParser` — обязаны
совпадать со схемами сервера (покрыты unit-тестами с обеих сторон).

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
