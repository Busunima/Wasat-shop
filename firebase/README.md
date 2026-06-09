# Firebase — конфигурация и правила

Backend-сервисы платформы: Auth, Firestore, Storage, FCM, App Check, Crashlytics.

## Проекты и окружения

Отдельные Firebase-проекты **dev / staging / production** (ТЗ §15). Привязка через
`.firebaserc` (создаётся при инициализации, не коммитим реальные id до настройки):

```jsonc
// .firebaserc (пример)
{
  "projects": {
    "dev":        "wasat-shop-dev",
    "staging":    "wasat-shop-staging",
    "production": "wasat-shop-prod"
  }
}
```

## Регион Firestore

**Принято:** единый регион `us-central1` (Iowa), **Standard edition**, Firestore
**Native** (ТЗ §18, п. 1). Региональное размещение до ~50% дешевле мульти-региона;
SLA 99.99% достаточно для MVP. Апгрейд на мульти-регион `nam5` — позже, при росте
требований к SLA (смена локации = новая БД + миграция).

> Регион выбирается **один раз** при создании БД и не меняется без миграции.

## Файлы

| Файл | Назначение |
| --- | --- |
| `firebase.json` | Конфиг эмуляторов (Auth/Firestore/Storage/UI) и таргетов |
| `firestore.rules` | Правила изоляции по `storeId` (каркас из ТЗ §4.3) |
| `storage.rules` | Запись только в `stores/{storeId}/**`, лимит 10 МБ |
| `firestore.indexes.json` | Заготовка композитных индексов (каталог, заказы) |

## Локальная разработка (эмуляторы)

```bash
npm install -g firebase-tools          # если не установлен
cd firebase
firebase emulators:start               # Auth :9099 · Firestore :8080 · Storage :9199 · UI :4000
```

Тесты правил доступа — через **Firebase Emulator Suite** (ТЗ §14): проверяются все
CRUD + правила изоляции (`isOwner`/`isStaff`, доступ покупателя к своим заказам).

## Деплой правил

```bash
firebase deploy --only firestore:rules,storage:rules --project staging
```

В CI правила деплоятся в составе пайплайна (ТЗ §15): `... → deploy staging → E2E → promote prod`.
