namespace WellnessApp.Api.Models;

// Кэш AI-подборки: один свежий документ на юзера
public class AiRecommendationCache
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public User User { get; set; } = null!;
    public string PayloadJson { get; set; } = string.Empty;
    public string Source { get; set; } = "rules"; // ai | rules
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime ExpiresAt { get; set; }
}
