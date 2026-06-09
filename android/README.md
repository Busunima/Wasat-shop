# Android-клиент

Kotlin + Jetpack Compose, **Clean Architecture** (MVVM + Repository). Двойной режим:
«Витрина» (покупатель) и «Кабинет» (администратор), переключение через нижнее меню;
режим определяется Firebase Custom Claims.

> Статус: **каркас.** Gradle-проект ещё не инициализирован — это первый шаг Фазы 1.

## Стек

| Область | Технология |
| --- | --- |
| UI | Jetpack Compose · **Material 3 Expressive** · DynamicColor |
| Архитектура | MVVM + Repository · UseCases · Clean Architecture (3 слоя) |
| DI | Hilt |
| Локальное хранилище | Room (offline: каталог, корзина) |
| Изображения | Coil (кэш + миниатюры) |
| Backend SDK | Firebase (Auth, Firestore, Storage, FCM, App Check) |
| Платежи | Stripe Android SDK (PaymentSheet) + Google Pay |
| Навигация | navigation-compose ≥ 2.8 (предиктивный «назад») |
| Аналитика | Firebase Analytics (GA4), Crashlytics, Performance |

## Платформенные требования (ТЗ §12)

- `minSdk = 28`, `targetSdk = 36` (обязательно для Google Play с авг. 2026).
- **Edge-to-edge** обязателен (на API 36 opt-out игнорируется).
- **Предиктивный «назад»** обязателен (на Android 16 `onBackPressed`/`KEYCODE_BACK`
  не вызываются).
- Адаптивность крупных экранов (`sw ≥ 600dp`): `WindowSizeClass`, list-detail,
  navigation rail.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64`; поддержка страниц памяти 16 KB при
  нативном коде.

## Предполагаемая структура модулей

```
app/                         — точка входа, навигация, DI-граф
core/
  designsystem/              — M3 Expressive: токены, тип-шкала, формы, движение, тема
  ui/                        — общие Compose-компоненты (единый источник стиля)
  data/                      — Firebase/REST/Room источники, репозитории
  domain/                    — модели, UseCases, OrderStatus (см. docs/order-status.md)
  common/                    — утилиты, Result, ошибки
feature/
  storefront/                — Splash, Home, Catalog, Product, Cart, Checkout, Orders, Profile
  admin/                     — Dashboard, Products, Orders, Returns, Analytics, Promocodes, Settings, Staff
  auth/                      — Google Sign-In (Credential Manager) + онбординг магазина
```

## Дизайн-система (ТЗ §11)

Контент-форвард минимализм: иерархия за счёт типографики, размера и пространства;
единый акцент (брендовый цвет магазина) только для действий. Светлая и тёмная темы
обязательны, контраст ≥ WCAG AA. Общая Compose-библиотека компонентов предотвращает
рассинхрон UI.

## Запуск (после инициализации)

```bash
# из корня монорепо
cd android
./gradlew assembleDebug          # сборка
./gradlew testDebugUnitTest      # JUnit 5 + MockK
./gradlew connectedCheck         # Compose UI / Espresso
```

`google-services.json` (dev) кладётся в `app/` локально и **не коммитится** (см.
корневой `.gitignore`).
