# Архитектура системы (ТЗ §2)

Clean Architecture, три слоя. Сервер минимален и выполняет операции, недоступные
клиенту напрямую: платёжные вебхуки, рассылки, агрегаты, транзакционный чекаут,
биллинг.

## Слои

| Слой | Состав |
| --- | --- |
| 1 · Mobile App (Android) | Kotlin + Jetpack Compose · MVVM + Repository · Room (offline) · Hilt DI · Coil · Firebase SDK · App Check · Stripe Android SDK |
| 2 · Server (Node.js) | Express.js + TypeScript · Firebase Admin SDK · Stripe SDK · Nodemailer · Zod · Cloud Run / Functions · Memorystore (rate limit) |
| 3 · Backend Services | Firebase Auth (Google) · Firestore · Storage · FCM · App Check · Algolia (поиск) · Stripe (Connect + Billing + Tax) · Google Pay · сервис рекомендаций · LLM API (AI-ассист контента) |
| Веб-панель суперадмина | Отдельное SPA (React/TypeScript), не входит в Android-клиент |

## Схема взаимодействия компонентов

| Компонент | Взаимодействует с | Протокол |
| --- | --- | --- |
| Android App | Firebase Auth / App Check | Firebase SDK (HTTPS) |
| Android App | Firestore | Firebase SDK (WebSocket/HTTPS) |
| Android App | Firebase Storage | Firebase SDK (HTTPS) |
| Android App | Express Server | REST / HTTPS + Firebase ID Token + App Check |
| Android App | Stripe (токенизация карты/кошелька) | Stripe SDK (HTTPS) |
| Android App | FCM | Firebase SDK (Push) |
| Express Server | Firestore (Admin) | Firebase Admin SDK |
| Express Server | Stripe API + Webhooks | HTTPS API + Webhook (подпись Stripe) |
| Express Server | Algolia | HTTPS (индексация / запросы) |
| Express Server | SMTP | Nodemailer / SMTP |

## Поисковый сервис

Полнотекстовый и фасетный поиск (название, теги, фильтры, сортировка) реализуется
через **Algolia** — глобальную распределённую поисковую сеть с низкой задержкой,
что важно для международного рынка. Firestore-триггеры (Cloud Functions)
синхронизируют индекс при создании/изменении товара. Бюджетная альтернатива —
self-hosted Typesense с региональными репликами (см. [`decisions.md`](decisions.md)).

## Принципы

- **Мульти-тенантность:** один экземпляр приложения обслуживает множество
  независимых магазинов; изоляция данных — на уровне Firestore Rules по `storeId`,
  защита от злоупотреблений — App Check.
- **Связь владелец → магазины:** в MVP — один магазин на Google-аккаунт; архитектура
  (`storeId`-UUID + `ownerUid`) допускает несколько магазинов и членство сотрудником
  в чужих без переделки в будущем; роль определяется членством.
- **Двойной режим:** «Витрина» (покупатель) и «Кабинет» (администратор);
  переключение через нижнее меню; режим определяется Firebase Custom Claims.
