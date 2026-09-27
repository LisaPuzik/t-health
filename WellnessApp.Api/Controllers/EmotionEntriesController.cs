using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class EmotionEntriesController : ControllerBase
{
    private readonly AppDbContext _db;

    public EmotionEntriesController(AppDbContext db)
    {
        _db = db;
    }

    // Получить историю настроения пользователя
    [HttpGet("user/{userId:int}")]
    public async Task<IActionResult> GetUserEmotionEntries(int userId)
    {
        if (User.GetCurrentUserId() != userId)
            return Forbid();

        var userExists = await _db.Users.AnyAsync(u => u.Id == userId);

        if (!userExists)
            return NotFound(new { message = "User not found" });

        var entries = await _db.EmotionEntries
            .AsNoTracking()
            .Where(e => e.UserId == userId)
            .OrderByDescending(e => e.CreatedAt)
            .Select(e => new
            {
                e.Id,
                e.Mood,
                e.Note,
                e.CreatedAt
            })
            .ToListAsync();

        return Ok(entries);
    }

    // Добавить новое настроение
    [HttpPost]
    public async Task<IActionResult> CreateEmotionEntry(
        [FromBody] CreateEmotionEntryRequest request)
    {
        if (User.GetCurrentUserId() != request.UserId)
            return Forbid();

        var userExists = await _db.Users.AnyAsync(u => u.Id == request.UserId);

        if (!userExists)
            return NotFound(new { message = "User not found" });

        // MVP: настроение от 1 до 5
        if (request.Mood < 1 || request.Mood > 5)
            return BadRequest(new
            {
                message = "Настроение — от 1 до 5"
            });

        if (request.Note?.Length > 500)
            return BadRequest(new
            {
                message = "Заметка — максимум 500 символов"
            });

        var emotionEntry = new EmotionEntry
        {
            UserId = request.UserId,
            Mood = request.Mood,
            Note = string.IsNullOrWhiteSpace(request.Note)
                ? null
                : request.Note.Trim()
        };

        _db.EmotionEntries.Add(emotionEntry);

        // Настроение изменилось — AI-подборка пересчитается при следующем запросе
        _db.AiRecommendationCaches.RemoveRange(
            _db.AiRecommendationCaches.Where(c => c.UserId == request.UserId));

        await _db.SaveChangesAsync();

        return Ok(new
        {
            emotionEntry.Id,
            emotionEntry.UserId,
            emotionEntry.Mood,
            emotionEntry.Note,
            emotionEntry.CreatedAt
        });
    }
}

public class CreateEmotionEntryRequest
{
    public int UserId { get; set; }

    public int Mood { get; set; }

    public string? Note { get; set; }
}