# Модель данных — Firestore (ТЗ §8)

Иерархия строится под `stores/{storeId}`. Документ магазина содержит `ownerUid` и
флаг `isPublic`. Доступ сотрудников определяется документом
`stores/{storeId}/staff/{uid}`. Правила доступа — [`../firebase/firestore.rules`](../firebase/firestore.rules).

```
stores/{storeId}/  (doc)        — настройки + ownerUid + isPublic + plan + slug + currency
  products/{pid}      categories/{cid}     orders/{oid}
  customers/{uid}     reviews/{rid}        promocodes/{code}
  staff/{uid}         analytics/{date}     fcmTokens/{uid}
  inventoryLog/{id}   auditLog/{id}        returns/{rid}
  subscription (doc)  articles/{aid}  (Post-MVP, зарезервировано)
slugs/{slug}   — обратный индекс slug -> storeId (публичный)
users/{uid}    — список магазинов пользователя (владелец/сотрудник)
```

## `stores/{storeId}`

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` | string | UUID магазина |
| `slug` | string | Публичный человекочитаемый адрес |
| `ownerUid` / `ownerEmail` | string | uid и email владельца |
| `name` / `description` | string | Название / описание |
| `logoUrl` / `bannerUrl` | string | Медиа |
| `theme` | map | `{ primary, secondary, font }` |
| `currency` | string | ISO-4217 (суммы — в минорных единицах) |
| `taxConfig` | map | Конфигурация Stripe Tax |
| `contact` | map | `{ email, phone, address }` |
| `isPublic` / `isBlocked` | boolean | Витрина открыта / заблокирован суперадмином |
| `plan` | string | `'free' \| 'basic' \| 'pro' \| 'enterprise'` |
| `stripeAccountId` | string | ID connected-аккаунта Stripe Connect |
| `subscription` | map | `{ plan, status, currentPeriodEnd, pspCustomerId }` |
| `createdAt` | timestamp | Дата создания |

## `products/{pid}`

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` / `name` / `description` | string | Идентификатор / название / описание |
| `price` / `originalPrice` | number | Цена / старая цена в минорных единицах валюты магазина |
| `images` | string[] | URL из Storage (+ миниатюры) |
| `category` / `tags` | string / string[] | Категория / теги |
| `variants` | map[] | `[{ size, color, stock, sku }]` |
| `totalStock` | number | Производное (сумма stock вариантов), пишет сервер |
| `sku` / `barcode` | string\|null | Артикул и штрихкод уровня товара (FR-A02) |
| `rating` / `reviewCount` | number | Агрегаты отзывов (FR-B08), пересчёт сервером |
| `ratingSum` | number | Внутренний накопитель суммы оценок (для инкрементального `rating`) |
| `status` | string | `'active' \| 'draft' \| 'archived'` |
| `createdAt` | timestamp | Дата создания |

## Локальная корзина (клиент, Room — не Firestore)

До слияния с серверной корзиной (Фаза 4, чекаут) корзина живёт на устройстве:
таблица `cart_items`, ключ позиции — `storeId + productId + variantKey`
(`variantKey` = нормализованное `"size=M;color=red"`, `""` без вариантов). Цена
фиксируется в минорных единицах на момент добавления и пересверяется сервером на
чекауте; количество ограничено 1–99; корзина ведётся помагазинно.

## `orders/{oid}`

| Поле | Тип | Описание |
| --- | --- | --- |
| `id` / `customerUid` | string | `customerUid` (uid) или `null` для гостя |
| `customerEmail` | string | Email покупателя |
| `items` | map[] | `[{ productId, name, qty, price, variant }]` |
| `subtotal` / `tax` / `discount` / `total` | number | Разложение суммы (минорные единицы) |
| `currency` | string | ISO-4217 |
| `promoCode` | string | Применённый промокод |
| `status` | string | Канонический enum — см. [`order-status.md`](order-status.md) |
| `delivery` | map | `{ method, address, cost, trackingNo }` |
| `payment` | map | `{ method, stripePaymentIntentId, paidAt }`; в MVP-ядре `method='deferred'` (Stripe — позже) |
| `idempotencyKey` | string | Ключ идемпотентности; id заказа = hash(uid+ключ) |
| `createdAt` | timestamp | Дата создания |

## Прочие коллекции

| Коллекция | Ключевые поля |
| --- | --- |
| `categories/{cid}` | `name, slug, parentId, order, imageUrl` |
| `reviews/{rid}` | `productId, customerUid, rating, text, photos[], orderId, createdAt, updatedAt` (id = hash(uid+productId): один на товар) |
| `promocodes/{code}` | `type, value, minAmount, startsAt, expiresAt, usageLimit, usedCount, scope` |
| `customers/{uid}` | `email, name, phone, wishlist[], addresses[], stripeCustomerId, totalSpent, ordersCount` |
| `fcmTokens/{uid}` | `tokens[], platform, updatedAt` (FR-B10; пишет сервер) |
| `staff/{uid}` | `uid, email, role ('manager'\|'staff'), addedAt` (FR-A09) |
| `inventoryLog/{id}` | `productId, variant, delta, reason, byUid, at` |
| `auditLog/{id}` | `actorUid, action, target, meta, at` |
| `returns/{rid}` | `orderId, items[], reason, status, refundAmount, stripeRefundId, createdAt` |
| `invoices/{id}` (платформа) | `storeId, amount, currency, status, periodStart, periodEnd` |
| `articles/{aid}` (Post-MVP) | `title, body, coverUrl, status, publishedAt` — зарезервировано |

> Суммы хранятся **в минорных единицах** валюты магазина (центы/копейки) во избежание
> ошибок округления. `totalStock`, `rating`, `reviewCount` и агрегаты аналитики —
> производные, пишутся только сервером через Admin SDK.
