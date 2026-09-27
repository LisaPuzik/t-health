namespace WellnessApp.Api.Models;

public class User
{
    public int Id { get; set; }

    public string Email { get; set; } = string.Empty;

    public string FirstName { get; set; } = string.Empty;

    public string LastName { get; set; } = string.Empty;

    public int? TeamId { get; set; }

    public Team? Team { get; set; }

    public int TotalPoints { get; set; }

    public int DailyStepGoal { get; set; } = 10000;

    public string PasswordHash { get; set; } = string.Empty;

    public string? RefreshToken { get; set; }

    public DateTime? RefreshTokenExpiryTime { get; set; }

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public List<UserInterest> UserInterests { get; set; } = [];
}