namespace WellnessApp.Api.Models;

public class Interest
{
    public int Id { get; set; }

    public string Name { get; set; } = string.Empty;

    public List<UserInterest> UserInterests { get; set; } = [];
}