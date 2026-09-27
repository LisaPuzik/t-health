using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;
using WellnessApp.Api.Services;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class WorkoutsController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly CrystalService _crystal;

    public WorkoutsController(AppDbContext db, CrystalService crystal)
    {
        _db = db;
        _crystal = crystal;
    }

    // Получить тренировки пользователя
    [HttpGet("user/{userId:int}")]
    public async Task<IActionResult> GetUserWorkouts(int userId)
    {
        if (User.GetCurrentUserId() != userId)
            return Forbid();

        var userExists = await _db.Users.AnyAsync(u => u.Id == userId);

        if (!userExists)
            return NotFound(new { message = "User not found" });

        var workouts = await _db.Workouts
            .AsNoTracking()
            .Where(w => w.UserId == userId)
            .OrderByDescending(w => w.CreatedAt)
            .Select(w => new
            {
                w.Id,
                w.Type,
                w.DurationMinutes,
                w.Points,
                w.CreatedAt
            })
            .ToListAsync();

        return Ok(workouts);
    }

    // Добавить тренировку
    [HttpPost]
    public async Task<IActionResult> CreateWorkout(
     [FromBody] CreateWorkoutRequest request)
    {
        if (User.GetCurrentUserId() != request.UserId)
            return Forbid();

        var user = await _db.Users
            .FirstOrDefaultAsync(u => u.Id == request.UserId);

        if (user == null)
            return NotFound(new { message = "User not found" });

        if (request.DurationMinutes <= 0)
            return BadRequest(new
            {
                message = "Duration must be greater than 0"
            });

        if (string.IsNullOrWhiteSpace(request.Type))
            return BadRequest(new
            {
                message = "Workout type is required"
            });

        var now = DateTime.UtcNow;

        // 1 минута = 1 point
        var workoutPoints = request.DurationMinutes;

        var workout = new Workout
        {
            UserId = request.UserId,
            Type = request.Type.Trim(),
            DurationMinutes = request.DurationMinutes,
            Points = workoutPoints,
            CreatedAt = now
        };

        _db.Workouts.Add(workout);

        // Начисляем очки за саму тренировку
        user.TotalPoints += workoutPoints;

        // Находим активные челленджи пользователя
        var participants = await _db.ChallengeParticipants
            .Include(cp => cp.Challenge)
            .Where(cp =>
                cp.UserId == request.UserId &&
                !cp.IsCompleted &&
                cp.Challenge.IsActive &&
                cp.Challenge.StartDate <= now &&
                cp.Challenge.EndDate >= now)
            .ToListAsync();

        var completedChallenges = new List<object>();

        foreach (var participant in participants)
        {
            var challenge = participant.Challenge;

            switch (challenge.MetricType)
            {
                case "WorkoutMinutes":
                    participant.CurrentValue += request.DurationMinutes;
                    break;

                case "WorkoutCount":
                    participant.CurrentValue += 1;
                    break;
            }

            // Проверяем выполнение
            if (participant.CurrentValue >= challenge.TargetValue)
            {
                participant.CurrentValue = challenge.TargetValue;
                participant.IsCompleted = true;

                user.TotalPoints += challenge.Points;

                completedChallenges.Add(new
                {
                    challenge.Id,
                    challenge.Title,
                    challenge.Points
                });
            }
        }

        await _db.SaveChangesAsync();

        // Активность изменилась — AI-подборка пересчитается при следующем запросе
        _db.AiRecommendationCaches.RemoveRange(
            _db.AiRecommendationCaches.Where(c => c.UserId == request.UserId));

        var pet = await _crystal.AwardAsync(request.UserId, CrystalService.XpForWorkout(request.DurationMinutes), CrystalService.EnergyPerWorkout);

        return Ok(new
        {
            workout = new
            {
                workout.Id,
                workout.Type,
                workout.DurationMinutes,
                workout.Points,
                workout.CreatedAt
            },

            userPoints = user.TotalPoints,
            crystal = new { pet.Level, pet.Xp, pet.Energy },

            challenges = participants.Select(p => new
            {
                p.ChallengeId,
                p.Challenge.Title,
                p.CurrentValue,
                p.Challenge.TargetValue,
                p.IsCompleted
            }),

            completedChallenges
        });
    }
}

public class CreateWorkoutRequest
{
    public int UserId { get; set; }

    public string Type { get; set; } = string.Empty;

    public int DurationMinutes { get; set; }
}