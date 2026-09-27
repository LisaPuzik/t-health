namespace WellnessApp.Api.Models;

public class Community
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class CommunityMember
{
    public int CommunityId { get; set; }
    public Community Community { get; set; } = null!;
    public int UserId { get; set; }
    public User User { get; set; } = null!;
    public DateTime JoinedAt { get; set; } = DateTime.UtcNow;
}

// Подписка на пользователя
public class UserFollow
{
    public int FollowerId { get; set; }
    public User Follower { get; set; } = null!;
    public int FolloweeId { get; set; }
    public User Followee { get; set; } = null!;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

// Каталог для рекомендаций: медитации, курсы (лежат в сообществах системы)
public class WellnessContent
{
    public int Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;

    // Meditation, Course, Article, Recipe
    public string ContentType { get; set; } = string.Empty;

    // При каком настроении рекомендовать (1..5)
    public int MinMood { get; set; } = 1;
    public int MaxMood { get; set; } = 5;

    public int? InterestId { get; set; }
    public Interest? Interest { get; set; }

    public int? CommunityId { get; set; }
    public Community? Community { get; set; }
}
