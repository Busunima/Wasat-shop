# Канонический enum статусов заказа (ТЗ §FR-A04)

**Единый источник истины.** На этот список ссылаются обе стороны двойной валидации
(§13): Kotlin-enum в Android-клиенте и Zod-схема на сервере. Любое изменение —
только здесь, затем синхронно в `android/` и `server/`.

## Основной поток (happy path)

```
NEW → CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED
```

| Статус | Значение |
| --- | --- |
| `NEW` | Заказ создан (чекаут прошёл, оплата подтверждена вебхуком) |
| `CONFIRMED` | Подтверждён магазином |
| `PROCESSING` | В сборке |
| `SHIPPED` | Передан в доставку (есть `trackingNo`) |
| `DELIVERED` | Доставлен покупателю |
| `COMPLETED` | Завершён (терминальный успешный) |

## Терминальные статусы

```
CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED
```

| Статус | Значение |
| --- | --- |
| `CANCELLED` | Отменён |
| `RETURN_REQUESTED` | Запрошен возврат покупателем |
| `RETURNED` | Товар возвращён на склад |
| `REFUNDED` | Произведено возмещение (Stripe Refund) |

## Правила переходов

- **Отмена покупателем** (`«Отменить заказ»`, §FR-B06) допустима только в статусах
  `NEW` / `CONFIRMED` / `PROCESSING`.
- Смена статуса выполняется сервером через `POST /api/stores/:id/orders/:oid/status`
  (Owner/Staff) и сопровождается auto-push покупателю.
- Жизненный цикл возврата (§FR-A11) отслеживается отдельным enum в коллекции
  `returns/{rid}`: `REQUESTED → APPROVED/REJECTED → RECEIVED → REFUNDED`. Он связан
  с заказом, но не подменяет статус заказа.

## Рекомендуемое представление

```kotlin
// android — единый источник правды
enum class OrderStatus {
    NEW, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, COMPLETED,
    CANCELLED, RETURN_REQUESTED, RETURNED, REFUNDED
}
```

```ts
// server — Zod whitelist (§13: whitelist enum)
export const ORDER_STATUS = [
  "NEW", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "COMPLETED",
  "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED",
] as const;
export const orderStatusSchema = z.enum(ORDER_STATUS);
```
