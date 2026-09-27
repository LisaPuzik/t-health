namespace WellnessApp.Api.AI;

public class GeminiSettings
{
    public string ApiKey { get; set; } = string.Empty;
    public string Model { get; set; } = "gemini-3.5-flash";
    public List<string> FallbackModels { get; set; } = ["gemini-flash-latest", "gemini-3.5-flash-lite"];
    public int CacheHours { get; set; } = 12;
    public int TimeoutSeconds { get; set; } = 15;
}
