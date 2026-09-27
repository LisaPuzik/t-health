package com.thealth.app.data

import com.thealth.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.MediaType.Companion.toMediaType

interface ApiService {
    @POST("api/auth/register") suspend fun register(@Body r: RegisterRequest): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body r: LoginRequest): AuthResponse
    @POST("api/auth/refresh") suspend fun refresh(@Body r: RefreshRequest): AuthResponse
    @GET("api/auth/me") suspend fun me(): MeResponse

    @GET("api/interests") suspend fun interests(): List<InterestDto>
    @PUT("api/interests/me") suspend fun setInterests(@Body r: SetInterestsRequest): MessageResponse

    @GET("api/crystal/me") suspend fun crystal(): CrystalDto

    @GET("api/crystal/pet") suspend fun pet(): PetDto
    @GET("api/health-metrics/summary") suspend fun summary(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): HealthSummary

    @POST("api/health-metrics/batch") suspend fun batch(@Body r: BatchRequest): BatchResponse

    @GET("api/health-metrics/streak") suspend fun streak(
        @Query("goal") goal: Int? = null
    ): StreakDto

    @GET("api/teams/leaderboard") suspend fun leaderboard(): List<TeamEntry>
    @GET("api/achievements/me") suspend fun myAchievements(): MyAchievements

    @GET("api/challenges") suspend fun challenges(): List<ChallengeDto>
    @GET("api/challenges/user/{id}") suspend fun userChallenges(@Path("id") id: Int): List<UserChallengeDto>
    @POST("api/challenges/{id}/join") suspend fun joinChallenge(@Path("id") id: Int, @Body r: JoinRequest): MessageResponse

    @GET("api/users") suspend fun users(): List<UserDto>

    @PUT("api/users/me") suspend fun updateMe(@Body r: UpdateNameRequest): UpdatedUser

    @GET("api/communities") suspend fun communities(): List<CommunityDto>
    @POST("api/communities") suspend fun createCommunity(@Body r: CreateCommunityRequest): CommunityDto
    @POST("api/communities/{id}/join") suspend fun joinCommunity(@Path("id") id: Int): MessageResponse
    @POST("api/communities/{id}/leave") suspend fun leaveCommunity(@Path("id") id: Int): MessageResponse

    @GET("api/recommendations") suspend fun recommendations(): RecoResponse

    @GET("api/recommendations/ai") suspend fun aiRecommendations(): AiRecoResponse

    @POST("api/workouts") suspend fun createWorkout(@Body r: CreateWorkoutRequest): WorkoutCreateResponse

    @POST("api/emotionentries") suspend fun createEmotion(@Body r: EmotionRequest): EmotionResponse

    @GET("api/workouts/user/{id}") suspend fun workouts(@Path("id") id: Int): List<WorkoutHistoryItem>

    @GET("api/emotionentries/user/{id}") suspend fun emotions(@Path("id") id: Int): List<MoodHistoryItem>

    @GET("api/feed") suspend fun feed(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): FeedPage

    @POST("api/feed") suspend fun createPost(@Body r: CreatePostRequest): CreatedPostDto

    @POST("api/feed/{id}/like") suspend fun toggleLike(@Path("id") id: Int): LikeResponse

    @GET("api/feed/{id}/comments") suspend fun comments(@Path("id") id: Int): CommentsResponse

    @POST("api/feed/{id}/comments") suspend fun addComment(
        @Path("id") id: Int,
        @Body r: CreateCommentRequest
    ): FeedCommentDto
}

// 401 -> бесшумный refresh одним полетом, повтор запроса
private class TokenAuthenticator(
    private val store: AuthStore,
    private val plain: ApiService
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val refresh = kotlinx.coroutines.runBlocking { store.refresh() } ?: return null
        val device = store.deviceId()
        val renewed = try {
            kotlinx.coroutines.runBlocking { plain.refresh(RefreshRequest(refresh, device)) }
        } catch (_: Exception) { return null }
        kotlinx.coroutines.runBlocking { store.setTokens(renewed.accessToken, renewed.refreshToken) }
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${renewed.accessToken}")
            .build()
    }

    private fun responseCount(r: Response): Int {
        var c = 1
        var p = r.priorResponse
        while (p != null) { c++; p = p.priorResponse }
        return c
    }
}

// Человеческий текст ошибки API: достаём русское message из тела,
// иначе — понятный фолбэк по коду. Никаких "HTTP 400" юзеру.
fun friendlyError(e: Exception): String {
    val he = e as? retrofit2.HttpException ?: return "Нет связи с сервером"
    return try {
        val body = he.response()?.errorBody()?.string().orEmpty()
        val msg = Regex(""""message"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?: Regex(""""[^"]+"\s*:\s*\[\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        when (he.code()) {
            401 -> msg ?: "Неверная почта или пароль"
            409 -> msg ?: "Такая почта уже зарегистрирована"
            else -> msg ?: "Что-то пошло не так (${he.code()})"
        }
    } catch (_: Exception) {
        "Нет связи с сервером"
    }
}

object ApiClient {    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun service(store: AuthStore): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val plain = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(logging).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)

        val authed = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = kotlinx.coroutines.runBlocking { store.access() }
                val req = if (token != null)
                    chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                else chain.request()
                chain.proceed(req)
            }
            .authenticator(TokenAuthenticator(store, plain))
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authed)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    // Без авторизации — для login/register
    fun public(store: AuthStore): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().addInterceptor(logging).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
