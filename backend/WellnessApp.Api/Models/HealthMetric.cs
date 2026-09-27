namespace WellnessApp.Api.Models;

public class HealthMetric
{
    public int Id { get; set; }

    public int UserId { get; set; }

    public User User { get; set; } = null!;

    // Steps, HeartRate, Distance, ActiveEnergy, Weight, SleepHours
    public string Type { get; set; } = string.Empty;

    public double Value { get; set; }

    // count, bpm, km, kcal, kg, hours
    public string Unit { get; set; } = string.Empty;

    public DateTime RecordedAt { get; set; }

    // AppleHealthKit, GoogleHealthConnect, Strava, Manual
    public string Source { get; set; } = string.Empty;

    // ID записи на стороне телефона/провайдера, для идемпотентности
    public string? ExternalId { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
