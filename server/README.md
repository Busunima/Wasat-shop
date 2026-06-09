# Server — Express + TypeScript

Минимальный сервер: выполняет операции, недоступные клиенту напрямую —
транзакционный чекаут, платёжные вебхуки, рассылки, агрегаты, биллинг (ТЗ §2, §9).
Деплой — **Cloud Run** (serverless, авто-масштаб; `min instances > 0` для чекаута,
без cold start).

> Статус: **каркас.** `npm init` ещё не выполнен — это первый шаг Фазы 1.

## Стек

| Область | Технология |
| --- | --- |
| Рантайм / фреймворк | Node.js · Express.js · TypeScript |
| Firebase | Firebase Admin SDK (Firestore, Auth, FCM, Custom Claims) |
| Платежи | Stripe SDK (Connect + Billing + Tax) |
| Валидация | Zod (whitelist enum, см. `docs/order-status.md`) |
| Почта | Nodemailer / SMTP |
| Поиск | Algolia (индексация по триггерам) |
| Rate limit | Memorystore (Redis) |
| Хостинг | Cloud Run · Cloud Scheduler (cron) · Secret Manager |

## Эндпоинты

Полный контракт — [`../docs/api-contract.md`](../docs/api-contract.md). Все запросы
(кроме вебхуков Stripe и cron) требуют `Authorization: Bearer {Firebase ID Token}` +
App Check token. Ключевые:

- `POST /api/stores/init` — создание магазина (UUID/slug, Custom Claim, Stripe-онбординг)
- `POST /api/checkout` — транзакц. заказ + сток + PaymentIntent + Stripe Tax, идемпотентность
- `POST /api/stores/:id/orders/:oid/status` — смена статуса + push
- `POST /api/stores/:id/returns/:rid/resolve` — возврат + Stripe Refund
- `POST /api/webhooks/stripe`, `POST /api/webhooks/stripe-billing` — проверка подписи

## Предполагаемая структура

```
src/
  index.ts                   — bootstrap Express, middleware
  middleware/
    auth.ts                  — verifyIdToken + App Check + роль (Owner/Staff/Superadmin)
    rateLimit.ts             — Memorystore-счётчик
    errorHandler.ts          — единый контракт ошибок (docs/api-contract.md)
  routes/
    stores.ts  checkout.ts  orders.ts  returns.ts  admin.ts  webhooks.ts  search.ts  cron.ts
  schemas/                   — Zod-схемы (включая orderStatus, синхрон с docs/order-status.md)
  services/
    stripe.ts  firestore.ts  algolia.ts  mail.ts  llm.ts
  lib/                       — utils, logger (маскирование email)
```

## Секреты (ТЗ §13)

`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_BILLING_WEBHOOK_SECRET`,
`CRON_SECRET`, SMTP-креды, Algolia-ключи — через Google Secret Manager / переменные
Cloud Run. **Без fallback в production.** Локально — `.env` (см. `.env.example`,
не коммитится).

## Запуск (после инициализации)

```bash
cd server
npm install
npm run dev          # локальный сервер против Firebase-эмуляторов
npm run lint         # eslint
npm test             # unit (Zod-схемы, сервисы)
npm run build        # tsc -> dist/
```
