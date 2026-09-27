using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class UsersController : ControllerBase
{
    private readonly AppDbContext _db;

    public UsersController(AppDbContext db)
    {
        _db = db;
    }

    [HttpGet]
    public async Task<IActionResult> GetUsers()
    {
        var users = await _db.Users
            .AsNoTracking()
            .Select(u => new
            {
                u.Id,
                u.Email,
                u.FirstName,
                u.LastName,
                u.TotalPoints,
                u.IsActive,
                Team = u.Team == null
                    ? null
                    : u.Team.Name
            })
            .ToListAsync();

        return Ok(users);
    }

    [HttpGet("{id:int}")]
    public async Task<IActionResult> GetUser(int id)
    {
        var user = await _db.Users
            .AsNoTracking()
            .Where(u => u.Id == id)
            .Select(u => new
            {
                u.Id,
                u.Email,
                u.FirstName,
                u.LastName,
                u.TotalPoints,
                u.IsActive,
                Team = u.Team == null
                    ? null
                    : u.Team.Name
            })
            .FirstOrDefaultAsync();

        if (user == null)
            return NotFound(new { message = "User not found" });

        return Ok(user);
    }

    public class UpdateMeRequest
    {
        public string? FirstName { get; set; }
        public string? LastName { get; set; }
        public int? DailyStepGoal { get; set; }
    }

    // Смена имени/фамилии себе
    [HttpPut("me")]
    public async Task<IActionResult> UpdateMe([FromBody] UpdateMeRequest req)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        if (req.FirstName != null && (string.IsNullOrWhiteSpace(req.FirstName) || req.FirstName.Trim().Length > 100))
            return BadRequest(new { message = "Имя — 1..100 символов" });
        if (req.LastName != null && (string.IsNullOrWhiteSpace(req.LastName) || req.LastName.Trim().Length > 100))
            return BadRequest(new { message = "Фамилия — 1..100 символов" });
        if (req.DailyStepGoal.HasValue && (req.DailyStepGoal < 1000 || req.DailyStepGoal > 50000))
            return BadRequest(new { message = "Цель — от 1000 до 50000 шагов" });

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Id == userId.Value);
        if (user == null) return NotFound(new { message = "User not found" });

        if (req.FirstName != null) user.FirstName = req.FirstName.Trim();
        if (req.LastName != null) user.LastName = req.LastName.Trim();
        if (req.DailyStepGoal.HasValue) user.DailyStepGoal = req.DailyStepGoal.Value;
        await _db.SaveChangesAsync();

        return Ok(new { user.Id, user.Email, user.FirstName, user.LastName, user.DailyStepGoal });
    }
}