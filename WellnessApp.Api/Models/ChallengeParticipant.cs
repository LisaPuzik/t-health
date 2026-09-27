namespace WellnessApp.Api.Models;

public class ChallengeParticipant
{
    public int Id { get; set; }

    public int ChallengeId { get; set; }

    public Challenge Challenge { get; set; } = null!;

    public int UserId { get; set; }

    public User User { get; set; } = null!;

    public int CurrentValue { get; set; }

    public bool IsCompleted { get; set; }

    public DateTime JoinedAt { get; set; } = DateTime.UtcNow;
}