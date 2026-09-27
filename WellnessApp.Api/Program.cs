using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using WellnessApp.Api.AI;
using WellnessApp.Api.Auth;
using WellnessApp.Api.Data;
using WellnessApp.Api.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "JWT in header: Bearer {token}",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT"
    });
    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" }
            },
            Array.Empty<string>()
        }
    });
});

builder.Services.Configure<JwtSettings>(builder.Configuration.GetSection("Jwt"));
var jwt = builder.Configuration.GetSection("Jwt").Get<JwtSettings>()
    ?? throw new InvalidOperationException("Jwt section is missing in configuration");

if (jwt.Key.Length < 32)
    throw new InvalidOperationException("Jwt:Key must be at least 32 chars");

builder.Services.AddSingleton<ITokenService, TokenService>();
builder.Services.AddScoped<CrystalService>();
builder.Services.AddScoped<AiRecommendationService>();

// Gemini: ключ только из env/user-secrets, в репозитории пусто
builder.Services.Configure<GeminiSettings>(builder.Configuration.GetSection("Gemini"));
var geminiKey = Environment.GetEnvironmentVariable("GEMINI_API_KEY");
if (!string.IsNullOrWhiteSpace(geminiKey))
    builder.Services.PostConfigure<GeminiSettings>(s => s.ApiKey = geminiKey);

builder.Services.AddHttpClient("gemini", c =>
{
    c.BaseAddress = new Uri("https://generativelanguage.googleapis.com/");
    c.Timeout = TimeSpan.FromSeconds(30);
});

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(o =>
    {
        o.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwt.Issuer,
            ValidAudience = jwt.Audience,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.Key)),
            ClockSkew = TimeSpan.FromMinutes(1)
        };
    });

builder.Services.AddAuthorization();

// Mobile-web клиент ходит с другого origin (файл / телефон в Wi-Fi).
// DEV: открыто. Перед продом заменить на точный список origins.
builder.Services.AddCors(o => o.AddPolicy("MobileDev", p => p
    .AllowAnyOrigin()
    .AllowAnyHeader()
    .AllowAnyMethod()));

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(
        builder.Configuration.GetConnectionString("DefaultConnection")
    ));

var app = builder.Build();

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.MigrateAsync();
    await DbInitializer.SeedAsync(db);
}

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseCors("MobileDev");

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.MapGet("/api/health/db", async (AppDbContext db) =>
{
    var connected = await db.Database.CanConnectAsync();

    return Results.Ok(new
    {
        database = "PostgreSQL",
        connected
    });
}).AllowAnonymous();

app.Run();
