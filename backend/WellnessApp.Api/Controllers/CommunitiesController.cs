using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/communities")]
[Authorize]
public class CommunitiesController : ControllerBase
{
    private readonly AppDbContext _db;
    public CommunitiesController(AppDbContext db) { _db = db; }

    public class CreateCommunityRequest
    {
        public string Name { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
    }

    [HttpGet]
    public async Task<IActionResult> List()
    {
        var userId = User.GetCurrentUserId();
        var items = await _db.Communities.AsNoTracking().OrderBy(c => c.Name)
            .Select(c => new { c.Id, c.Name, c.Description,
                members = _db.CommunityMembers.Count(m => m.CommunityId == c.Id),
                joined = userId != null && _db.CommunityMembers.Any(m => m.CommunityId == c.Id && m.UserId == userId.Value) })
            .ToListAsync();
        return Ok(items);
    }

    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreateCommunityRequest req)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();
        if (string.IsNullOrWhiteSpace(req.Name) || req.Name.Length > 100)
            return BadRequest(new { message = "Name required, max 100" });

        var c = new Community { Name = req.Name.Trim(), Description = req.Description.Trim() };
        _db.Communities.Add(c);
        await _db.SaveChangesAsync();
        _db.CommunityMembers.Add(new CommunityMember { CommunityId = c.Id, UserId = userId.Value });
        await _db.SaveChangesAsync();
        return Ok(new { c.Id, c.Name, c.Description });
    }

    [HttpPost("{id:int}/join")]
    public async Task<IActionResult> Join(int id)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();
        if (!await _db.Communities.AnyAsync(c => c.Id == id)) return NotFound(new { message = "Community not found" });
        if (await _db.CommunityMembers.AnyAsync(m => m.CommunityId == id && m.UserId == userId.Value))
            return BadRequest(new { message = "Already joined" });
        _db.CommunityMembers.Add(new CommunityMember { CommunityId = id, UserId = userId.Value });
        await _db.SaveChangesAsync();
        return Ok(new { message = "Joined" });
    }

    [HttpPost("{id:int}/leave")]
    public async Task<IActionResult> Leave(int id)
    {
        var userId = User.GetCurrentUserId();
        var m = await _db.CommunityMembers.FirstOrDefaultAsync(x => x.CommunityId == id && x.UserId == userId);
        if (m == null) return NotFound(new { message = "Not a member" });
        _db.CommunityMembers.Remove(m);
        await _db.SaveChangesAsync();
        return Ok(new { message = "Left" });
    }
}

[ApiController]
[Route("api/follows")]
[Authorize]
public class FollowsController : ControllerBase
{
    private readonly AppDbContext _db;
    public FollowsController(AppDbContext db) { _db = db; }

    [HttpPost("{userId:int}")]
    public async Task<IActionResult> Follow(int userId)
    {
        var me = User.GetCurrentUserId();
        if (me == null) return Unauthorized();
        if (me.Value == userId) return BadRequest(new { message = "Cannot follow yourself" });
        if (!await _db.Users.AnyAsync(u => u.Id == userId)) return NotFound(new { message = "User not found" });
        if (await _db.UserFollows.AnyAsync(f => f.FollowerId == me.Value && f.FolloweeId == userId))
            return BadRequest(new { message = "Already following" });
        _db.UserFollows.Add(new UserFollow { FollowerId = me.Value, FolloweeId = userId });
        await _db.SaveChangesAsync();
        return Ok(new { message = "Following" });
    }

    [HttpDelete("{userId:int}")]
    public async Task<IActionResult> Unfollow(int userId)
    {
        var me = User.GetCurrentUserId();
        var f = await _db.UserFollows.FirstOrDefaultAsync(x => x.FollowerId == me && x.FolloweeId == userId);
        if (f == null) return NotFound();
        _db.UserFollows.Remove(f);
        await _db.SaveChangesAsync();
        return Ok(new { message = "Unfollowed" });
    }

    [HttpGet("me")]
    public async Task<IActionResult> MyFollows()
    {
        var me = User.GetCurrentUserId();
        var following = await _db.UserFollows.AsNoTracking().Where(f => f.FollowerId == me)
            .Select(f => new { f.FolloweeId, Name = f.Followee.FirstName + " " + f.Followee.LastName }).ToListAsync();
        var followers = await _db.UserFollows.AsNoTracking().Where(f => f.FolloweeId == me)
            .Select(f => new { f.FollowerId, Name = f.Follower.FirstName + " " + f.Follower.LastName }).ToListAsync();
        return Ok(new { following, followers });
    }
}
