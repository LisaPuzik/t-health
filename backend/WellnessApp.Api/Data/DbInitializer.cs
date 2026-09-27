using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Data;

public static class DbInitializer
{
    public static async Task SeedAsync(AppDbContext db)
    {
        // Backfill паролей для БД, созданной до введения auth
        if (await db.Users.AnyAsync())
        {
            var withoutPassword = await db.Users
                .Where(u => u.PasswordHash == null || u.PasswordHash == "")
                .ToListAsync();
            if (withoutPassword.Count > 0)
            {
                foreach (var u in withoutPassword)
                    u.PasswordHash = BCrypt.Net.BCrypt.HashPassword("Password123!");
                await db.SaveChangesAsync();
            }

            await EnsureStepsChallengeAsync(db);
            await EnsureCrystalPetsAsync(db);
            await EnsureFeedSeedAsync(db);
            await EnsureFeedPostsAsync(db);
            return;
        }

        var engineering = new Team
        {
            Name = "Engineering"
        };

        var marketing = new Team
        {
            Name = "Marketing"
        };

        db.Teams.AddRange(engineering, marketing);
        await db.SaveChangesAsync();

        var alice = new User
        {
            Email = "alice@company.com",
            FirstName = "Alice",
            LastName = "Johnson",
            TeamId = engineering.Id,
            TotalPoints = 120,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("Password123!")
        };

        var bob = new User
        {
            Email = "bob@company.com",
            FirstName = "Bob",
            LastName = "Smith",
            TeamId = engineering.Id,
            TotalPoints = 80,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("Password123!")
        };

        var charlie = new User
        {
            Email = "charlie@company.com",
            FirstName = "Charlie",
            LastName = "Brown",
            TeamId = marketing.Id,
            TotalPoints = 150,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword("Password123!")
        };

        db.Users.AddRange(alice, bob, charlie);
        await db.SaveChangesAsync();

        var runningChallenge = new Challenge
        {
            Title = "100 Minutes of Activity",
            Description = "Complete 100 minutes of activity.",
            MetricType = "WorkoutMinutes",
            TargetValue = 100,
            Points = 100,
            StartDate = DateTime.UtcNow.Date,
            EndDate = DateTime.UtcNow.Date.AddDays(7),
            IsActive = true
        };

        var workoutChallenge = new Challenge
        {
            Title = "Workout Challenge",
            Description = "Complete 5 workouts.",
            MetricType = "WorkoutCount",
            TargetValue = 5,
            Points = 150,
            StartDate = DateTime.UtcNow.Date,
            EndDate = DateTime.UtcNow.Date.AddDays(14),
            IsActive = true
        };

        db.Challenges.AddRange(runningChallenge, workoutChallenge);

        // Шаговый челлендж для мобайла (HealthKit / Health Connect)
        var stepsChallenge = new Challenge
        {
            Title = "50,000 Steps Week",
            Description = "Walk 50,000 steps in 7 days. Sync from Apple Health or Health Connect.",
            MetricType = "Steps",
            TargetValue = 50000,
            Points = 150,
            StartDate = DateTime.UtcNow.Date,
            EndDate = DateTime.UtcNow.Date.AddDays(7),
            IsActive = true
        };
        db.Challenges.Add(stepsChallenge);

        var firstWorkout = new Achievement
        {
            Name = "First Workout",
            Description = "Complete your first workout.",
            Points = 20
        };

        var challengeCompleted = new Achievement
        {
            Name = "Challenge Completed",
            Description = "Complete your first challenge.",
            Points = 50
        };

        db.Achievements.AddRange(firstWorkout, challengeCompleted);

        await db.SaveChangesAsync();

        var workouts = new List<Workout>
        {
            new()
            {
                UserId = alice.Id,
                Type = "Running",
                DurationMinutes = 30,
                Points = 30,
                CreatedAt = DateTime.UtcNow.AddDays(-1)
            },
            new()
            {
                UserId = alice.Id,
                Type = "Yoga",
                DurationMinutes = 20,
                Points = 20,
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                UserId = bob.Id,
                Type = "Walking",
                DurationMinutes = 45,
                Points = 45,
                CreatedAt = DateTime.UtcNow.AddDays(-1)
            }
        };

        db.Workouts.AddRange(workouts);

        var emotions = new List<EmotionEntry>
        {
            new()
            {
                UserId = alice.Id,
                Mood = 5,
                Note = "Feeling great today.",
                CreatedAt = DateTime.UtcNow
            },
            new()
            {
                UserId = bob.Id,
                Mood = 3,
                Note = "A bit tired.",
                CreatedAt = DateTime.UtcNow
            }
        };

        db.EmotionEntries.AddRange(emotions);

        var participants = new List<ChallengeParticipant>
        {
            new()
            {
                ChallengeId = runningChallenge.Id,
                UserId = alice.Id,
                CurrentValue = 50,
                IsCompleted = false
            },
            new()
            {
                ChallengeId = runningChallenge.Id,
                UserId = bob.Id,
                CurrentValue = 45,
                IsCompleted = false
            },
            new()
            {
                ChallengeId = workoutChallenge.Id,
                UserId = charlie.Id,
                CurrentValue = 5,
                IsCompleted = true
            }
        };

        db.ChallengeParticipants.AddRange(participants);

        var achievements = new List<UserAchievement>
        {
            new()
            {
                UserId = alice.Id,
                AchievementId = firstWorkout.Id
            },
            new()
            {
                UserId = charlie.Id,
                AchievementId = firstWorkout.Id
            },
            new()
            {
                UserId = charlie.Id,
                AchievementId = challengeCompleted.Id
            }
        };

        var interests = CanonicalInterests.Select(n => new Interest { Name = n }).ToList();

        db.Interests.AddRange(interests);
        await db.SaveChangesAsync();

        db.UserAchievements.AddRange(achievements);

        await db.SaveChangesAsync();

        await EnsureCrystalPetsAsync(db);
        await EnsureFeedSeedAsync(db);
        await EnsureFeedPostsAsync(db);
    }

    private static async Task EnsureStepsChallengeAsync(AppDbContext db)
    {
        if (await db.Challenges.AnyAsync(c => c.MetricType == "Steps" && c.IsActive))
            return;

        db.Challenges.Add(new Challenge
        {
            Title = "50,000 Steps Week",
            Description = "Walk 50,000 steps in 7 days. Sync from Apple Health or Health Connect.",
            MetricType = "Steps",
            TargetValue = 50000,
            Points = 150,
            StartDate = DateTime.UtcNow.Date,
            EndDate = DateTime.UtcNow.Date.AddDays(7),
            IsActive = true
        });
        await db.SaveChangesAsync();
    }

    private static readonly string[] CanonicalInterests =
    [
        "Running", "Fitness", "TherapeuticExercise", "Bicycling", "Swimming",
        "WeightTraining", "Skiing", "MartialArts", "Chess", "Volleyball",
        "Football", "Tennis", "Basketball", "Hockey"
    ];

    // Старые ключи -> новые (для переназначения контента и подписок)
    private static readonly Dictionary<string, string?> InterestRemap = new()
    {
        ["Walking"] = "Running",
        ["Yoga"] = "Fitness",
        ["MentalHealth"] = "Fitness",
        ["Nutrition"] = null, // категории еды больше нет
    };

    private static async Task EnsureInterestsAsync(AppDbContext db)
    {
        var existing = (await db.Interests.Select(i => i.Name).ToListAsync()).ToHashSet();
        var missing = CanonicalInterests.Where(n => !existing.Contains(n)).Select(n => new Interest { Name = n });
        if (missing.Any())
        {
            db.Interests.AddRange(missing);
            await db.SaveChangesAsync();
        }

        // Убираем устаревшие: контент переназначаем, подписки чистим
        var obsolete = await db.Interests
            .Where(i => !CanonicalInterests.Contains(i.Name))
            .ToListAsync();
        if (obsolete.Count == 0) return;

        var byName = await db.Interests.ToDictionaryAsync(i => i.Name, i => i.Id);
        foreach (var old in obsolete)
        {
            if (InterestRemap.TryGetValue(old.Name, out var target) &&
                target != null && byName.TryGetValue(target, out var targetId))
            {
                var contents = await db.WellnessContents
                    .Where(w => w.InterestId == old.Id).ToListAsync();
                foreach (var w in contents) w.InterestId = targetId;

                var dupes = await db.UserInterests
                    .Where(u => u.InterestId == old.Id).ToListAsync();
                var already = (await db.UserInterests
                    .Where(u => u.InterestId == targetId)
                    .Select(u => u.UserId).ToListAsync()).ToHashSet();
                db.UserInterests.RemoveRange(dupes);
                var newcomers = dupes
                    .Where(u => !already.Contains(u.UserId))
                    .Select(u => new UserInterest { UserId = u.UserId, InterestId = targetId });
                db.UserInterests.AddRange(newcomers);
            }
            else
            {
                var contents = await db.WellnessContents
                    .Where(w => w.InterestId == old.Id).ToListAsync();
                foreach (var w in contents) w.InterestId = null;

                var links = await db.UserInterests
                    .Where(u => u.InterestId == old.Id).ToListAsync();
                db.UserInterests.RemoveRange(links);
            }
            db.Interests.Remove(old);
        }
        await db.SaveChangesAsync();
    }
    private static async Task EnsureCrystalPetsAsync(AppDbContext db)
    {
        var userIds = await db.Users.Select(u => u.Id).ToListAsync();
        var existing = (await db.CrystalPets.Select(c => c.UserId).ToListAsync()).ToHashSet();
        var missing = userIds.Where(id => !existing.Contains(id))
            .Select(id => new CrystalPet { UserId = id });
        if (missing.Any())
        {
            db.CrystalPets.AddRange(missing);
            await db.SaveChangesAsync();
        }
    }

    // Сиды ленты постов (как в макете). Идемпотентно.
    private static async Task EnsureFeedPostsAsync(AppDbContext db)
    {
        if (await db.Posts.AnyAsync()) return;

        var users = await db.Users.ToListAsync();
        if (users.Count == 0) return;
        User ByEmail(string email) => users.FirstOrDefault(u => u.Email == email) ?? users[0];

        var now = DateTime.UtcNow;
        var posts = new List<Post>
        {
            new()
            {
                UserId = ByEmail("charlie@company.com").Id,
                PostType = "Recipe",
                ImageEmoji = "🥗",
                Content = "Приготовила полезный боул с киноа, авокадо и лососем 🥗 Делюсь рецептом — всё просто, 15 минут.",
                CreatedAt = now.AddHours(-2)
            },
            new()
            {
                UserId = ByEmail("bob@company.com").Id,
                PostType = "Workout",
                ImageEmoji = "🏃",
                Content = "Утренняя пробежка 7 км по набережной. Погода — 🔥, темп 5:20/км.",
                CreatedAt = now.AddHours(-4)
            },
            new()
            {
                UserId = ByEmail("alice@company.com").Id,
                PostType = "Achievement",
                ImageEmoji = "🏆",
                Content = "Закрыла челлендж «100k шагов за неделю» 🎉 Спасибо команде за поддержку!",
                CreatedAt = now.AddDays(-1)
            },
            new()
            {
                UserId = ByEmail("charlie@company.com").Id,
                PostType = "General",
                ImageEmoji = null,
                Content = "Напоминаю: в субботу в 10:00 — совместная йога в парке. Записывайтесь в сообществе 🧘",
                CreatedAt = now.AddDays(-2)
            }
        };
        db.Posts.AddRange(posts);
        await db.SaveChangesAsync();

        var alice = ByEmail("alice@company.com");
        var bob = ByEmail("bob@company.com");
        var charlie = ByEmail("charlie@company.com");
        db.PostLikes.AddRange(
            new PostLike { PostId = posts[0].Id, UserId = alice.Id },
            new PostLike { PostId = posts[0].Id, UserId = bob.Id },
            new PostLike { PostId = posts[1].Id, UserId = alice.Id },
            new PostLike { PostId = posts[1].Id, UserId = charlie.Id },
            new PostLike { PostId = posts[2].Id, UserId = bob.Id },
            new PostLike { PostId = posts[2].Id, UserId = charlie.Id });
        db.Comments.AddRange(
            new Comment { PostId = posts[0].Id, UserId = alice.Id, Content = "Выглядит вкусно! А чем киноа заменяла?", CreatedAt = now.AddHours(-1) },
            new Comment { PostId = posts[1].Id, UserId = charlie.Id, Content = "Красава! В следующий раз бегу с тобой 💪", CreatedAt = now.AddHours(-3) });
        await db.SaveChangesAsync();
    }

    // Сиды ленты: сообщества + каталог рекомендаций. Идемпотентно.
    private static async Task EnsureFeedSeedAsync(AppDbContext db)
    {
        await EnsureInterestsAsync(db);

        if (!await db.Communities.AnyAsync())
        {
            db.Communities.AddRange(
                new Community { Name = "Yoga & Breath", Description = "Медитации, дыхание, йога." },
                new Community { Name = "Healthy Food", Description = "Рецепты и питание." },
                new Community { Name = "Runners", Description = "Планы тренировок и забеги." });
            await db.SaveChangesAsync();
        }

        if (!await db.WellnessContents.AnyAsync())
        {
            var byName = await db.Interests.ToDictionaryAsync(i => i.Name, i => i.Id);
            var commByName = await db.Communities.ToDictionaryAsync(c => c.Name, c => c.Id);
            int? Id(string n) => byName.TryGetValue(n, out var v) ? v : null;
            int? Comm(string n) => commByName.TryGetValue(n, out var v) ? v : null;

            db.WellnessContents.AddRange(
                new WellnessContent { Title = "Дыхание 5 минут", Description = "Короткая практика чтобы снизить стресс.", ContentType = "Meditation", MinMood = 1, MaxMood = 2, InterestId = Id("Fitness"), CommunityId = Comm("Yoga & Breath") },
                new WellnessContent { Title = "Медитация перед сном", Description = "10 минут спокойствия.", ContentType = "Meditation", MinMood = 1, MaxMood = 3, InterestId = Id("Fitness"), CommunityId = Comm("Yoga & Breath") },
                new WellnessContent { Title = "Курс: энергия за 7 дней", Description = "Сон, свет, прогулки.", ContentType = "Course", MinMood = 2, MaxMood = 3, InterestId = Id("Fitness"), CommunityId = Comm("Runners") },
                new WellnessContent { Title = "Курс саморазвития: фокус", Description = "Привычки и внимание.", ContentType = "Course", MinMood = 3, MaxMood = 4, InterestId = Id("Fitness") },
                new WellnessContent { Title = "Легкая пробежка 20 мин", Description = "План для настроения 4+.", ContentType = "Article", MinMood = 4, MaxMood = 5, InterestId = Id("Running"), CommunityId = Comm("Runners") },
                new WellnessContent { Title = "Боул с киноа", Description = "Рецепт восстановления.", ContentType = "Recipe", MinMood = 3, MaxMood = 5, InterestId = null, CommunityId = Comm("Healthy Food") });
            await db.SaveChangesAsync();
        }

        // Патч контента, засиженного до появления интересов (InterestId = null)
        {
            var byName = await db.Interests.ToDictionaryAsync(i => i.Name, i => i.Id);
            var map = new Dictionary<string, string?>
            {
                ["Дыхание 5 минут"] = "Fitness",
                ["Медитация перед сном"] = "Fitness",
                ["Курс: энергия за 7 дней"] = "Fitness",
                ["Курс саморазвития: фокус"] = "Fitness",
                ["Легкая пробежка 20 мин"] = "Running",
                ["Боул с киноа"] = null
            };
            var contents = await db.WellnessContents.Where(w => w.InterestId == null).ToListAsync();
            var changed = false;
            foreach (var w in contents)
            {
                if (!map.TryGetValue(w.Title, out var iname)) continue;
                if (iname == null) continue; // без категории — тоже состояние
                if (byName.TryGetValue(iname, out var iid))
                {
                    w.InterestId = iid;
                    changed = true;
                }
            }
            if (changed) await db.SaveChangesAsync();
        }

        // alice в Yoga, bob в Runners - чтобы лента была не пустая
        var alice = await db.Users.FirstOrDefaultAsync(u => u.Email == "alice@company.com");
        var bob = await db.Users.FirstOrDefaultAsync(u => u.Email == "bob@company.com");
        var yoga = await db.Communities.FirstOrDefaultAsync(c => c.Name == "Yoga & Breath");
        var runners = await db.Communities.FirstOrDefaultAsync(c => c.Name == "Runners");
        if (alice != null && yoga != null && !await db.CommunityMembers.AnyAsync(m => m.CommunityId == yoga.Id && m.UserId == alice.Id))
            db.CommunityMembers.Add(new CommunityMember { CommunityId = yoga.Id, UserId = alice.Id });
        if (bob != null && runners != null && !await db.CommunityMembers.AnyAsync(m => m.CommunityId == runners.Id && m.UserId == bob.Id))
            db.CommunityMembers.Add(new CommunityMember { CommunityId = runners.Id, UserId = bob.Id });
        await db.SaveChangesAsync();
    }
}