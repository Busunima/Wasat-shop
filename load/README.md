# Нагрузочные тесты (k6) — ТЗ §14 Load

Сценарии нагрузки для критических путей (§12: «чекаут и поиск под нагрузкой,
1000 одновременных без деградации»). Запускаются **вручную** против staging —
в CI не гоняются (чекаут требует Firebase ID-token, а полноценная нагрузка не
должна бить эмулятор/прод).

## Установка

```
brew install k6        # или https://k6.io/docs/get-started/installation/
```

## Сценарии

| Файл | Путь | Auth | Порог (ТЗ §12) |
|------|------|------|----------------|
| `catalog-search.js` | `GET /api/stores/:id/products` (поиск/фильтр) | не нужен (публичный) | p95 < 300 мс, ошибки < 1%, до 1000 VU |
| `checkout.js` | `POST /api/checkout` | Firebase ID-token | p95 < 3 с, ошибки < 2% |

## Запуск

Каталог/поиск (без токена):

```
k6 run -e BASE_URL=https://staging.example.com -e STORE_ID=<storeId> load/catalog-search.js
```

Чекаут (нужен токен покупателя и тестовый товар с большим запасом):

```
k6 run \
  -e BASE_URL=https://staging.example.com \
  -e STORE_ID=<storeId> \
  -e PRODUCT_ID=<productId> \
  -e ID_TOKEN=<firebase-id-token> \
  load/checkout.js
```

`ID_TOKEN` получается из приложения (Firebase Auth) или через REST Identity Toolkit
для тестового пользователя. Для устойчивого прогона чекаута используйте тестовый
магазин с товаром без вариантов и высоким `totalStock` (иначе нагрузка упрётся в
`OUT_OF_STOCK`, что само по себе корректное поведение).

## Критерии приёмки (§14)

Прогон считается успешным, если k6 не нарушил `thresholds` (заданы в каждом
сценарии): доля ошибок и p95-латентность в пределах целей §12.
