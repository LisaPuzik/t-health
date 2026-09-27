# T-Health — Android (Kotlin + Jetpack Compose)

Нативный клиент к [WellnessApp.Api](../WellnessApp.Api): онбординг (Splash → Вход → Интересы → Приватность),
Главная с кольцом шагов, Челленджи, Сообщества, Лента, Профиль, тамагочи-кристалл, AI-подборка.

## Быстрый старт

Требования: [Android Studio](https://developer.android.com/studio) (JDK 17+ встроен),
запущенный [бэкенд](../WellnessApp.Api) (см. его README).

```bash
# 1. Бэк (в другом терминале):
dotnet run --project WellnessApp.Api --urls http://0.0.0.0:5059
```

2. Открой в Android Studio: File → Open → папка `THealth.Android`.
   Первый sync сам докачает Gradle и зависимости (5–15 минут).
3. Укажи адрес бэка **без правки кода** — создай файл `local.properties`
   в корне проекта (он в `.gitignore`, у каждого свой):
   ```properties
   apiBaseUrl=http://10.0.2.2:5059/
   ```
   - эмулятор → `http://10.0.2.2:5059/` (дефолт, можно не создавать файл);
   - физический телефон → LAN-IP компа из `ipconfig`, напр.
     `apiBaseUrl=http://192.168.1.50:5059/`
     (телефон и ПК в одном Wi-Fi; порт открыть:
     `New-NetFirewallRule -DisplayName "WellnessApi" -Direction Inbound -LocalPort 5059 -Protocol TCP -Action Allow`).
   - Если телефон по LAN-IP: допиши его же в
     `app/src/main/res/xml/network_security_config.xml` (там есть пример).
4. Запусти эмулятор (API 30+, лучше с Google Play) или подключи телефон → Run.

Тестовые аккаунты: `alice@company.com` / `bob@company.com`, пароль `Password123!`
(можно зарегистрировать новый прямо в приложении).

## Откуда берутся шаги

Без облаков: `Sensor.TYPE_STEP_COUNTER` телефона + штатное разрешение
`ACTIVITY_RECOGNITION` (системный диалог, показывается всегда).
Запрос — один раз при входе на Главную и тумблером на Приватности.
Кольцо показывает дельту счётчика от утренней базы; на бэк тихо уходят
часовые бакеты (`Source=StepSensor`, идемпотентные — дубли не копятся).
Пульс сенсором телефона не взять — в сводке будет прочерк.

## Что внутри

- `MainActivity.kt` — навигация + онбординг
- `ui/screens/` — `HomeTab` (кольцо, AI-подборка, рекомендации, коллеги),
  `ChallengesTab` (сегменты, рейтинг отделов), `CommunitiesTab`,
  `ProfileTab` (имя, интересы, история-календарь), `PetScreen` (кристалл),
  `FeedScreen` (лента)
- `ui/components/Ui.kt` — общие кнопки/карточки/кольцо
- `data/` — Retrofit-контракт бэка (`ApiClient`), DTO (`ApiModels`),
  токены + флаги (`AuthStore`, DataStore), сенсор (`StepSensor`),
  TTL-кэш запросов (`Cache`, 60 сек)
- 401 → бесшумный refresh; `deviceId` = ANDROID_ID (мульти-девайс на бэке)

## Ограничения

- Пульс — только с носимого устройства, в MVP его нет — в сводке прочерк.
- Чаты сообществ — заглушка («появятся в следующей версии»).
- T-ID SSO нет: кнопка «Войти через T-ID» — это email+пароль к API (подписано в UI).
- Цель шагов, XP-формулы кристалла и decay — см. README бэкенда.

## Лицензия

MIT — см. [LICENSE](LICENSE).
