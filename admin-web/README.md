# Admin Web — веб-панель суперадмина

Отдельное **SPA на React + TypeScript** для роли суперадминистратора платформы
(ТЗ §7). **Не входит в Android-клиент.** Доступ — Custom Claim `superadmin: true`.

> Статус: **каркас.** Проект ещё не инициализирован. Веб-панель оценивается и
> разрабатывается **отдельно** от MVP мобильного приложения (+150–250 ч, см.
> [`../docs/roadmap.md`](../docs/roadmap.md)).

## Назначение (функции, ТЗ §7)

| FR | Функция |
| --- | --- |
| FR-S01 | Список всех магазинов с метриками (поиск по `storeId`, `slug`, `ownerEmail`, названию) |
| FR-S02 | Управление магазином: блокировка, смена тарифа, просмотр для поддержки (read-only, `auditLog`) |
| FR-S03 | Тарифные планы: лимиты товаров/заказов/хранилища |
| FR-S04 | Глобальная аналитика: всего/активных/новых магазинов, GMV (приведение валют), MAU |
| FR-S05 | Биллинг подписок (Stripe Billing): циклы, инвойсы, dunning, grace-period |

## Стек (предполагаемый)

| Область | Технология |
| --- | --- |
| Фреймворк | React + TypeScript + Vite |
| Аутентификация | Firebase Auth (Google) — проверка claim `superadmin` |
| Данные | REST к Express-серверу (`/api/admin/*`) с ID Token + App Check |
| UI | по выбору команды (MUI / Ant Design / собственная) |
| Валидация | Zod (общие схемы переиспользуются из контрактов) |

## Доступ к данным

Веб-панель работает **только через серверные эндпоинты** `/api/admin/*`
([`../docs/api-contract.md`](../docs/api-contract.md)) — `GET /api/admin/stores`,
`PATCH /api/admin/stores/:id/block`. Прямого доступа к Firestore в обход сервера нет.

## Запуск (после инициализации)

```bash
cd admin-web
npm install
npm run dev          # Vite dev server
npm run lint
npm test
npm run build
```
