using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace WellnessApp.Api.Auth;

public static class ClaimsExtensions
{
    public static int? GetCurrentUserId(this ClaimsPrincipal user)
    {
        var id = user.FindFirstValue(ClaimTypes.NameIdentifier)
              ?? user.FindFirstValue(JwtRegisteredClaimNames.Sub);
        return int.TryParse(id, out var userId) ? userId : null;
    }
}
