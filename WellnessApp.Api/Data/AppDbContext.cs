using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options)
        : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<Team> Teams => Set<Team>();
    public DbSet<Workout> Workouts => Set<Workout>();
    public DbSet<EmotionEntry> EmotionEntries => Set<EmotionEntry>();
    public DbSet<Challenge> Challenges => Set<Challenge>();
    public DbSet<ChallengeParticipant> ChallengeParticipants => Set<ChallengeParticipant>();
    public DbSet<Achievement> Achievements => Set<Achievement>();
    public DbSet<UserAchievement> UserAchievements => Set<UserAchievement>();
    public DbSet<Interest> Interests => Set<Interest>();
    public DbSet<UserInterest> UserInterests => Set<UserInterest>();
    public DbSet<HealthMetric> HealthMetrics => Set<HealthMetric>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<CrystalPet> CrystalPets => Set<CrystalPet>();
    public DbSet<Community> Communities => Set<Community>();
    public DbSet<CommunityMember> CommunityMembers => Set<CommunityMember>();
    public DbSet<UserFollow> UserFollows => Set<UserFollow>();
    public DbSet<WellnessContent> WellnessContents => Set<WellnessContent>();
    public DbSet<AiRecommendationCache> AiRecommendationCaches => Set<AiRecommendationCache>();
    public DbSet<Post> Posts => Set<Post>();
    public DbSet<PostLike> PostLikes => Set<PostLike>();
    public DbSet<Comment> Comments => Set<Comment>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // User -> Team
        modelBuilder.Entity<User>()
            .HasOne(u => u.Team)
            .WithMany(t => t.Users)
            .HasForeignKey(u => u.TeamId)
            .OnDelete(DeleteBehavior.SetNull);

        // User -> Workout
        modelBuilder.Entity<Workout>()
            .HasOne(w => w.User)
            .WithMany()
            .HasForeignKey(w => w.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // User -> EmotionEntry
        modelBuilder.Entity<EmotionEntry>()
            .HasOne(e => e.User)
            .WithMany()
            .HasForeignKey(e => e.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Challenge -> Participants
        modelBuilder.Entity<ChallengeParticipant>()
            .HasOne(cp => cp.Challenge)
            .WithMany()
            .HasForeignKey(cp => cp.ChallengeId)
            .OnDelete(DeleteBehavior.Cascade);

        // User -> ChallengeParticipants
        modelBuilder.Entity<ChallengeParticipant>()
            .HasOne(cp => cp.User)
            .WithMany()
            .HasForeignKey(cp => cp.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // One user cannot join the same challenge twice
        modelBuilder.Entity<ChallengeParticipant>()
            .HasIndex(cp => new { cp.ChallengeId, cp.UserId })
            .IsUnique();

        // User -> Achievement
        modelBuilder.Entity<UserAchievement>()
            .HasOne(ua => ua.User)
            .WithMany()
            .HasForeignKey(ua => ua.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // Achievement -> UserAchievement
        modelBuilder.Entity<UserAchievement>()
            .HasOne(ua => ua.Achievement)
            .WithMany()
            .HasForeignKey(ua => ua.AchievementId)
            .OnDelete(DeleteBehavior.Cascade);

        // One achievement can be earned only once by one user
        modelBuilder.Entity<UserAchievement>()
            .HasIndex(ua => new { ua.UserId, ua.AchievementId })
            .IsUnique();

        // Unique email
        modelBuilder.Entity<User>()
            .HasIndex(u => u.Email)
            .IsUnique();

        modelBuilder.Entity<UserInterest>()
            .HasKey(ui => new { ui.UserId, ui.InterestId });

        modelBuilder.Entity<UserInterest>()
            .HasOne(ui => ui.User)
            .WithMany(u => u.UserInterests)
            .HasForeignKey(ui => ui.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<UserInterest>()
            .HasOne(ui => ui.Interest)
            .WithMany(i => i.UserInterests)
            .HasForeignKey(ui => ui.InterestId)
            .OnDelete(DeleteBehavior.Cascade);

        // User -> HealthMetric
        modelBuilder.Entity<HealthMetric>()
            .HasOne(h => h.User)
            .WithMany()
            .HasForeignKey(h => h.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<HealthMetric>()
            .HasIndex(h => new { h.UserId, h.Type, h.RecordedAt });

        // Дедуп повторных синков с телефона: NULL ExternalId не конфликтуют в Postgres - то что надо
        modelBuilder.Entity<HealthMetric>()
            .HasIndex(h => new { h.UserId, h.Source, h.ExternalId })
            .IsUnique();

        // User -> RefreshToken (мульти-девайс)
        modelBuilder.Entity<RefreshToken>()
            .HasOne(r => r.User)
            .WithMany()
            .HasForeignKey(r => r.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<RefreshToken>()
            .HasIndex(r => r.Token)
            .IsUnique();

        modelBuilder.Entity<RefreshToken>()
            .HasIndex(r => new { r.UserId, r.DeviceId });

        // Один кристалл на юзера
        modelBuilder.Entity<CrystalPet>()
            .HasOne(c => c.User)
            .WithMany()
            .HasForeignKey(c => c.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<CrystalPet>()
            .HasIndex(c => c.UserId)
            .IsUnique();

        modelBuilder.Entity<CommunityMember>()
            .HasKey(m => new { m.CommunityId, m.UserId });
        modelBuilder.Entity<CommunityMember>()
            .HasOne(m => m.Community).WithMany().HasForeignKey(m => m.CommunityId).OnDelete(DeleteBehavior.Cascade);
        modelBuilder.Entity<CommunityMember>()
            .HasOne(m => m.User).WithMany().HasForeignKey(m => m.UserId).OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<UserFollow>()
            .HasKey(f => new { f.FollowerId, f.FolloweeId });
        modelBuilder.Entity<UserFollow>()
            .HasOne(f => f.Follower).WithMany().HasForeignKey(f => f.FollowerId).OnDelete(DeleteBehavior.Cascade);
        modelBuilder.Entity<UserFollow>()
            .HasOne(f => f.Followee).WithMany().HasForeignKey(f => f.FolloweeId).OnDelete(DeleteBehavior.Restrict);

        modelBuilder.Entity<WellnessContent>()
            .HasOne(w => w.Interest).WithMany().HasForeignKey(w => w.InterestId).OnDelete(DeleteBehavior.SetNull);
        modelBuilder.Entity<WellnessContent>()
            .HasOne(w => w.Community).WithMany().HasForeignKey(w => w.CommunityId).OnDelete(DeleteBehavior.SetNull);

        // Один свежий AI-кэш на юзера
        modelBuilder.Entity<AiRecommendationCache>()
            .HasOne(c => c.User)
            .WithMany()
            .HasForeignKey(c => c.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<AiRecommendationCache>()
            .HasIndex(c => c.UserId)
            .IsUnique();

        // Лента: пост автора, лайки и комменты каскадно за постом
        modelBuilder.Entity<Post>()
            .HasOne(p => p.User)
            .WithMany()
            .HasForeignKey(p => p.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Post>()
            .HasOne(p => p.Community)
            .WithMany()
            .HasForeignKey(p => p.CommunityId)
            .OnDelete(DeleteBehavior.SetNull);

        modelBuilder.Entity<Post>()
            .HasOne(p => p.Interest)
            .WithMany()
            .HasForeignKey(p => p.InterestId)
            .OnDelete(DeleteBehavior.SetNull);

        modelBuilder.Entity<Post>()
            .HasIndex(p => p.CreatedAt);

        modelBuilder.Entity<PostLike>()
            .HasKey(l => new { l.PostId, l.UserId });

        modelBuilder.Entity<PostLike>()
            .HasOne(l => l.Post)
            .WithMany()
            .HasForeignKey(l => l.PostId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<PostLike>()
            .HasOne(l => l.User)
            .WithMany()
            .HasForeignKey(l => l.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Comment>()
            .HasOne(c => c.Post)
            .WithMany()
            .HasForeignKey(c => c.PostId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Comment>()
            .HasOne(c => c.User)
            .WithMany()
            .HasForeignKey(c => c.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<Comment>()
            .HasIndex(c => c.PostId);
    }
}