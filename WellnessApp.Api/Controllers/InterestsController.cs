using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.Controllers;

[ApiController]
[Route("api/interests")]
[Authorize]
public class InterestsController : ControllerBase
{
    private readonly AppDbContext _db;
    public InterestsController(AppDbContext db) { _db = db; }

    [HttpGet]
    public async Task<IActionResult> List()
    {
        var userId = User.GetCurrentUserId();
        var items = await _db.Interests.AsNoTracking().OrderBy(i => i.Name)
            .Select(i => new { i.Id, i.Name,
                selected = userId != null && _db.UserInterests.Any(u => u.UserId == userId.Value && u.InterestId == i.Id) })
            .ToListAsync();
        return Ok(items);
    }

    public class SetInterestsRequest { public List<int> InterestIds { get; set; } = []; }

    [HttpPut("me")]
    public async Task<IActionResult> SetMine([FromBody] SetInterestsRequest req)
    {
        var userId = User.GetCurrentUserId();
        if (userId == null) return Unauthorized();
        if (req.InterestIds.Count > 20) return BadRequest(new { message = "Max 20 interests" });
        if (req.InterestIds.Distinct().Count() != req.InterestIds.Count)
            return BadRequest(new { message = "Duplicates" });

        var valid = await _db.Interests.Where(i => req.InterestIds.Contains(i.Id)).Select(i => i.Id).ToListAsync();
        if (valid.Count != req.InterestIds.Count) return BadRequest(new { message = "Unknown interest id" });

        var existing = await _db.UserInterests.Where(u => u.UserId == userId.Value).ToListAsync();
        _db.UserInterests.RemoveRange(existing);
        _db.UserInterests.AddRange(req.InterestIds.Select(id => new UserInterest { UserId = userId.Value, InterestId = id }));
        await _db.SaveChangesAsync();
        return Ok(new { message = "Saved", count = req.InterestIds.Count });
    }
}
