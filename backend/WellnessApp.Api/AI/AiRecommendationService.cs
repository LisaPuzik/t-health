using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using WellnessApp.Api.Data;
using WellnessApp.Api.Models;

namespace WellnessApp.Api.AI;

public record AiItem(string Title, string Description, string ContentType, string Reason);

public record AiResult(string Source, int? Mood, List<AiItem> Items);

public class AiRecommendationService
{
    private static readonly HashSet<string> AllowedTypes = new(StringComparer.OrdinalIgnoreCase)
        { "Meditation", "Course", "Article", "Recipe", "Challenge" };

    private readonly AppDbContext _db;
    private readonly IHttpClientFactory _http;
    private readonly GeminiSettings _settings;
    private readonly ILogger<AiRecommendationService> _log;

    public AiRecommendationService(
        AppDbContext db, IHttpClientFactory http,
        IOptions<GeminiSettings> options, ILogger<AiRecommendationService> log)
    {
        _db = db;
        _http = http;
        _settings = options.Value;
        _log = log;
    }

    public async Task<AiResult> GetAsync(int userId, CancellationToken ct = default)
    {
        var now = DateTime.UtcNow;
        var cached = await _db.AiRecommendationCaches
            .FirstOrDefaultAsync(c => c.UserId == userId && c.ExpiresAt > now, ct);
        if (cached != null)
        {
            var items = Deserialize(cached.PayloadJson);
            var mood = await LatestMoodAsync(userId, ct);
            return new AiResult(cached.Source, mood, items);
        }

        var result = await TryGenerateAsync(userId, ct)
            ?? await FallbackAsync(userId, ct);

        _db.AiRecommendationCaches.RemoveRange(
            _db.AiRecommendationCaches.Where(c => c.UserId == userId));
        _db.AiRecommendationCaches.Add(new AiRecommendationCache
        {
            UserId = userId,
            PayloadJson = JsonSerializer.Serialize(result.Items),
            Source = result.Source,
            ExpiresAt = now.AddHours(_settings.CacheHours)
        });
        await _db.SaveChangesAsync(ct);
        return result;
    }

    // ---------- Gemini ----------

    private async Task<AiResult?> TryGenerateAsync(int userId, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(_settings.ApiKey))
            return null;

        try
        {
            var snapshot = await BuildSnapshotAsync(userId, ct);
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            cts.CancelAfter(TimeSpan.FromSeconds(120));

            var client = _http.CreateClient("gemini");
            var body = new
            {
                system_instruction = new { parts = new[] { new { text = SystemPrompt } } },
                contents = new[] { new { role = "user", parts = new[] { new { text = snapshot } } } },
                generationConfig = new
                {
                    responseMimeType = "application/json",
                    maxOutputTokens = 1000,
                    temperature = 0.7
                }
            };
            var models = new[] { _settings.Model }
                .Concat(_settings.FallbackModels.Where(m => !string.IsNullOrWhiteSpace(m)))
                .Distinct()
                .Take(3)
                .ToList();

            // Цепочка моделей: перегруз одной (429/503) — пробуем следующую
            foreach (var model in models)
            {
                var items = await TryModelAsync(client, model, body, cts.Token);
                if (items is { Count: > 0 })
                {
                    var mood = await LatestMoodAsync(userId, ct);
                    return new AiResult("ai", mood, items);
                }
            }
            return null;
        }
        catch
        {
            return null; // любая ошибка -> фолбэк на правила
        }
    }

    private async Task<List<AiItem>> TryModelAsync<T>(
        HttpClient client, string model, T body, CancellationToken ct)
    {
        // 429/503 — транзиентные всплески, один ретрай той же модели
        for (var attempt = 0; attempt < 2; attempt++)
        {
            using var req = new HttpRequestMessage(
                HttpMethod.Post, $"v1beta/models/{model}:generateContent");
            req.Headers.Add("x-goog-api-key", _settings.ApiKey);
            req.Content = JsonContent.Create(body);

            HttpResponseMessage resp;
            var sw = System.Diagnostics.Stopwatch.StartNew();
            try
            {
                resp = await client.SendAsync(req, ct);
            }
            catch (Exception e)
            {
                _log.LogWarning("Gemini {Model} send failed after {Ms}ms: {Err}",
                    model, sw.ElapsedMilliseconds, e.Message);
                return [];
            }

            using (resp)
            {
                if (resp.IsSuccessStatusCode)
                {
                    var doc = await resp.Content.ReadFromJsonAsync<JsonObject>(cancellationToken: ct);
                    var text = doc?["candidates"]?[0]?["content"]?["parts"]?[0]?["text"]?.GetValue<string>();
                    if (string.IsNullOrWhiteSpace(text))
                    {
                        _log.LogWarning("Gemini {Model} ok but empty text", model);
                        return [];
                    }
                    var items = Deserialize(StripFences(text)).Where(Valid).Take(4).ToList();
                    _log.LogInformation("Gemini {Model} ok, chars={Chars}, valid={Valid}",
                        model, text.Length, items.Count);
                    if (items.Count == 0)
                        _log.LogWarning("Gemini {Model} sample: {Sample}", model,
                            text.Length > 300 ? text[..300] : text);
                    return items;
                }

                // Не транзиентное (400/401/403/404) — следующую модель
                if (resp.StatusCode is not System.Net.HttpStatusCode.TooManyRequests
                    and not System.Net.HttpStatusCode.ServiceUnavailable)
                    return [];

                if (attempt == 0)
                    await Task.Delay(2000, ct);
            }
        }
        return [];
    }

    private const string SystemPrompt = """
Ты — тренер-компаньон wellness-приложения. По слепку пользователя верни СТРОГО JSON-массив из 2-4 рекомендаций, без пояснений вне массива.
Каждый элемент: {"title": string, "description": string (до 90 символов), "contentType": "Meditation"|"Course"|"Article"|"Recipe"|"Challenge", "reason": string (коротко, почему сейчас)}.
Логика:
- настроение 1-2 или спад за дни: Meditation (устал — медитацию);
- настроение 4-5 + активность: тренировка или вызов (в ресурсе — тренировку);
- уровень кристалла 1-2: мягкий старт (короткие Article/Course);
- уровень 5+: вызов + Challenge из наших челленджей;
- тип Challenge предлагай ТОЛЬКО из списка open_challenges (настоящие челленджи, в description укажи цель);
- учитывай интересы и сообщества пользователя, пиши по-русски, дружелюбно.
""";

    // Только агрегаты, без PII (нет email/имён)
    private async Task<string> BuildSnapshotAsync(int userId, CancellationToken ct)
    {
        var now = DateTime.UtcNow;
        var weekAgo = now.AddDays(-7);

        var moods = await _db.EmotionEntries.AsNoTracking()
            .Where(e => e.UserId == userId && e.CreatedAt >= weekAgo)
            .OrderByDescending(e => e.CreatedAt)
            .Select(e => e.Mood).Take(7).ToListAsync(ct);

        var interests = await _db.UserInterests.AsNoTracking()
            .Where(u => u.UserId == userId).Select(u => u.Interest.Name).ToListAsync(ct);
        var comms = await _db.CommunityMembers.AsNoTracking()
            .Where(m => m.UserId == userId).Select(m => m.Community.Name).ToListAsync(ct);

        var steps = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId && h.Type == "Steps" && h.RecordedAt >= weekAgo)
            .SumAsync(h => h.Value, ct);
        var workouts = await _db.Workouts.AsNoTracking()
            .Where(w => w.UserId == userId && w.CreatedAt >= weekAgo)
            .Select(w => w.DurationMinutes).ToListAsync(ct);
        var pet = await _db.CrystalPets.AsNoTracking()
            .FirstOrDefaultAsync(c => c.UserId == userId, ct);
        var streakDays = await StreakAsync(userId, ct);
        var challenges = await _db.ChallengeParticipants.AsNoTracking()
            .Where(cp => cp.UserId == userId && !cp.IsCompleted)
            .Select(cp => cp.Challenge.Title).ToListAsync(ct);
        var joinedIds = await _db.ChallengeParticipants.AsNoTracking()
            .Where(cp => cp.UserId == userId)
            .Select(cp => cp.ChallengeId).ToListAsync(ct);
        var openChallenges = await _db.Challenges.AsNoTracking()
            .Where(c => c.IsActive && c.EndDate >= now && !joinedIds.Contains(c.Id))
            .OrderBy(c => c.EndDate).Take(5)
            .Select(c => new { c.Title, c.MetricType, c.TargetValue })
            .ToListAsync(ct);

        return JsonSerializer.Serialize(new
        {
            mood_now = moods.FirstOrDefault(),
            mood_week = moods,
            interests, communities = comms,
            steps_7d = (int)steps,
            workouts_7d = workouts.Count,
            workout_minutes_7d = workouts.Sum(),
            crystal_level = pet?.Level ?? 1,
            streak_days = streakDays,
            active_challenges = challenges,
            open_challenges = openChallenges
        });
    }

    private async Task<int> StreakAsync(int userId, CancellationToken ct, int goal = 10000)
    {
        var today = DateTime.UtcNow.Date;
        var byDay = await _db.HealthMetrics.AsNoTracking()
            .Where(h => h.UserId == userId && h.Type == "Steps" && h.RecordedAt >= today.AddDays(-400))
            .GroupBy(h => h.RecordedAt.Date)
            .Select(g => new { day = g.Key, total = g.Sum(h => h.Value) })
            .ToDictionaryAsync(x => x.day, x => (int)x.total, ct);

        var streak = 0;
        var day = byDay.TryGetValue(today, out var ts) && ts >= goal ? today : today.AddDays(-1);
        while (byDay.TryGetValue(day, out var total) && total >= goal)
        {
            streak++;
            day = day.AddDays(-1);
        }
        return streak;
    }

    // ---------- Fallback: существующие правила ----------

    private async Task<AiResult> FallbackAsync(int userId, CancellationToken ct)
    {
        var mood = await LatestMoodAsync(userId, ct);
        var myInterests = await _db.UserInterests.AsNoTracking()
            .Where(u => u.UserId == userId).Select(u => u.InterestId).ToListAsync(ct);
        var myComms = await _db.CommunityMembers.AsNoTracking()
            .Where(m => m.UserId == userId).Select(m => m.CommunityId).ToListAsync(ct);

        var contents = await _db.WellnessContents.AsNoTracking().ToListAsync(ct);
        var items = contents
            .Select(w =>
            {
                int score = 0;
                var reason = "популярное";
                if (mood != null && mood >= w.MinMood && mood <= w.MaxMood) { score += 3; reason = $"под настроение {mood}"; }
                if (w.InterestId.HasValue && myInterests.Contains(w.InterestId.Value)) { score += 2; reason += " + твой интерес"; }
                if (w.CommunityId.HasValue && myComms.Contains(w.CommunityId.Value)) { score += 2; reason += " + твоё сообщество"; }
                return (w, score, reason);
            })
            .OrderByDescending(x => x.score).Take(4)
            .Select(x => new AiItem(x.w.Title, x.w.Description, x.w.ContentType, x.reason))
            .ToList();

        return new AiResult("rules", mood, items);
    }

    private async Task<int?> LatestMoodAsync(int userId, CancellationToken ct) =>
        await _db.EmotionEntries.AsNoTracking()
            .Where(e => e.UserId == userId)
            .OrderByDescending(e => e.CreatedAt)
            .Select(e => (int?)e.Mood)
            .FirstOrDefaultAsync(ct);

    private static bool Valid(AiItem i) =>
        !string.IsNullOrWhiteSpace(i.Title) && AllowedTypes.Contains(i.ContentType.Trim());

    private static List<AiItem> Deserialize(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<List<AiItem>>(json,
                new JsonSerializerOptions { PropertyNameCaseInsensitive = true }) ?? [];
        }
        catch { return []; }
    }

    private static string StripFences(string t)
    {
        t = t.Trim();
        if (t.StartsWith("```"))
        {
            var start = t.IndexOf('\n');
            var end = t.LastIndexOf("```");
            if (start >= 0 && end > start) return t.Substring(start + 1, end - start - 1).Trim();
        }
        return t;
    }
}
