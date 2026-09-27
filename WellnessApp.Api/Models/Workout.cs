namespace WellnessApp.Api.Models;

public class Workout
{
    public int Id { get; set; }

    public int UserId { get; set; }

    public User User { get; set; } = null!;

    public string Type { get; set; } = string.Empty;

    public int DurationMinutes { get; set; }

    public int Points { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}