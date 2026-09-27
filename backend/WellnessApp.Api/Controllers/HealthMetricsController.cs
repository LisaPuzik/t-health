using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;
using WellnessApp.Api.Services;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/health-metrics")]
[Authorize]
public class HealthMetricsController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly CrystalService _crystal;

    private static readonly HashSet<string> AllowedTypes = new(StringComparer.OrdinalIgnoreCase)
    {
        "Steps", "HeartRate", "Distance", "ActiveEnergy", "Weight", "SleepHours"
    };

    private static readonly HashSet<string> AllowedSources = new(StringComparer.OrdinalIgnoreCase)
    {
        "AppleHealthKit", "GoogleHealthConnect", "Strava", "Manual", "StepSensor"
    };

    public HealthMetricsController(AppDbContext db, CrystalService crystal)
    {
        _db = db;
        _crystal = crystal;
    }

    public class HealthMetricItem
    {
        public string Type { get; set; } = string.Empty;
        public double Value { get; set; }
        public string Unit { get; set; } = string.Empty;
        public DateTime RecordedAt { get; set; }
        public string Source { get; set; } = string.Empty;
        public string? ExternalId { get; set; }
    }

    public class BatchRequest
    {
        public List<HealthMetricItem> Metrics { get; set; } = [];
    }

    // Мобильный синк: пачка до 500 записей, идемпотентно по (Source, ExternalId)
    [HttpPost("batch")]
    public async Task<IActionResult> UploadBatch([FromBody] BatchRequest request)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        if (request.Metrics.Count == 0 || request.Metrics.Count > 500)
            return BadRequest(new { message = "Metrics count must be 1..500" });

        var now = DateTime.UtcNow;
        var toAdd = new List<HealthMetric>();
        int skipped = 0;

        // Собираем ExternalId из пачки для одного запроса дедупа
        var externalIds = request.Metrics
            .Where(m => !string.IsNullOrWhiteSpace(m.ExternalId) && !string.IsNullOrWhiteSpace(m.Source))
            .Select(m => m.Source.Trim() + "|" + m.ExternalId!.Trim())
            .Distinct()
            .ToList();

        var existing = new HashSet<string>();
        if (externalIds.Count > 0)
        {
            var sources = request.Metrics
                .Where(m => !string.IsNullOrWhiteSpace(m.ExternalId))
                .Select(m => m.Source.Trim())
                .Distinct()
                .ToList();
            var ids = request.Metrics
                .Where(m => !string.IsNullOrWhiteSpace(m.ExternalId))
                .Select(m => m.ExternalId!.Trim())
                .Distinct()
                .ToList();

            existing = (await _db.HealthMetrics
                .AsNoTracking()
                .Where(h => h.UserId == userId.Value && sources.Contains(h.Source) && h.ExternalId != null && ids.Contains(h.ExternalId))
                .Select(h => h.Source + "|" + h.ExternalId!)
                .ToListAsync())
                .ToHashSet();
        }

        foreach (var m in request.Metrics)
        {
            var err = Validate(m);
            if (err != null) return BadRequest(new { message = err });

            var source = m.Source.Trim();
            var externalId = string.IsNullOrWhiteSpace(m.ExternalId) ? null : m.ExternalId.Trim();

            if (externalId != null && existing.Contains(source + "|" + externalId))
            {
                skipped++;
                continue;
            }

            toAdd.Add(new HealthMetric
            {
                UserId = userId.Value,
                Type = m.Type.Trim(),
                Value = m.Value,
                Unit = m.Unit.Trim(),
                RecordedAt = m.RecordedAt.Kind == DateTimeKind.Unspecified
                    ? DateTime.SpecifyKind(m.RecordedAt, DateTimeKind.Utc)
                    : m.RecordedAt.ToUniversalTime(),
                Source = source,
                ExternalId = externalId,
                CreatedAt = now
            });
        }

        if (toAdd.Count > 0)
        {
            _db.HealthMetrics.AddRange(toAdd);
            // Активность изменилась — AI-подборка пересчитается при следующем запросе
            _db.AiRecommendationCaches.RemoveRange(
                _db.AiRecommendationCaches.Where(c => c.UserId == userId.Value));
            try
            {
                await _db.SaveChangesAsync();
            }
            catch (DbUpdateException)
            {
                // Гонка двух синков: часть уже вставили - пересчитываем тихо
                return Conflict(new { message = "Duplicate batch, retry with new ExternalId" });
            }
        }

        // Шаги -> прогресс челленджей с MetricType=Steps
        var stepsAdded = toAdd.Where(h => h.Type.Equals("Steps", StringComparison.OrdinalIgnoreCase)).Sum(h => (int)h.Value);
        var completedChallenges = new List<object>();
        object? crystal = null;
        if (stepsAdded > 0)
        {
            var participants = await _db.ChallengeParticipants
                .Include(cp => cp.Challenge)
                .Where(cp => cp.UserId == userId.Value && !cp.IsCompleted
                    && cp.Challenge.IsActive
                    && cp.Challenge.MetricType == "Steps"
                    && cp.Challenge.StartDate <= now && cp.Challenge.EndDate >= now)
                .ToListAsync();

            if (participants.Count > 0)
            {
                var user = await _db.Users.FirstAsync(u => u.Id == userId.Value);
                foreach (var p in participants)
                {
                    p.CurrentValue += stepsAdded;
                    if (p.CurrentValue >= p.Challenge.TargetValue)
                    {
                        p.CurrentValue = p.Challenge.TargetValue;
                        p.IsCompleted = true;
                        user.TotalPoints += p.Challenge.Points;
                        completedChallenges.Add(new { p.Challenge.Id, p.Challenge.Title, p.Challenge.Points });
                    }
                }
                await _db.SaveChangesAsync();
            }

            var pet = await _crystal.AwardAsync(userId.Value, CrystalService.XpForSteps(stepsAdded), CrystalService.EnergyForSteps(stepsAdded));
            crystal = new { pet.Level, pet.Xp, pet.Energy };
        }
        else if (toAdd.Count > 0)
        {
            // Нешаговые метрики тоже кормят кристалл минимально
            var pet = await _crystal.AwardAsync(userId.Value, toAdd.Count, 1);
            crystal = new { pet.Level, pet.Xp, pet.Energy };
        }

        return Ok(new { inserted = toAdd.Count, skipped, stepsAdded, completedChallenges, crystal });
    }

    [HttpGet]
    public async Task<IActionResult> List(
        [FromQuery] DateTime? from, [FromQuery] DateTime? to,
        [FromQuery] string? type, [FromQuery] int page = 1, [FromQuery] int pageSize = 50)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        page = Math.Clamp(page, 1, 100);
        pageSize = Math.Clamp(pageSize, 1, 200);

        var q = _db.HealthMetrics.AsNoTracking().Where(h => h.UserId == userId.Value);
        if (from.HasValue) q = q.Where(h => h.RecordedAt >= from.Value.ToUniversalTime());
        if (to.HasValue) q = q.Where(h => h.RecordedAt <= to.Value.ToUniversalTime());
        if (!string.IsNullOrWhiteSpace(type)) q = q.Where(h => h.Type == type.Trim());

        var total = await q.CountAsync();
        var items = await q.OrderByDescending(h => h.RecordedAt)
            .Skip((page - 1) * pageSize).Take(pageSize)
            .Select(h => new { h.Id, h.Type, h.Value, h.Unit, h.RecordedAt, h.Source, h.ExternalId })
            .ToListAsync();

        return Ok(new { total, page, pageSize, items });
    }

    // Агрегаты для графиков, огоньков и кристалла
    [HttpGet("summary")]
    public async Task<IActionResult> Summary([FromQuery] DateTime? from, [FromQuery] DateTime? to)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var toDt = (to ?? DateTime.UtcNow).ToUniversalTime();
        var fromDt = (from ?? toDt.AddDays(-7)).ToUniversalTime();

        var metrics = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId.Value && h.RecordedAt >= fromDt && h.RecordedAt <= toDt)
            .ToListAsync();

        double Total(string t) => metrics.Where(m => m.Type.Equals(t, StringComparison.OrdinalIgnoreCase)).Sum(m => m.Value);
        var hr = metrics.Where(m => m.Type.Equals("HeartRate", StringComparison.OrdinalIgnoreCase)).ToList();
        var workoutMinutes = await _db.Workouts.AsNoTracking()
            .Where(w => w.UserId == userId.Value && w.CreatedAt >= fromDt && w.CreatedAt <= toDt)
            .SumAsync(w => w.DurationMinutes);

        return Ok(new
        {
            from = fromDt,
            to = toDt,
            totalSteps = (int)Total("Steps"),
            totalDistance = Total("Distance"),
            totalActiveEnergy = Total("ActiveEnergy"),
            totalSleepHours = Total("SleepHours"),
            totalWorkoutMinutes = workoutMinutes,
            avgHeartRate = hr.Count == 0 ? (double?)null : Math.Round(hr.Average(m => m.Value), 1),
            records = metrics.Count
        });
    }

    // Стрик: сколько дней подряд закрыта цель. Без ?goal — личная цель юзера
    [HttpGet("streak")]
    public async Task<IActionResult> Streak([FromQuery] int? goal = null)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var userGoal = await _db.Users.AsNoTracking()
            .Where(u => u.Id == userId.Value)
            .Select(u => u.DailyStepGoal)
            .FirstOrDefaultAsync();
        goal = Math.Clamp(goal ?? userGoal, 100, 100000);

        var today = DateTime.UtcNow.Date;
        var from = today.AddDays(-400);
        var byDay = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId.Value
                && h.Type == "Steps"
                && h.RecordedAt >= from)
            .GroupBy(h => h.RecordedAt.Date)
            .Select(g => new { day = g.Key, total = g.Sum(h => h.Value) })
            .ToDictionaryAsync(x => x.day, x => (int)x.total);

        var todaySteps = byDay.TryGetValue(today, out var ts) ? ts : 0;
        var streak = 0;
        var day = todaySteps >= goal ? today : today.AddDays(-1);
        while (byDay.TryGetValue(day, out var total) && total >= goal)
        {
            streak++;
            day = day.AddDays(-1);
        }

        return Ok(new
        {
            goal,
            currentStreak = streak,
            todaySteps,
            goalReachedToday = todaySteps >= goal
        });
    }

    private static string? Validate(HealthMetricItem m)
    {
        if (!AllowedTypes.Contains(m.Type.Trim()))
            return $"Invalid type '{m.Type}'. Allowed: {string.Join(",", AllowedTypes)}";
        if (!AllowedSources.Contains(m.Source.Trim()))
            return $"Invalid source '{m.Source}'. Allowed: {string.Join(",", AllowedSources)}";
        if (string.IsNullOrWhiteSpace(m.Unit) || m.Unit.Length > 16)
            return "Unit is required (max 16)";
        if (m.RecordedAt > DateTime.UtcNow.AddMinutes(5))
            return "RecordedAt cannot be in the future";
        if (m.RecordedAt < DateTime.UtcNow.AddYears(-2))
            return "RecordedAt too old";
        if (!string.IsNullOrEmpty(m.ExternalId) && m.ExternalId.Length > 128)
            return "ExternalId max 128";

        var t = m.Type.Trim().ToLowerInvariant();
        return t switch
        {
            "steps" => m.Value is < 0 or > 100000 ? "Steps must be 0..100000 per record" : null,
            "heartrate" => m.Value is < 20 or > 250 ? "HeartRate must be 20..250" : null,
            "weight" => m.Value is < 20 or > 300 ? "Weight must be 20..300" : null,
            "sleephours" => m.Value is < 0 or > 24 ? "SleepHours must be 0..24" : null,
            "distance" => m.Value is < 0 or > 500 ? "Distance must be 0..500" : null,
            "activeenergy" => m.Value is < 0 or > 10000 ? "ActiveEnergy must be 0..10000" : null,
            _ => null
        };
    }
}
