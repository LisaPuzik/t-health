using System.ComponentModel.DataAnnotations;

namespace WellnessApp.Api.Auth;

public class RegisterRequest
{
    [Required(ErrorMessage = "Укажи email")]
    [EmailAddress(ErrorMessage = "Похоже, в email опечатка")]
    [MaxLength(256)]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Придумай пароль")]
    [MinLength(6, ErrorMessage = "Пароль — минимум 6 символов")]
    [MaxLength(100)]
    public string Password { get; set; } = string.Empty;

    [Required(ErrorMessage = "Как тебя зовут?")]
    [MaxLength(100)]
    public string FirstName { get; set; } = string.Empty;

    [Required(ErrorMessage = "Укажи фамилию")]
    [MaxLength(100)]
    public string LastName { get; set; } = string.Empty;

    public int? TeamId { get; set; }

    [MaxLength(128)]
    public string? DeviceId { get; set; }
}

public class LoginRequest
{
    [Required(ErrorMessage = "Укажи email")]
    [EmailAddress(ErrorMessage = "Похоже, в email опечатка")]
    public string Email { get; set; } = string.Empty;

    [Required(ErrorMessage = "Введи пароль")]
    public string Password { get; set; } = string.Empty;

    [MaxLength(128)]
    public string? DeviceId { get; set; }
}

public class RefreshRequest
{
    [Required]
    public string RefreshToken { get; set; } = string.Empty;

    [MaxLength(128)]
    public string? DeviceId { get; set; }
}

public class AuthResponse
{
    public string AccessToken { get; set; } = string.Empty;
    public string RefreshToken { get; set; } = string.Empty;
    public DateTime ExpiresAt { get; set; }
    public int UserId { get; set; }
    public string Email { get; set; } = string.Empty;
}
