using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly ITokenService _tokens;
    private readonly JwtSettings _settings;

    public AuthController(AppDbContext db, ITokenService tokens, IOptions<JwtSettings> options)
    {
        _db = db;
        _tokens = tokens;
        _settings = options.Value;
    }

    [HttpPost("register")]
    [AllowAnonymous]
    public async Task<IActionResult> Register([FromBody] RegisterRequest request)
    {
        var email = request.Email.Trim().ToLowerInvariant();

        if (await _db.Users.AnyAsync(u => u.Email.ToLower() == email))
            return Conflict(new { message = "Эта почта уже зарегистрирована" });

        if (request.TeamId.HasValue &&
            !await _db.Teams.AnyAsync(t => t.Id == request.TeamId.Value))
            return BadRequest(new { message = "Команда не найдена" });

        var user = new User
        {
            Email = email,
            FirstName = request.FirstName.Trim(),
            LastName = request.LastName.Trim(),
            TeamId = request.TeamId,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password)
        };

        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        var deviceId = NormalizeDeviceId(request.DeviceId);
        var refresh = await IssueRefreshTokenAsync(user.Id, deviceId);

        return Ok(BuildResponse(user, refresh));
    }

    [HttpPost("login")]
    [AllowAnonymous]
    public async Task<IActionResult> Login([FromBody] LoginRequest request)
    {
        var email = request.Email.Trim().ToLowerInvariant();
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email.ToLower() == email);

        if (user == null || string.IsNullOrEmpty(user.PasswordHash) ||
            !BCrypt.Net.BCrypt.Verify(request.Password, user.PasswordHash))
            return Unauthorized(new { message = "Неверная почта или пароль" });

        if (!user.IsActive)
            return Forbid();

        var deviceId = NormalizeDeviceId(request.DeviceId);
        var refresh = await IssueRefreshTokenAsync(user.Id, deviceId);

        return Ok(BuildResponse(user, refresh));
    }

    [HttpPost("refresh")]
    [AllowAnonymous]
    public async Task<IActionResult> Refresh([FromBody] RefreshRequest request)
    {
        var stored = await _db.RefreshTokens
            .Include(r => r.User)
            .FirstOrDefaultAsync(r => r.Token == request.RefreshToken && r.RevokedAt == null);

        if (stored == null || stored.ExpiresAt <= DateTime.UtcNow || !stored.User.IsActive)
            return Unauthorized(new { message = "Сессия истекла — войди заново" });

        // Ротация: старый отзываем, новый выдаем на тот же DeviceId
        stored.RevokedAt = DateTime.UtcNow;
        var refresh = await IssueRefreshTokenAsync(stored.UserId, stored.DeviceId);
        await _db.SaveChangesAsync();

        return Ok(BuildResponse(stored.User, refresh));
    }

    [HttpPost("logout")]
    [Authorize]
    public async Task<IActionResult> Logout([FromBody] RefreshRequest? request = null)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        if (request?.RefreshToken != null)
        {
            var stored = await _db.RefreshTokens
                .FirstOrDefaultAsync(r => r.Token == request.RefreshToken && r.UserId == userId.Value);
            if (stored != null) stored.RevokedAt = DateTime.UtcNow;
        }
        else
        {
            // Без токена в body - отзываем все токены текущего устройства? Нет DeviceId - отзываем все
            var tokens = await _db.RefreshTokens
                .Where(r => r.UserId == userId.Value && r.RevokedAt == null)
                .ToListAsync();
            foreach (var t in tokens) t.RevokedAt = DateTime.UtcNow;
        }

        await _db.SaveChangesAsync();
        return Ok(new { message = "Logged out" });
    }

    [HttpGet("me")]
    [Authorize]
    public async Task<IActionResult> Me()
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();

        var user = await _db.Users
            .AsNoTracking()
            .Where(u => u.Id == userId.Value)
            .Select(u => new
            {
                u.Id,
                u.Email,
                u.FirstName,
                u.LastName,
                u.TotalPoints,
                u.DailyStepGoal,
                u.TeamId,
                Team = u.Team == null ? null : u.Team.Name
            })
            .FirstOrDefaultAsync();

        if (user == null) return NotFound(new { message = "User not found" });
        return Ok(user);
    }

    private async Task<string> IssueRefreshTokenAsync(int userId, string deviceId)
    {
        // Одно устройство - один активный токен: старые отзываем
        var old = await _db.RefreshTokens
            .Where(r => r.UserId == userId && r.DeviceId == deviceId && r.RevokedAt == null)
            .ToListAsync();
        foreach (var o in old) o.RevokedAt = DateTime.UtcNow;

        var token = _tokens.GenerateRefreshToken();
        _db.RefreshTokens.Add(new Models.RefreshToken
        {
            UserId = userId,
            DeviceId = deviceId,
            Token = token,
            ExpiresAt = DateTime.UtcNow.AddDays(_settings.RefreshTokenDays)
        });
        await _db.SaveChangesAsync();
        return token;
    }

    private static string NormalizeDeviceId(string? deviceId)
        => string.IsNullOrWhiteSpace(deviceId) ? "legacy" : deviceId.Trim()[..Math.Min(128, deviceId.Trim().Length)];

    private AuthResponse BuildResponse(User user, string refreshToken)
    {
        return new AuthResponse
        {
            AccessToken = _tokens.GenerateAccessToken(user),
            RefreshToken = refreshToken,
            ExpiresAt = DateTime.UtcNow.AddMinutes(_settings.AccessTokenMinutes),
            UserId = user.Id,
            Email = user.Email
        };
    }
}
