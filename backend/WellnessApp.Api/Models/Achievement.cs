namespace WellnessApp.Api.Models;

public class Achievement
{
    public int Id { get; set; }

    public string Name { get; set; } = string.Empty;

    public string Description { get; set; } = string.Empty;

    public int Points { get; set; }
}