using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/feed")]
[Authorize]
public class FeedController : ControllerBase
{
    private readonly AppDbContext _db;
    public FeedController(AppDbContext db) { _db = db; }

    private static readonly HashSet<string> AllowedTypes = new(StringComparer.OrdinalIgnoreCase)
    {
        "General", "Recipe", "Workout", "Achievement"
    };

    public class CreatePostRequest
    {
        public string Content { get; set; } = string.Empty;
        public string PostType { get; set; } = "General";
        public string? ImageEmoji { get; set; }
    }

    public class CreateCommentRequest
    {
        public string Content { get; set; } = string.Empty;
    }

    // Лента: новые сверху, фильтр по типу (Рецепты/Тренировки/Достижения)
    [HttpGet]
    public async Task<IActionResult> List(
        [FromQuery] string? type, [FromQuery] int page = 1, [FromQuery] int pageSize = 20)
    {
        var me = User.GetCurrentUserId();
        if (me == null) return Unauthorized();

        page = Math.Clamp(page, 1, 100);
        pageSize = Math.Clamp(pageSize, 1, 50);

        var q = _db.Posts.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(type))
        {
            if (!AllowedTypes.Contains(type.Trim())) return BadRequest(new { message = "Invalid type" });
            var t = type.Trim();
            q = q.Where(p => p.PostType == t);
        }

        var total = await q.CountAsync();
        var items = await q.OrderByDescending(p => p.CreatedAt)
            .Skip((page - 1) * pageSize).Take(pageSize)
            .Select(p => new
            {
                id = p.Id,
                authorName = p.User.FirstName,
                authorTeam = p.User.Team != null ? p.User.Team.Name : null as string,
                content = p.Content,
                postType = p.PostType,
                imageEmoji = p.ImageEmoji,
                createdAt = p.CreatedAt,
                likesCount = _db.PostLikes.Count(l => l.PostId == p.Id),
                likedByMe = _db.PostLikes.Any(l => l.PostId == p.Id && l.UserId == me.Value),
                commentsCount = _db.Comments.Count(c => c.PostId == p.Id)
            })
            .ToListAsync();

        return Ok(new { total, page, pageSize, items });
    }

    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreatePostRequest req)
    {
        var me = User.GetCurrentUserId();
        if (me == null) return Unauthorized();

        var content = (req.Content ?? "").Trim();
        if (content.Length is < 1 or > 1000)
            return BadRequest(new { message = "Content required, max 1000" });

        var type = string.IsNullOrWhiteSpace(req.PostType) ? "General" : req.PostType.Trim();
        if (!AllowedTypes.Contains(type))
            return BadRequest(new { message = "Invalid postType" });

        var emoji = string.IsNullOrWhiteSpace(req.ImageEmoji) ? null : req.ImageEmoji.Trim();
        if (emoji is { Length: > 32 })
            return BadRequest(new { message = "ImageEmoji max 32 chars" });

        var post = new Post
        {
            UserId = me.Value,
            Content = content,
            PostType = type,
            ImageEmoji = emoji,
            CreatedAt = DateTime.UtcNow
        };
        _db.Posts.Add(post);
        await _db.SaveChangesAsync();

        return Ok(new
        {
            id = post.Id,
            content = post.Content,
            postType = post.PostType,
            imageEmoji = post.ImageEmoji,
            createdAt = post.CreatedAt
        });
    }

    // Лайк-тоггл: повторный запрос снимает лайк
    [HttpPost("{id:int}/like")]
    public async Task<IActionResult> ToggleLike(int id)
    {
        var me = User.GetCurrentUserId();
        if (me == null) return Unauthorized();
        if (!await _db.Posts.AnyAsync(p => p.Id == id)) return NotFound(new { message = "Post not found" });

        var existing = await _db.PostLikes
            .FirstOrDefaultAsync(l => l.PostId == id && l.UserId == me.Value);
        bool liked;
        if (existing == null)
        {
            _db.PostLikes.Add(new PostLike { PostId = id, UserId = me.Value });
            liked = true;
        }
        else
        {
            _db.PostLikes.Remove(existing);
            liked = false;
        }
        await _db.SaveChangesAsync();

        var count = await _db.PostLikes.CountAsync(l => l.PostId == id);
        return Ok(new { liked, likesCount = count });
    }

    [HttpGet("{id:int}/comments")]
    public async Task<IActionResult> Comments(int id)
    {
        if (!await _db.Posts.AnyAsync(p => p.Id == id)) return NotFound(new { message = "Post not found" });

        var items = await _db.Comments.AsNoTracking()
            .Where(c => c.PostId == id)
            .OrderBy(c => c.CreatedAt)
            .Select(c => new
            {
                id = c.Id,
                authorName = c.User.FirstName,
                content = c.Content,
                createdAt = c.CreatedAt
            })
            .ToListAsync();

        return Ok(new { items });
    }

    [HttpPost("{id:int}/comments")]
    public async Task<IActionResult> AddComment(int id, [FromBody] CreateCommentRequest req)
    {
        var me = User.GetCurrentUserId();
        if (me == null) return Unauthorized();
        if (!await _db.Posts.AnyAsync(p => p.Id == id)) return NotFound(new { message = "Post not found" });

        var content = (req.Content ?? "").Trim();
        if (content.Length is < 1 or > 500)
            return BadRequest(new { message = "Content required, max 500" });

        var comment = new Comment { PostId = id, UserId = me.Value, Content = content };
        _db.Comments.Add(comment);
        await _db.SaveChangesAsync();

        return Ok(new { id = comment.Id, content = comment.Content, createdAt = comment.CreatedAt });
    }
}
