# Wasat Shop — Android Multi-Store E-Commerce Platform

Мульти-тенантная SaaS-платформа: каждый Google-аккаунт разворачивает собственный
изолированный интернет-магазин за несколько минут. Реализация по **ТЗ v2.2**
(«Согласованные решения»).

> Статус: **каркас монорепо (kickoff).** Это фундамент под Фазу 1 «Фундамент»
> (нед. 1–3). Рабочий код компонентов добавляется в последующих коммитах —
> см. [`docs/roadmap.md`](docs/roadmap.md).

## Что это

| Параметр | Значение |
| --- | --- |
| Продукт | Мобильное приложение (Android) + серверная часть + веб-панель суперадмина |
| Платформа | Android: min API 28 · target API 36 (требование Google Play с авг. 2026) |
| Целевой рынок | Международный (мульти-валюта, мульти-язык) |
| Технический стек | Kotlin / Jetpack Compose · Material 3 Expressive · Firebase · Node.js/Express · Firestore |
| Аутентификация | Google Sign-In через Credential Manager (+ passkeys) · Firebase Auth + App Check |
| Идентификатор магазина | `storeId` (UUID) + публичный `slug`; `email` — атрибут владельца |
| Платежи | Stripe (Connect Express) + Google Pay; подписки SaaS — Stripe Billing |

## Структура монорепо

```
android/      Android-клиент (Kotlin/Compose, Clean Arch, Hilt, Room, Coil)
server/       Node.js/Express + TypeScript (Admin SDK, Stripe, Zod) — Cloud Run
admin-web/    React/TS SPA веб-панели суперадмина (не входит в Android-клиент)
firebase/     firestore.rules, storage.rules, индексы, конфиг эмуляторов
docs/         Архитектура, модель данных, контракты API, дорожная карта, решения
.github/      CI/CD пайплайн, CODEOWNERS, шаблон PR
```

Выбор **монорепо** обоснован: атомарные изменения общих контрактов между клиентом и
сервером (enum статусов заказа, модель данных, REST API), единый CI/CD и минимум
накладных расходов для MVP и небольшой команды.

## Архитектура (кратко)

Clean Architecture, три слоя. Сервер минимален и выполняет операции, недоступные
клиенту напрямую: транзакционный чекаут, платёжные вебхуки, рассылки, агрегаты,
биллинг. Подробно — [`docs/architecture.md`](docs/architecture.md).

```
Android App ──Firebase SDK──▶ Firebase (Auth / Firestore / Storage / FCM / App Check)
     │
     ├──REST + ID Token + App Check──▶ Express Server (Cloud Run) ──▶ Stripe / Algolia / SMTP / LLM
     │
     └──Stripe Android SDK──▶ Stripe (токенизация карты/кошелька)
```

## Документация

| Документ | Содержание |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | Слои, компоненты, протоколы взаимодействия (ТЗ §2) |
| [docs/data-model.md](docs/data-model.md) | Firestore-иерархия `stores/{storeId}/...` (ТЗ §8) |
| [docs/api-contract.md](docs/api-contract.md) | REST-эндпоинты и требования к Auth (ТЗ §9) |
| [docs/order-status.md](docs/order-status.md) | Канонический enum статусов заказа (ТЗ §FR-A04) |
| [docs/analytics-events.md](docs/analytics-events.md) | Продуктовые события GA4 (ТЗ §16) |
| [docs/security.md](docs/security.md) | App Check, Rules, секреты, GDPR (ТЗ §13) |
| [docs/decisions.md](docs/decisions.md) | Принятые решения с экономобоснованием (ТЗ §18) |
| [docs/roadmap.md](docs/roadmap.md) | 5 фаз разработки на 16 недель (ТЗ §17) |

## Сборка компонентов

Каждый каталог содержит собственный README с инструкциями по разворачиванию:
[`android/`](android/README.md), [`server/`](server/README.md),
[`admin-web/`](admin-web/README.md), [`firebase/`](firebase/README.md).
На этапе каркаса проекты ещё не инициализированы (нет Gradle/npm) — это первый шаг
Фазы 1.

## С чего начать (для команды)

1. Прочитать ТЗ и [`docs/roadmap.md`](docs/roadmap.md).
2. Сверить общие контракты: [`docs/order-status.md`](docs/order-status.md),
   [`docs/data-model.md`](docs/data-model.md), [`docs/api-contract.md`](docs/api-contract.md).
3. Поднять Firebase-эмуляторы по [`firebase/README.md`](firebase/README.md) и проверить
   [`firebase/firestore.rules`](firebase/firestore.rules).
4. Стартовать задачи Фазы 1 (нед. 1–3) из дорожной карты.
