# REST API — ключевые эндпоинты (ТЗ §9)

Сервер выполняет операции, недоступные клиенту: транзакционный чекаут, агрегаты,
рассылки, вебхуки, биллинг. **Все запросы** (кроме вебхуков Stripe и cron) —
`Authorization: Bearer {Firebase ID Token}` + App Check token.

## Эндпоинты (реализовано)

Таблица отражает фактическую поверхность сервера. Роли: **Public¹** — `optionalAuth`;
**Member** — владелец/менеджер/сотрудник (`requireStoreStaff`); **Owner** — только
владелец (`requireStoreRole`); **Buyer** — любой авторизованный покупатель.

### Магазин и онбординг

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| POST | `/api/stores/init` | Создание магазина, storeId, claim, Stripe-онбординг (deferred без ключей) | Authenticated |
| GET | `/api/stores/:id` | Публичная карточка магазина | Public¹ |
| GET | `/api/stores/by-slug/:slug` | Резолв витрины по slug (deep link/QR, FR-B01) | Public |
| PATCH | `/api/stores/:id` | Настройки магазина (FR-A01) | Owner |
| GET | `/api/stores/:id/plan` | Тариф, лимиты и использование (FR-S03) | Owner |

### Товары, инвентарь, рекомендации, отзывы

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| GET | `/api/stores/:id/products` | Листинг (фильтры/сортировка/курсор, FR-B02) | Public¹ |
| GET | `/api/stores/:id/products/:pid` | Карточка товара | Public¹ |
| POST/PATCH/DELETE | `/api/stores/:id/products[/:pid]` | CRUD товара (FR-A02) | Member |
| POST | `/api/stores/:id/inventory/products/:pid/stock` | Корректировка остатка (FR-A03) | Member |
| POST | `/api/stores/:id/inventory/import` | CSV-импорт остатков (text/csv) | Member |
| GET | `/api/stores/:id/inventory/log` | История изменений остатков | Member |
| GET | `/api/stores/:id/recommendations/popular` | Популярное (FR-B12) | Public¹ |
| GET | `/api/stores/:id/recommendations/related/:pid` | Похожие товары (FR-B12) | Public¹ |
| GET | `/api/stores/:id/products/:pid/reviews` | Список отзывов (FR-B08) | Public¹ |
| POST | `/api/stores/:id/products/:pid/reviews` | Отзыв (право — по заказу) | Buyer |
| DELETE | `/api/stores/:id/products/:pid/reviews/:rid` | Удаление отзыва (модерация) | Owner |

### Промокоды, аналитика, сотрудники, push, AI

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| GET/POST/DELETE | `/api/stores/:id/promocodes[/:code]` | CRUD промокодов (FR-A06) | Owner |
| POST | `/api/stores/:id/promocodes/preview` | Предпросмотр скидки для корзины | Public (+App Check) |
| POST | `/api/stores/:id/events` | Продуктовое событие (§16) | Public (+App Check) |
| GET | `/api/stores/:id/analytics` | Дашборд воронки/выручки (FR-A05) | Owner |
| GET/POST/PATCH/DELETE | `/api/stores/:id/staff[/:uid]` | Сотрудники и роли (FR-A09) | Owner |
| POST | `/api/stores/:id/push-tokens` | Регистрация FCM-токена (FR-B10) | Buyer |
| POST | `/api/stores/:id/ai/describe` | AI-описание товара (FR-A12; 501 без ключа) | Member |

### Заказы (ядро, оплата deferred)

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| POST | `/api/checkout` | Транзакц. заказ + списание стока + промокод (FR-B05) | Buyer (+App Check) |
| GET | `/api/stores/:id/orders` | Список заказов магазина, фильтр по статусу (FR-A04) | Member |
| GET | `/api/stores/:id/orders/my` | Заказы покупателя (FR-B06) | Buyer |
| GET | `/api/stores/:id/orders/:oid` | Карточка заказа (member или свой) | Authenticated |
| POST | `/api/stores/:id/orders/:oid/status` | Смена статуса (валидация переходов) + push | Member |
| POST | `/api/stores/:id/orders/:oid/cancel` | Отмена покупателем до отгрузки + ресток | Buyer |

### Суперадмин (FR-S01–S04)

| Метод | Путь | Описание | Auth |
| --- | --- | --- | --- |
| GET | `/api/admin/stores` | Список магазинов (поиск q/plan/blocked, курсор) | Superadmin |
| PATCH | `/api/admin/stores/:id/block` | Блокировка/разблокировка + auditLog | Superadmin |
| PATCH | `/api/admin/stores/:id/plan` | Смена тарифа + auditLog | Superadmin |
| GET | `/api/admin/analytics` | Глобальная аналитика (GMV/MAU/тарифы) | Superadmin |

### Запланировано (Фаза 4, блокируется Stripe/Algolia)

| Метод | Путь | Описание | Блокер |
| --- | --- | --- | --- |
| — | Stripe PaymentSheet/Tax в `/api/checkout` | Реальная оплата вместо deferred | Stripe test-ключ |
| POST | `/api/stores/:id/returns/:rid/resolve` | Возврат + Stripe Refund (FR-A11/B09) | Stripe |
| POST | `/api/webhooks/stripe[-billing]` | Вебхуки платежей/подписок (FR-S05) | Stripe |
| GET | `/api/stores/:id/stripe/onboard-link` | Онбординг Stripe Connect | Stripe |
| POST | `/api/search/reindex` | Переиндексация (Algolia, FR-B02) | решение Algolia |
| POST | `/api/cron/cleanup` | Очистка устаревших данных | — |

¹ **Public с повышением прав** (`optionalAuth`): анонимный запрос видит только
`active`-товары и магазин с `isPublic && !isBlocked`; запрос с member-токеном
(claims.storeId = :id, роль owner/manager/staff) видит все статусы и непубличный магазин.

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
| 409 | `CONFLICT` | Недопустимый переход статуса / занятый slug-код / дубль |
| 422 | `OUT_OF_STOCK` / `PROMO_INVALID` | Бизнес-валидация чекаута |
| 501 | `NOT_IMPLEMENTED` | Фича env-gated без ключа (напр. AI без ANTHROPIC_API_KEY) |
| 429 | `RATE_LIMITED` | Превышен лимит (Memorystore) |
| 500 | `INTERNAL` | Непредвиденная ошибка |

## Ключевые контракты

- **`/api/checkout`** — единственный путь создания заказа: серверная транзакция с
  пересчётом цен (клиентским суммам не доверяем, §13), атомарным списанием стока
  (товары и варианты) и валидацией промокода (`applyPromo`). **Идемпотентность** по
  `idempotencyKey`: id заказа детерминирован от `uid+idempotencyKey`, повтор
  возвращает **тот же заказ со статусом 200** (без повторного списания). Оплата в
  MVP-ядре — `payment.method = "deferred"`; Stripe PaymentIntent/Tax встраиваются в
  ту же транзакцию при появлении ключей (паттерн env-gated, как Stripe-онбординг).
  Побочно (fire-and-forget): `purchase`-событие в аналитику + push владельцу.
- **Смена статуса** заказа использует канонический enum и таблицу допустимых
  переходов (`ALLOWED_TRANSITIONS`, зеркало на клиенте `OrderTransitions`) — см.
  [`order-status.md`](order-status.md); отмена до отгрузки восстанавливает сток.
- **Отзыв** (`POST .../reviews`) принимается только если у покупателя есть заказ в
  статусе `DELIVERED`/`COMPLETED`, содержащий товар; один отзыв на (товар,
  пользователь), агрегаты `rating`/`reviewCount` товара пересчитываются в транзакции.
- **AI-ассист** (`/ai/describe`) и **Stripe** — env-gated: без ключа отвечают
  `NOT_IMPLEMENTED` (501) / `deferred`; сборка и тесты ключей не требуют.
- **Вебхуки Stripe** аутентифицируются проверкой подписи (без Firebase-токена);
  секрет — без fallback в production (§13).
- **Custom Claims** (`{storeId, role}`, `superadmin`) выставляет **только сервер**.
