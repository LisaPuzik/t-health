using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Services;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/crystal")]
[Authorize]
public class CrystalController : ControllerBase
{
    private readonly CrystalService _crystal;
    private readonly AppDbContext _db;

    // Стадии роста по уровню (single source для приложений)
    private static readonly (int Level, string Name, string Icon)[] Stages =
    [
        (1, "Пещера", "💎"),
        (2, "Горы", "💎"),
        (3, "Сияние", "💠"),
        (4, "Космос", "🔷"),
        (5, "Звёзды", "🔮"),
        (6, "Галактика", "✨"),
        (7, "Легенда", "🌟"),
    ];

    public CrystalController(CrystalService crystal, AppDbContext db)
    {
        _crystal = crystal;
        _db = db;
    }

    [HttpGet("me")]
    public async Task<IActionResult> Me()
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var pet = await _crystal.GetOrCreateAsync(userId.Value);
        return Ok(new
        {
            pet.Level,
            pet.Xp,
            pet.Energy,
            xpForNextLevel = CrystalService.XpPerLevel - (pet.Xp % CrystalService.XpPerLevel),
            pet.LastFedAt,
            pet.UpdatedAt
        });
    }

    // Полный экран тамагочи: уровень, стадии, статистика, майлстоуны шагов
    [HttpGet("pet")]
    public async Task<IActionResult> Pet()
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var pet = await _crystal.GetOrCreateAsync(userId.Value);
        var now = DateTime.UtcNow;
        var monthAgo = now.AddDays(-30);

        var steps30 = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId.Value && h.Type == "Steps" && h.RecordedAt >= monthAgo)
            .SumAsync(h => h.Value);
        var lifetimeSteps = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId.Value && h.Type == "Steps")
            .SumAsync(h => h.Value);
        var workouts30 = await _db.Workouts.AsNoTracking()
            .CountAsync(w => w.UserId == userId.Value && w.CreatedAt >= monthAgo);

        var today = now.Date;
        var stepsByDay = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId.Value && h.Type == "Steps" && h.RecordedAt >= today.AddDays(-400))
            .GroupBy(h => h.RecordedAt.Date)
            .Select(g => new { day = g.Key, total = g.Sum(h => h.Value) })
            .ToDictionaryAsync(x => x.day, x => (int)x.total);

        const int goal = 10000;
        var streak = 0;
        var day = stepsByDay.TryGetValue(today, out var ts) && ts >= goal ? today : today.AddDays(-1);
        while (stepsByDay.TryGetValue(day, out var total) && total >= goal)
        {
            streak++;
            day = day.AddDays(-1);
        }

        var stageIdx = Math.Clamp(pet.Level, 1, Stages.Length) - 1;
        var inLevel = pet.Xp % CrystalService.XpPerLevel;
        var pct = inLevel * 100 / CrystalService.XpPerLevel;
        var remaining = CrystalService.XpPerLevel - inLevel;
        var workoutsToGo = Math.Max(1, (int)Math.Ceiling(remaining / 60.0));

        var challenge = await _db.ChallengeParticipants.AsNoTracking()
            .Where(cp => cp.UserId == userId.Value && !cp.IsCompleted && cp.Challenge.IsActive)
            .OrderBy(cp => cp.Challenge.EndDate)
            .Select(cp => new { cp.Challenge.Title, cp.CurrentValue, Target = cp.Challenge.TargetValue })
            .FirstOrDefaultAsync();

        var milestones = new List<object>();
        foreach (var m in new[] { 10000, 50000, 100000 })
        {
            var done = (int)lifetimeSteps >= m;
            milestones.Add(new
            {
                title = $"{m / 1000} 000 шагов",
                done,
                detail = done ? "Открыто" : $"Осталось {m - (int)lifetimeSteps}"
            });
        }
        if (challenge != null)
            milestones.Add(new
            {
                title = challenge.Title,
                done = false,
                detail = $"{challenge.CurrentValue} из {challenge.Target}"
            });

        return Ok(new
        {
            pet.Level,
            pet.Xp,
            pet.Energy,
            xpForNextLevel = remaining,
            progressPct = pct,
            workoutsToGo,
            stage = new
            {
                index = stageIdx + 1,
                Stages[stageIdx].Name,
                Stages[stageIdx].Icon,
                stages = Stages.Select((s, i) => new
                {
                    level = s.Level,
                    s.Name,
                    s.Icon,
                    state = i < stageIdx ? "done" : i == stageIdx ? "current" : "locked"
                })
            },
            stats = new
            {
                streakDays = streak,
                workouts30,
                steps30 = (int)steps30,
                lifetimeSteps = (int)lifetimeSteps
            },
            milestones,
            pet.LastFedAt
        });
    }
}
