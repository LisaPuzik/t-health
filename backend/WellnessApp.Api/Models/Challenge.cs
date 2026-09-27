namespace WellnessApp.Api.Models;

public class Challenge
{
    public int Id { get; set; }

    public string Title { get; set; } = string.Empty;

    public string Description { get; set; } = string.Empty;

    public string MetricType { get; set; } = string.Empty;

    public int TargetValue { get; set; }

    public int Points { get; set; }

    public DateTime StartDate { get; set; }

    public DateTime EndDate { get; set; }

    public bool IsActive { get; set; } = true;
}