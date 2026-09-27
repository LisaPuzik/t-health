using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.AI;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;

namespace WellnessApp.Api.Controllers;

// Персональные рекомендации: настроение + интересы + подписки
[ApiController]
[Route("api/recommendations")]
[Authorize]
public class RecommendationsController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly AiRecommendationService _ai;
    public RecommendationsController(AppDbContext db, AiRecommendationService ai) { _db = db; _ai = ai; }

    [HttpGet]
    public async Task<IActionResult> Get()
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var mood = await _db.EmotionEntries.AsNoTracking()
            .Where(e => e.UserId == userId.Value)
            .OrderByDescending(e => e.CreatedAt)
            .Select(e => e.Mood)
            .FirstOrDefaultAsync();

        var myInterests = await _db.UserInterests.AsNoTracking()
            .Where(u => u.UserId == userId.Value).Select(u => u.InterestId).ToListAsync();
        var myCommunities = await _db.CommunityMembers.AsNoTracking()
            .Where(m => m.UserId == userId.Value).Select(m => m.CommunityId).ToListAsync();

        var contents = await _db.WellnessContents.AsNoTracking()
            .Select(w => new { w.Id, w.Title, w.Description, w.ContentType, w.MinMood, w.MaxMood, w.InterestId,
                Interest = w.Interest == null ? null : w.Interest.Name,
                w.CommunityId, Community = w.Community == null ? null : w.Community.Name })
            .ToListAsync();

        var scored = contents.Select(w =>
        {
            int score = 0;
            string reason = "popular";
            if (mood != 0 && mood >= w.MinMood && mood <= w.MaxMood) { score += 3; reason = $"mood:{mood}"; }
            if (w.InterestId.HasValue && myInterests.Contains(w.InterestId.Value)) { score += 2; reason += "+interest"; }
            if (w.CommunityId.HasValue && myCommunities.Contains(w.CommunityId.Value)) { score += 2; reason += "+community"; }
            return new { w, score, reason };
        })
        .OrderByDescending(x => x.score)
        .ThenBy(x => x.w.Id)
        .Take(10)
        .Select(x => new { x.w.Id, x.w.Title, x.w.Description, x.w.ContentType, x.w.Interest, x.w.Community, x.score, x.reason });

        return Ok(new { mood = mood == 0 ? (int?)null : mood, items = scored });
    }

    // AI-подборка (Gemini): кэш 12ч, без ключа/при ошибке — те же правила
    [HttpGet("ai")]
    public async Task<IActionResult> Ai(CancellationToken ct)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var result = await _ai.GetAsync(userId.Value, ct);
        return Ok(new
        {
            source = result.Source,
            result.Mood,
            items = result.Items.Select(i => new
            {
                i.Title,
                i.Description,
                i.ContentType,
                i.Reason
            })
        });
    }
}
