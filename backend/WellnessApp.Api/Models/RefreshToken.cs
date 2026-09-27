namespace WellnessApp.Api.Models;

// Один refresh-токен = одно устройство. Позволяет сидеть с iPhone + Android одновременно.
public class RefreshToken
{
    public int Id { get; set; }

    public int UserId { get; set; }

    public User User { get; set; } = null!;

    // UUID устройства, присылает мобильный клиент. Пустой = legacy/web.
    public string DeviceId { get; set; } = string.Empty;

    public string Token { get; set; } = string.Empty;

    public DateTime ExpiresAt { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime? RevokedAt { get; set; }
}
