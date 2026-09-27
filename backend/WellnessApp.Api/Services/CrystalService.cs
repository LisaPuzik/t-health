using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Services;

// Правила MVP (поменяются когда придет ТЗ по кристаллу):
// - 1000 шагов = 10 XP, +2 Energy
// - 1 мин тренировки = 2 XP, +5 Energy за тренировку
// - 1 уровень = 300 XP, Energy cap 0..100
// - Decay: -10 Energy за каждые полные сутки без активности, минимум 0
public class CrystalService
{
    public const int XpPerLevel = 300;

    private readonly AppDbContext _db;

    public CrystalService(AppDbContext db)
    {
        _db = db;
    }

    public static int XpForSteps(int steps) => steps / 100; // 1000 шагов = 10 XP
    public static int EnergyForSteps(int steps) => steps / 500; // 1000 шагов = +2
    public static int XpForWorkout(int minutes) => minutes * 2;
    public const int EnergyPerWorkout = 5;

    public async Task<CrystalPet> GetOrCreateAsync(int userId)
    {
        var pet = await _db.CrystalPets.FirstOrDefaultAsync(c => c.UserId == userId);
        if (pet == null)
        {
            pet = new CrystalPet { UserId = userId };
            _db.CrystalPets.Add(pet);
            await _db.SaveChangesAsync();
            return pet;
        }
        ApplyDecay(pet, DateTime.UtcNow);
        await _db.SaveChangesAsync();
        return pet;
    }

    // Вызывать из Workouts / HealthMetrics после SaveChanges основной сущности
    public async Task<CrystalPet> AwardAsync(int userId, int xp, int energyDelta)
    {
        var pet = await _db.CrystalPets.FirstOrDefaultAsync(c => c.UserId == userId);
        if (pet == null)
        {
            pet = new CrystalPet { UserId = userId };
            _db.CrystalPets.Add(pet);
        }
        else
        {
            ApplyDecay(pet, DateTime.UtcNow);
        }

        var now = DateTime.UtcNow;
        pet.Xp += Math.Max(0, xp);
        pet.Energy = Math.Clamp(pet.Energy + energyDelta, 0, 100);
        pet.Level = pet.Xp / XpPerLevel + 1;
        if (xp > 0 || energyDelta > 0) pet.LastFedAt = now;
        pet.UpdatedAt = now;

        await _db.SaveChangesAsync();
        return pet;
    }

    public static void ApplyDecay(CrystalPet pet, DateTime now)
    {
        var daysIdle = (now.Date - pet.LastFedAt.Date).Days;
        if (daysIdle >= 1)
            pet.Energy = Math.Max(0, pet.Energy - daysIdle * 10);
        pet.UpdatedAt = now;
    }
}
