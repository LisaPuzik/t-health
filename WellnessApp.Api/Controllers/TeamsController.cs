using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/teams")]
[Authorize]
public class TeamsController : ControllerBase
{
    private readonly AppDbContext _db;
    public TeamsController(AppDbContext db) { _db = db; }

    [HttpGet]
    public async Task<IActionResult> List()
    {
        var items = await _db.Teams.AsNoTracking()
            .Select(t => new { t.Id, t.Name, members = t.Users.Count })
            .OrderBy(t => t.Name).ToListAsync();
        return Ok(items);
    }

    // Рейтинг отделов по сумме очков (для карточки на экране челленджей)
    [HttpGet("leaderboard")]
    public async Task<IActionResult> Leaderboard()
    {
        var items = await _db.Teams.AsNoTracking()
            .Select(t => new
            {
                t.Id,
                t.Name,
                members = t.Users.Count,
                totalPoints = t.Users.Sum(u => u.TotalPoints)
            })
            .OrderByDescending(t => t.totalPoints)
            .ToListAsync();
        return Ok(items);
    }
}

[ApiController]
[Route("api/achievements")]
[Authorize]
public class AchievementsController : ControllerBase
{
    private readonly AppDbContext _db;
    public AchievementsController(AppDbContext db) { _db = db; }

    [HttpGet]
    public async Task<IActionResult> All()
    {
        return Ok(await _db.Achievements.AsNoTracking()
            .Select(a => new { a.Id, a.Name, a.Description, a.Points })
            .ToListAsync());
    }

    [HttpGet("me")]
    public async Task<IActionResult> Mine()
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();
        var items = await _db.UserAchievements.AsNoTracking()
            .Where(ua => ua.UserId == userId.Value)
            .Select(ua => new
            {
                ua.AchievementId,
                ua.Achievement.Name,
                ua.Achievement.Description,
                ua.Achievement.Points,
                ua.EarnedAt
            })
            .OrderByDescending(x => x.EarnedAt)
            .ToListAsync();
        return Ok(new { count = items.Count, items });
    }
}
