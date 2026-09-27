namespace WellnessApp.Api.Models;

// Тамагочи-кристалл MVP: XP копят шаги и тренировки, Energy - "сытость" (decay по дням без активности).
public class CrystalPet
{
    public int Id { get; set; }

    public int UserId { get; set; }

    public User User { get; set; } = null!;

    public int Xp { get; set; }

    public int Level { get; set; } = 1;

    // 0..100
    public int Energy { get; set; } = 50;

    public DateTime LastFedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
