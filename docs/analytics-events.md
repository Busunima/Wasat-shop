# Аналитика и продуктовые события (ТЗ §16)

Воронка FR-A05 (просмотр → корзина → заказ) измеряется явным сбором событий на базе
**Firebase Analytics (GA4)** с привязкой к `storeId`. Сырые события доступны в
**BigQuery** для платформенной аналитики.

## События

| Событие | Параметры | Назначение |
| --- | --- | --- |
| `product_view` | `storeId, productId` | Числитель воронки (просмотры) |
| `add_to_cart` | `storeId, productId, qty, price` | Корзина |
| `begin_checkout` | `storeId, value, items` | Старт оформления |
| `purchase` | `storeId, orderId, value, tax, currency` | Заказ (конверсия) |
| `search` | `storeId, query, results` | Качество поиска |
| `return_requested` | `storeId, orderId` | Возвраты |

## Агрегация

Агрегаты для дашборда владельца (§FR-A05) считаются **сервером** (Cloud Functions) и
складываются в `stores/{storeId}/analytics/{date}`. Источник «просмотров» в воронке —
именно эти продуктовые события (не realtime-listeners), что согласуется с паттернами
экономии чтений Firestore (см. [`decisions.md`](decisions.md), п. 6).

> Важно: `product_view` — клиентское GA4-событие; агрегат `analytics/{date}` пишется
> только сервером (правило `allow write: if false` для клиента, см.
> [`../firebase/firestore.rules`](../firebase/firestore.rules)).
