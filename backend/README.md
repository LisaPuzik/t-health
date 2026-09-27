# WellnessApp.Api — бэкенд T-Health

ASP.NET Core 8 + EF Core + PostgreSQL. Аутентификация JWT (access + мульти-девайс refresh),
тренировки, метрики здоровья, челленджи (личные + шаги), сообщества, лента, достижения,
кристалл-тамагочи, стрики, AI-рекомендации (Google Gemini с фолбэком на правила).

Мобильный клиент: [android](../android) (Kotlin + Jetpack Compose).

## Быстрый старт

Требования: [.NET 8 SDK](https://dotnet.microsoft.com/download), [PostgreSQL 16](https://www.postgresql.org/download/) (или Docker).

```bash
# 1. База (вариант А — Docker)
docker compose up -d
# вариант Б — свой Postgres: создай БД wellness

# 2. Секреты (в репозитории их нет — только плейсхолдеры)
cd WellnessApp.Api
dotnet user-secrets init
dotnet user-secrets set "ConnectionStrings:DefaultConnection" "Host=localhost;Port=5432;Database=wellness;Username=wellness;Password=твой_пароль"
dotnet user-secrets set "Jwt:Key" "случайная_строка_минимум_32_символа_1234567890"
# Gemini (необязательно — без ключа работает фолбэк на правила):
dotnet user-secrets set "Gemini:ApiKey" "AIza..."

# 3. Запуск (миграции + сиды применяются сами)
dotnet run --urls http://0.0.0.0:5059
```

Swagger: `http://localhost:5059/swagger`. Проверка БД: `GET /api/health/db`.

То же через переменные окружения (например, в проде):
`ConnectionStrings__DefaultConnection`, `Jwt__Key`, `GEMINI_API_KEY`.

## Тестовые аккаунты (сиды)

| Email | Пароль |
|---|---|
| `alice@company.com` | `Password123!` |
| `bob@company.com` | `Password123!` |
| `charlie@company.com` | `Password123!` |

## Ключевой сценарий (можно повторить в Swagger)

1. `POST /api/auth/register` → `{accessToken, ...}`
2. `PUT /api/interests/me` → `{interestIds: [...]}`
3. `POST /api/health-metrics/batch` (пример ниже) → шаги засчитаны
4. `GET /api/health-metrics/summary`, `GET /api/health-metrics/streak`
5. `GET /api/challenges` → `POST /api/challenges/{id}/join`
6. `GET /api/crystal/pet`, `GET /api/recommendations/ai` (`source: ai|rules`)

Пример батча метрик:

```json
{
  "metrics": [
    { "type": "Steps", "value": 5000, "unit": "count",
      "recordedAt": "2026-09-27T10:00:00Z",
      "source": "Manual", "externalId": "demo-steps-1" }
  ]
}
```

Типы: `Steps, HeartRate, Distance, ActiveEnergy, Weight, SleepHours`.
Источники: `Manual, StepSensor, AppleHealthKit, GoogleHealthConnect, Strava`.
Повторная отправка с тем же `ExternalId` не дублируется (идемпотентность).

## Допущения и ограничения

- Цель шагов по умолчанию — 10000/день (меняется `PUT /api/users/me` → `dailyStepGoal`, 1000–50000); стрик считается по личной цели.
- XP кристалла: 1000 шагов = 10 XP, 1 мин тренировки = 2 XP, уровень = каждые 300 XP; энергия −10/сутки без активности.
- AI-подборка кэшируется 12 часов, сбрасывается при новом настроении/тренировке/синке; без `Gemini:ApiKey` отдаются правила (`source: rules`).
- Пульс с телефона без носимого устройства взять нельзя — в сводке будет прочерк.
- Проект — MVP: нет ролей/HR-панели, пушей, чатов (сообщества без сообщений), веб-клиента.

## Структура

```
WellnessApp.Api/
  Controllers/   # Auth, Users, Workouts, HealthMetrics, Challenges, Teams,
                 # Communities(+Follows), Posts(feed), Interests, Achievements,
                 # Emotions, Crystal, Recommendations(+AI)
  Models/        # сущности EF Core
  Data/          # AppDbContext, DbInitializer (сиды + backfill)
  Auth/          # JWT: настройки, токены, DTO
  AI/            # Gemini: настройки, AI-сервис
  Services/      # CrystalService
  Migrations/    # EF Core миграции
```

## Лицензия

MIT — см. [LICENSE](LICENSE).
