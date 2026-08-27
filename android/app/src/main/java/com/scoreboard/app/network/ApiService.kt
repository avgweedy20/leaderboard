package com.scoreboard.app.network

import com.scoreboard.app.BuildConfig
import com.scoreboard.app.models.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface SupabaseRestService {
    @GET("rest/v1/houses")
    suspend fun getHouses(
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): List<House>

    @GET("rest/v1/sports")
    suspend fun getSports(
        @Query("select") select: String = "*"
    ): List<Sport>

    @POST("rest/v1/sports")
    suspend fun createSport(
        @Header("Prefer") prefer: String = "return=representation",
        @Body sport: Map<String, @JvmSuppressWildcards Any>
    ): List<Sport>

    @GET("rest/v1/teams")
    suspend fun getTeams(
        @Query("select") select: String = "*,houses(*),sports(*)",
        @Query("sport_id") sportId: String? = null,
        @Query("house_id") houseId: String? = null,
        @Query("gender") gender: String? = null
    ): List<Team>

    @GET("rest/v1/players")
    suspend fun getPlayers(
        @Query("select") select: String = "*,teams(*)",
        @Query("team_id") teamId: String? = null
    ): List<Player>

    @GET("rest/v1/matches")
    suspend fun getMatches(
        @Query("select") select: String = "*,sports(*)",
        @Query("sport_id") sportId: String? = null,
        @Query("gender") gender: String? = null,
        @Query("stage") stage: String? = null
    ): List<MatchItem>

    @PATCH("rest/v1/matches")
    suspend fun updateMatchScore(
        @Query("id") idFilter: String,
        @Body updateData: Map<String, @JvmSuppressWildcards Any?>
    )

    @GET("rest/v1/house_overall_standings")
    suspend fun getHouseOverallStandings(
        @Query("select") select: String = "*",
        @Query("order") order: String = "rank.asc"
    ): List<HouseOverallStanding>

    @GET("rest/v1/leaderboard_view")
    suspend fun getLeaderboard(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("gender") gender: String? = null,
        @Query("order") order: String = "rank.asc"
    ): List<LeaderboardItem>

    @POST("auth/v1/token?grant_type=password")
    suspend fun loginWithPassword(
        @Body body: Map<String, String>
    ): SupabaseAuthResponse
}

data class SupabaseAuthResponse(
    @com.google.gson.annotations.SerializedName("access_token") val accessToken: String,
    @com.google.gson.annotations.SerializedName("token_type") val tokenType: String?
)

object SupabaseRepository {
    var adminAuthToken: String? = null

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private var cachedService: SupabaseRestService? = null

    private val service: SupabaseRestService
        get() {
            if (!isConfigured) {
                throw IllegalStateException("Database Connection Error: Missing credentials or network failure")
            }
            if (cachedService == null) {
                val baseUrl = if (BuildConfig.SUPABASE_URL.endsWith("/")) BuildConfig.SUPABASE_URL else "${BuildConfig.SUPABASE_URL}/"
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(object : Interceptor {
                        override fun intercept(chain: Interceptor.Chain): Response {
                            val token = adminAuthToken ?: BuildConfig.SUPABASE_ANON_KEY
                            val request = chain.request().newBuilder()
                                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            return chain.proceed(request)
                        }
                    })
                    .build()

                cachedService = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SupabaseRestService::class.java)
            }
            return cachedService!!
        }

    suspend fun getHouses(): List<House> = service.getHouses()

    suspend fun getSports(): List<Sport> = service.getSports()

    suspend fun getTeams(sportId: String? = null, houseId: String? = null, gender: String? = null): List<Team> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val hFilter = if (!houseId.isNullOrEmpty()) "eq.$houseId" else null
        val gFilter = if (!gender.isNullOrEmpty()) "eq.$gender" else null
        return service.getTeams(sportId = sFilter, houseId = hFilter, gender = gFilter)
    }

    suspend fun getPlayers(teamId: String? = null): List<Player> {
        val tFilter = if (!teamId.isNullOrEmpty()) "eq.$teamId" else null
        return service.getPlayers(teamId = tFilter)
    }

    suspend fun getMatches(sportId: String? = null, gender: String? = null, stage: String? = null): List<MatchItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val gFilter = if (!gender.isNullOrEmpty()) "eq.$gender" else null
        val stFilter = if (!stage.isNullOrEmpty()) "eq.$stage" else null
        return service.getMatches(sportId = sFilter, gender = gFilter, stage = stFilter)
    }

    suspend fun getHouseOverallStandings(): List<HouseOverallStanding> = service.getHouseOverallStandings()

    suspend fun getLeaderboard(sportId: String? = null, gender: String? = null): List<LeaderboardItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val gFilter = if (!gender.isNullOrEmpty()) "eq.$gender" else null
        return service.getLeaderboard(sportId = sFilter, gender = gFilter)
    }

    suspend fun updateMatchScore(
        matchId: String,
        winnerTeamId: String?,
        isDraw: Boolean,
        scoreTeamA: Int,
        scoreTeamB: Int,
        scoreSummary: String,
        status: String = "completed"
    ) {
        val diff = kotlin.math.abs(scoreTeamA - scoreTeamB)
        val payload = mapOf<String, Any?>(
            "status" to status,
            "winner_team_id" to winnerTeamId,
            "is_draw" to isDraw,
            "score_team_a" to scoreTeamA,
            "score_team_b" to scoreTeamB,
            "score_difference" to diff,
            "score_summary" to scoreSummary
        )
        service.updateMatchScore(idFilter = "eq.$matchId", updateData = payload)
    }

    suspend fun login(email: String, pass: String): String {
        val res = service.loginWithPassword(mapOf("email" to email, "password" to pass))
        adminAuthToken = res.accessToken
        cachedService = null
        return res.accessToken
    }

    fun logout() {
        adminAuthToken = null
        cachedService = null
    }
}
