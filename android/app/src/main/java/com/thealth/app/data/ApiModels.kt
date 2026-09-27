package com.thealth.app.data

import kotlinx.serialization.Serializable

// DTO строго по контракту WellnessApp.Api (camelCase)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val teamId: Int? = null,
    val deviceId: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String? = null
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
    val deviceId: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val userId: Int,
    val email: String
)

@Serializable
data class MeResponse(
    val id: Int,
    val email: String,
    val firstName: String,
    val lastName: String,
    val totalPoints: Int,
    val dailyStepGoal: Int = 10000,
    val teamId: Int? = null,
    val team: String? = null
)

@Serializable
data class InterestDto(
    val id: Int,
    val name: String,
    val selected: Boolean = false
)

@Serializable
data class SetInterestsRequest(val interestIds: List<Int>)

@Serializable
data class CrystalDto(
    val level: Int,
    val xp: Int,
    val energy: Int,
    val xpForNextLevel: Int = 0,
    val lastFedAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class HealthSummary(
    val totalSteps: Int = 0,
    val totalDistance: Double = 0.0,
    val totalActiveEnergy: Double = 0.0,
    val totalSleepHours: Double = 0.0,
    val totalWorkoutMinutes: Int = 0,
    val avgHeartRate: Double? = null,
    val records: Int = 0
)

@Serializable
data class HealthMetricItem(
    val type: String,
    val value: Double,
    val unit: String,
    val recordedAt: String,
    val source: String,
    val externalId: String? = null
)

@Serializable
data class BatchRequest(val metrics: List<HealthMetricItem>)

@Serializable
data class CrystalInfo(val level: Int = 1, val xp: Int = 0, val energy: Int = 50)

@Serializable
data class ChallengeDone(val id: Int, val title: String, val points: Int)

@Serializable
data class BatchResponse(
    val inserted: Int = 0,
    val skipped: Int = 0,
    val stepsAdded: Int = 0,
    val completedChallenges: List<ChallengeDone> = emptyList(),
    val crystal: CrystalInfo? = null
)

@Serializable
data class MessageResponse(val message: String = "")

@Serializable
data class StreakDto(
    val goal: Int = 10000,
    val currentStreak: Int = 0,
    val todaySteps: Int = 0,
    val goalReachedToday: Boolean = false
)

@Serializable
data class TeamEntry(val id: Int, val name: String, val members: Int = 0, val totalPoints: Int = 0)

@Serializable
data class AchievementItem(
    val achievementId: Int = 0,
    val name: String,
    val description: String = "",
    val points: Int = 0,
    val earnedAt: String = ""
)

@Serializable
data class MyAchievements(val count: Int = 0, val items: List<AchievementItem> = emptyList())

@Serializable
data class ChallengeDto(
    val id: Int,
    val title: String,
    val description: String = "",
    val metricType: String = "",
    val targetValue: Int = 0,
    val points: Int = 0,
    val startDate: String = "",
    val endDate: String = "",
    val isActive: Boolean = true,
    val participants: Int = 0,
    val joined: Boolean = false
)

@Serializable
data class UserChallengeDto(
    val challengeId: Int,
    val title: String,
    val description: String = "",
    val metricType: String = "",
    val targetValue: Int = 0,
    val points: Int = 0,
    val currentValue: Int = 0,
    val isCompleted: Boolean = false,
    val joinedAt: String = "",
    val startDate: String = "",
    val endDate: String = ""
)

@Serializable
data class JoinRequest(val userId: Int)

@Serializable
data class UserDto(
    val id: Int,
    val email: String = "",
    val firstName: String,
    val lastName: String,
    val totalPoints: Int = 0,
    val team: String? = null
)

@Serializable
data class CommunityDto(
    val id: Int,
    val name: String,
    val description: String = "",
    val members: Int = 0,
    val joined: Boolean = false
)

@Serializable
data class CreateCommunityRequest(val name: String, val description: String = "")

@Serializable
data class RecoItem(
    val id: Int,
    val title: String,
    val description: String = "",
    val contentType: String = "",
    val interest: String? = null,
    val community: String? = null
)

@Serializable
data class RecoResponse(val mood: Int? = null, val items: List<RecoItem> = emptyList())

@Serializable
data class AiRecoItem(
    val title: String,
    val description: String = "",
    val contentType: String = "",
    val reason: String = ""
)

@Serializable
data class AiRecoResponse(
    val source: String = "rules",
    val mood: Int? = null,
    val items: List<AiRecoItem> = emptyList()
)

@Serializable
data class CreateWorkoutRequest(val userId: Int, val type: String, val durationMinutes: Int)

@Serializable
data class WorkoutInfo(val id: Int = 0, val type: String = "", val durationMinutes: Int = 0, val points: Int = 0)

@Serializable
data class EmotionRequest(val userId: Int, val mood: Int, val note: String? = null)

@Serializable
data class EmotionResponse(val id: Int = 0, val mood: Int = 0)

@Serializable
data class UpdateNameRequest(val firstName: String? = null, val lastName: String? = null, val dailyStepGoal: Int? = null)

@Serializable
data class UpdatedUser(val id: Int = 0, val email: String = "", val firstName: String = "", val lastName: String = "", val dailyStepGoal: Int = 10000)

@Serializable
data class WorkoutHistoryItem(
    val id: Int = 0,
    val type: String = "",
    val durationMinutes: Int = 0,
    val points: Int = 0,
    val createdAt: String = ""
)

@Serializable
data class MoodHistoryItem(
    val id: Int = 0,
    val mood: Int = 0,
    val note: String? = null,
    val createdAt: String = ""
)

@Serializable
data class WorkoutCreateResponse(
    val workout: WorkoutInfo = WorkoutInfo(),
    val userPoints: Int = 0,
    val crystal: CrystalInfo? = null
)

@Serializable
data class PetStage(
    val level: Int,
    val name: String,
    val icon: String,
    val state: String
)

@Serializable
data class PetStageInfo(
    val index: Int,
    val name: String,
    val icon: String,
    val stages: List<PetStage> = emptyList()
)

@Serializable
data class PetStats(
    val streakDays: Int = 0,
    val workouts30: Int = 0,
    val steps30: Int = 0,
    val lifetimeSteps: Int = 0
)

@Serializable
data class PetMilestone(val title: String, val done: Boolean = false, val detail: String = "")

@Serializable
data class PetDto(
    val level: Int = 1,
    val xp: Int = 0,
    val energy: Int = 50,
    val xpForNextLevel: Int = 300,
    val progressPct: Int = 0,
    val workoutsToGo: Int = 1,
    val stage: PetStageInfo = PetStageInfo(1, "", ""),
    val stats: PetStats = PetStats(),
    val milestones: List<PetMilestone> = emptyList()
)

// Лента новостей (api/feed). postType: General, Recipe, Workout, Achievement

@Serializable
data class FeedPostDto(
    val id: Int,
    val authorName: String = "",
    val authorTeam: String? = null,
    val content: String = "",
    val postType: String = "General",
    val imageEmoji: String? = null,
    val createdAt: String = "",
    val likesCount: Int = 0,
    val likedByMe: Boolean = false,
    val commentsCount: Int = 0
)

@Serializable
data class FeedPage(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val items: List<FeedPostDto> = emptyList()
)

@Serializable
data class CreatePostRequest(
    val content: String,
    val postType: String = "General",
    val imageEmoji: String? = null
)

@Serializable
data class CreatedPostDto(
    val id: Int = 0,
    val content: String = "",
    val postType: String = "",
    val imageEmoji: String? = null,
    val createdAt: String = ""
)

@Serializable
data class LikeResponse(val liked: Boolean = false, val likesCount: Int = 0)

@Serializable
data class FeedCommentDto(
    val id: Int = 0,
    val authorName: String = "",
    val content: String = "",
    val createdAt: String = ""
)

@Serializable
data class CommentsResponse(val items: List<FeedCommentDto> = emptyList())

@Serializable
data class CreateCommentRequest(val content: String)
