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
public class ChallengesController : ControllerBase
{
    private readonly AppDbContext _db;

    public ChallengesController(AppDbContext db)
    {
        _db = db;
    }

    // Все активные челленджи
    [HttpGet]
    public async Task<IActionResult> GetChallenges()
    {
        var userId = User.GetCurrentUserId();
        var challenges = await _db.Challenges
            .AsNoTracking()
            .Where(c => c.IsActive)
            .OrderBy(c => c.EndDate)
            .Select(c => new
            {
                c.Id,
                c.Title,
                c.Description,
                c.MetricType,
                c.TargetValue,
                c.Points,
                c.StartDate,
                c.EndDate,
                c.IsActive,
                participants = _db.ChallengeParticipants.Count(cp => cp.ChallengeId == c.Id),
                joined = userId != null && _db.ChallengeParticipants.Any(cp => cp.ChallengeId == c.Id && cp.UserId == userId.Value)
            })
            .ToListAsync();

        return Ok(challenges);
    }

    // Челленджи конкретного пользователя
    [HttpGet("user/{userId:int}")]
    public async Task<IActionResult> GetUserChallenges(int userId)
    {
        if (User.GetCurrentUserId() != userId)
            return Forbid();

        var userExists = await _db.Users.AnyAsync(u => u.Id == userId);

        if (!userExists)
            return NotFound(new { message = "User not found" });

        var challenges = await _db.ChallengeParticipants
            .AsNoTracking()
            .Where(cp => cp.UserId == userId)
            .Select(cp => new
            {
                cp.ChallengeId,
                Title = cp.Challenge.Title,
                cp.Challenge.Description,
                cp.Challenge.MetricType,
                TargetValue = cp.Challenge.TargetValue,
                Points = cp.Challenge.Points,
                cp.CurrentValue,
                cp.IsCompleted,
                cp.JoinedAt,
                cp.Challenge.StartDate,
                cp.Challenge.EndDate
            })
            .OrderBy(c => c.EndDate)
            .ToListAsync();

        return Ok(challenges);
    }

    // Вступить в челлендж
    [HttpPost("{challengeId:int}/join")]
    public async Task<IActionResult> JoinChallenge(
        int challengeId,
        [FromBody] JoinChallengeRequest request)
    {
        if (User.GetCurrentUserId() != request.UserId)
            return Forbid();

        var userExists = await _db.Users.AnyAsync(u => u.Id == request.UserId);

        if (!userExists)
            return NotFound(new { message = "User not found" });

        var challenge = await _db.Challenges
            .FirstOrDefaultAsync(c => c.Id == challengeId);

        if (challenge == null)
            return NotFound(new { message = "Challenge not found" });

        if (!challenge.IsActive)
            return BadRequest(new { message = "Challenge is not active" });

        var alreadyJoined = await _db.ChallengeParticipants
            .AnyAsync(cp =>
                cp.ChallengeId == challengeId &&
                cp.UserId == request.UserId);

        if (alreadyJoined)
            return BadRequest(new { message = "User already joined this challenge" });

        var participant = new ChallengeParticipant
        {
            ChallengeId = challengeId,
            UserId = request.UserId,
            CurrentValue = 0,
            IsCompleted = false
        };

        _db.ChallengeParticipants.Add(participant);

        await _db.SaveChangesAsync();

        return Ok(new
        {
            message = "Successfully joined challenge",
            participant.Id,
            participant.ChallengeId,
            participant.UserId,
            participant.CurrentValue,
            participant.IsCompleted,
            participant.JoinedAt
        });
    }
}

public class JoinChallengeRequest
{
    public int UserId { get; set; }
}