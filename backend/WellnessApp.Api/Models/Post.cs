namespace WellnessApp.Api.Models;

// Лента новостей: посты коллег (рецепты, тренировки, достижения).
public class Post
{
    public int Id { get; set; }

    public int UserId { get; set; }
    public User User { get; set; } = null!;

    public string Content { get; set; } = string.Empty;

    // General, Recipe, Workout, Achievement
    public string PostType { get; set; } = "General";

    // Эмодзи для картинки-заглушки (как в макете), null = без картинки
    public string? ImageEmoji { get; set; }

    public string? Title { get; set; }

    public int? CommunityId { get; set; }
    public Community? Community { get; set; }

    public int? InterestId { get; set; }
    public Interest? Interest { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class PostLike
{
    public int PostId { get; set; }
    public Post Post { get; set; } = null!;

    public int UserId { get; set; }
    public User User { get; set; } = null!;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}

public class Comment
{
    public int Id { get; set; }

    public int PostId { get; set; }
    public Post Post { get; set; } = null!;

    public int UserId { get; set; }
    public User User { get; set; } = null!;

    public string Content { get; set; } = string.Empty;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
