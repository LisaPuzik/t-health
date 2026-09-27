# T-Health — корпоративное wellness-приложение (MVP, open-source)

Бэкенд + нативный Android-клиент.

| Часть | Папка | Стек |
|---|---|---|
| Бэкенд | [`backend/`](backend/) | ASP.NET Core 8, EF Core, PostgreSQL, JWT, Gemini AI |
| Android | [`android/`](android/) | Kotlin, Jetpack Compose, Retrofit, DataStore |

## Быстрый старт

1. Подними бэкенд (инструкция + секреты): [`backend/README.md`](backend/README.md).
2. Подними клиент (адрес бэка через `local.properties`): [`android/README.md`](android/README.md).
3. Тестовые аккаунты: `alice@company.com` / `bob@company.com`, пароль `Password123!`.

## Что умеет MVP

Регистрация/вход (JWT + мульти-девайс refresh), онбординг с интересами,
шаги с сенсора телефона в кольцо дня, тренировки, настроение, челленджи
(личные + шаги) с рейтингом отделов, сообщества, лента, достижения,
стрики, кристалл-тамагочи, AI-подборка (Gemini с фолбэком на правила).

Ограничения и допущения — в README частей.

## Лицензия

MIT — см. [LICENSE](LICENSE).
