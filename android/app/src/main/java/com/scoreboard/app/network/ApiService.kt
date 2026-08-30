package com.scoreboard.app.network

import com.google.gson.Gson
import com.scoreboard.app.BuildConfig
import com.scoreboard.app.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException

data class ApiErrorBody(val error: String?)

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

interface FlaskApiService {
    @GET("api/health")
    suspend fun getHealth(): HealthInfo

    @GET("api/version")
    suspend fun getVersion(): VersionInfo

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): LoginResponse

    @GET("api/auth/me")
    suspend fun getMe(): AdminMe

    @POST("api/auth/logout")
    suspend fun logout(): Map<String, Any?>

    @GET("api/houses")
    suspend fun getHouses(): List<House>

    @GET("api/sports")
    suspend fun getSports(): List<Sport>

    @GET("api/teams")
    suspend fun getTeams(
        @Query("sport_id") sportId: String? = null,
        @Query("house_id") houseId: String? = null,
        @Query("gender") gender: String? = null
    ): List<Team>

    @POST("api/teams")
    suspend fun createTeam(@Body body: Map<String, @JvmSuppressWildcards Any?>): Team

    @PUT("api/teams/{id}")
    suspend fun updateTeam(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Team

    @DELETE("api/teams/{id}")
    suspend fun deleteTeam(@Path("id") id: String): Map<String, Any?>

    @GET("api/players")
    suspend fun getPlayers(@Query("team_id") teamId: String? = null): List<Player>

    @POST("api/players")
    suspend fun createPlayer(@Body body: Map<String, @JvmSuppressWildcards Any?>): Player

    @PUT("api/players/{id}")
    suspend fun updatePlayer(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): Player

    @DELETE("api/players/{id}")
    suspend fun deletePlayer(@Path("id") id: String): Map<String, Any?>

    @POST("api/players/bulk")
    suspend fun bulkUpsertPlayers(@Body items: List<Map<String, @JvmSuppressWildcards Any?>>): Map<String, Any?>

    @POST("api/players/bulk-delete")
    suspend fun bulkDeletePlayers(@Body body: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any?>

    @GET("api/matches")
    suspend fun getMatches(
        @Query("sport_id") sportId: String? = null,
        @Query("gender") gender: String? = null,
        @Query("stage") stage: String? = null
    ): List<MatchItem>

    @POST("api/matches")
    suspend fun createMatch(@Body body: Map<String, @JvmSuppressWildcards Any?>): MatchItem

    @PUT("api/matches/{id}")
    suspend fun updateMatch(@Path("id") id: String, @Body body: Map<String, @JvmSuppressWildcards Any?>): MatchItem

    @DELETE("api/matches/{id}")
    suspend fun deleteMatch(@Path("id") id: String): Map<String, Any?>

    @GET("api/leaderboard/overall")
    suspend fun getOverallStandings(@Query("gender") gender: String? = null): List<HouseOverallStanding>

    @GET("api/leaderboard")
    suspend fun getLeaderboard(
        @Query("sport_id") sportId: String? = null,
        @Query("gender") gender: String? = null
    ): List<LeaderboardItem>

    @GET("api/leaderboard/qualifiers")
    suspend fun getQualifiers(): List<LeaderboardItem>

    @GET("api/admin/list")
    suspend fun getAdmins(): AdminListResponse

    @POST("api/admin/add")
    suspend fun addAdmin(@Body body: Map<String, String>): Map<String, Any?>

    @POST("api/admin/remove")
    suspend fun removeAdmin(@Body body: Map<String, String>): Map<String, Any?>

    @POST("api/admin/reset-password")
    suspend fun resetAdminPassword(@Body body: Map<String, String>): Map<String, Any?>

    @GET("api/admin/log")
    suspend fun getAuditLog(
        @Query("action") action: String? = null,
        @Query("actor") actor: String? = null,
        @Query("target") target: String? = null,
        @Query("details") details: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): AuditLogResponse
}

object ApiRepository {
    var adminToken: String? = null
        private set
    var adminExpiresAtMillis: Long = 0L
        private set
    var adminRole: String? = null
        private set

    val isAuthenticated: Boolean
        get() = !adminToken.isNullOrBlank() && adminExpiresAtMillis > System.currentTimeMillis()

    private val gson = Gson()

    private val baseUrl: String
        get() {
            val url = BuildConfig.API_BASE_URL.trim()
            return if (url.endsWith("/")) url else "$url/"
        }

    private val okHttpClient: OkHttpClient by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
            adminToken?.takeIf { it.isNotBlank() }?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val service: FlaskApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlaskApiService::class.java)
    }

    private fun apiErrorBody(e: retrofit2.HttpException): String? {
        return try {
            val body = e.response()?.errorBody()?.string()
            if (body.isNullOrBlank()) null
            else gson.fromJson(body, ApiErrorBody::class.java)?.error ?: body
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        try {
            return withContext(Dispatchers.IO) { block() }
        } catch (e: retrofit2.HttpException) {
            val msg = apiErrorBody(e) ?: "Request failed (HTTP ${e.code()})"
            throw ApiException(msg, e.code())
        } catch (e: IOException) {
            throw ApiException("Database Connection Error: Cannot reach the API server at ${BuildConfig.API_BASE_URL}. Check that the backend is running.")
        }
    }

    fun restoreSession(token: String, expiresAtMillis: Long, role: String? = null) {
        adminToken = token
        adminExpiresAtMillis = expiresAtMillis
        adminRole = role ?: adminRole
    }

    fun setSession(token: String, expiresAtMillis: Long, role: String? = null) {
        adminToken = token
        adminExpiresAtMillis = expiresAtMillis
        adminRole = role ?: adminRole
    }

    fun logout() {
        adminToken = null
        adminExpiresAtMillis = 0L
        adminRole = null
    }

    suspend fun getHealth(): HealthInfo = apiCall { service.getHealth() }

    suspend fun login(email: String, password: String): LoginResponse = apiCall {
        service.login(mapOf("email" to email, "password" to password))
    }

    suspend fun getMe(): AdminMe = apiCall { service.getMe() }

    suspend fun logout() = apiCall { service.logout() }

    suspend fun getHouses(): List<House> = apiCall { service.getHouses() }

    suspend fun getSports(): List<Sport> = apiCall { service.getSports() }

    suspend fun getTeams(sportId: String? = null, houseId: String? = null, gender: String? = null): List<Team> =
        apiCall { service.getTeams(sportId, houseId, gender) }

    suspend fun createTeam(name: String, houseId: String, sportId: String, gender: String, squadLabel: String?): Team =
        apiCall {
            service.createTeam(
                mapOf(
                    "name" to name,
                    "house_id" to houseId,
                    "sport_id" to sportId,
                    "gender" to gender,
                    "squad_label" to squadLabel
                )
            )
        }

    suspend fun updateTeam(
        id: String,
        name: String,
        houseId: String,
        sportId: String,
        gender: String,
        squadLabel: String?
    ): Team = apiCall {
        service.updateTeam(
            id,
            mapOf(
                "name" to name,
                "house_id" to houseId,
                "sport_id" to sportId,
                "gender" to gender,
                "squad_label" to squadLabel
            )
        )
    }

    suspend fun deleteTeam(id: String) = apiCall { service.deleteTeam(id) }

    suspend fun getPlayers(teamId: String? = null): List<Player> =
        apiCall { service.getPlayers(teamId) }

    suspend fun createPlayer(
        name: String,
        teamId: String?,
        rollNumber: String?,
        grade: String?,
        section: String?,
        gender: String?
    ): Player = apiCall {
        service.createPlayer(
            mapOf(
                "name" to name,
                "team_id" to teamId,
                "roll_number" to rollNumber,
                "grade" to grade,
                "section" to section,
                "gender" to gender
            )
        )
    }

    suspend fun updatePlayer(
        id: String,
        name: String,
        teamId: String?,
        rollNumber: String?,
        grade: String?,
        section: String?,
        gender: String?
    ): Player = apiCall {
        service.updatePlayer(
            id,
            mapOf(
                "name" to name,
                "team_id" to teamId,
                "roll_number" to rollNumber,
                "grade" to grade,
                "section" to section,
                "gender" to gender
            )
        )
    }

    suspend fun deletePlayer(id: String) = apiCall { service.deletePlayer(id) }

    suspend fun bulkDeletePlayers(ids: List<String>) = apiCall { service.bulkDeletePlayers(mapOf("player_ids" to ids)) }

    suspend fun getMatches(sportId: String? = null, gender: String? = null, stage: String? = null): List<MatchItem> =
        apiCall { service.getMatches(sportId, gender, stage) }

    suspend fun createMatch(
        sportId: String,
        gender: String,
        teamAId: String?,
        teamBId: String?,
        stage: String?,
        level: String?,
        roundInfo: String?
    ): MatchItem = apiCall {
        service.createMatch(
            mapOf(
                "sport_id" to sportId,
                "gender" to gender,
                "team_a_id" to teamAId,
                "team_b_id" to teamBId,
                "stage" to stage,
                "level" to level,
                "round_info" to roundInfo
            )
        )
    }

    suspend fun updateMatch(
        id: String,
        teamAId: String?,
        teamBId: String?,
        stage: String?,
        roundInfo: String?,
        winnerTeamId: String?,
        isDraw: Boolean,
        scoreTeamA: Int,
        scoreTeamB: Int,
        scoreSummary: String?,
        status: String?
    ): MatchItem = apiCall {
        val payload = mutableMapOf<String, Any?>()
        teamAId?.let { payload["team_a_id"] = it }
        teamBId?.let { payload["team_b_id"] = it }
        stage?.let { payload["stage"] = it }
        roundInfo?.let { payload["round_info"] = it }
        winnerTeamId?.let { payload["winner_team_id"] = it }
        payload["is_draw"] = isDraw
        payload["score_team_a"] = scoreTeamA
        payload["score_team_b"] = scoreTeamB
        payload["score_summary"] = scoreSummary
        status?.let { payload["status"] = it }
        service.updateMatch(id, payload)
    }

    suspend fun deleteMatch(id: String) = apiCall { service.deleteMatch(id) }

    suspend fun getOverallStandings(gender: String? = null): List<HouseOverallStanding> =
        apiCall { service.getOverallStandings(gender) }

    suspend fun getLeaderboard(sportId: String? = null, gender: String? = null): List<LeaderboardItem> =
        apiCall { service.getLeaderboard(sportId, gender) }

    suspend fun getQualifiers(): List<LeaderboardItem> = apiCall { service.getQualifiers() }

    suspend fun getAdmins(): List<AdminAccount> = apiCall { service.getAdmins().admins }

    suspend fun addAdmin(email: String, password: String, role: String) = apiCall {
        service.addAdmin(mapOf("email" to email, "password" to password, "role" to role))
    }

    suspend fun removeAdmin(email: String) = apiCall { service.removeAdmin(mapOf("email" to email)) }

    suspend fun resetAdminPassword(email: String, password: String) = apiCall {
        service.resetAdminPassword(mapOf("email" to email, "password" to password))
    }

    suspend fun getAuditLog(offset: Int = 0, limit: Int = 50): AuditLogResponse = apiCall {
        service.getAuditLog(limit = limit, offset = offset)
    }
}