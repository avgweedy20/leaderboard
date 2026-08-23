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
        @Query("select") select: String = "*,team_sports(sport_id)",
        @Query("level") level: String? = null
    ): List<Team>

    @POST("rest/v1/teams")
    suspend fun createTeam(
        @Header("Prefer") prefer: String = "return=representation",
        @Body team: Map<String, @JvmSuppressWildcards Any>
    ): List<Team>

    @POST("rest/v1/team_sports")
    suspend fun createTeamSportsBulk(
        @Body teamSportsList: List<Map<String, @JvmSuppressWildcards Any>>
    )

    @DELETE("rest/v1/team_sports")
    suspend fun deleteTeamSports(
        @Query("team_id") teamIdFilter: String
    )

    @GET("rest/v1/players")
    suspend fun getPlayers(
        @Query("select") select: String = "*",
        @Query("team_id") teamId: String? = null,
        @Query("level") level: String? = null
    ): List<Player>

    @POST("rest/v1/players")
    suspend fun createPlayer(
        @Header("Prefer") prefer: String = "return=representation",
        @Body player: Map<String, @JvmSuppressWildcards Any>
    ): List<Player>

    @POST("rest/v1/players")
    suspend fun createPlayersBulk(
        @Header("Prefer") prefer: String = "return=representation",
        @Body playersList: List<Map<String, @JvmSuppressWildcards Any>>
    ): List<Player>

    @GET("rest/v1/matches")
    suspend fun getMatches(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<MatchItem>

    @POST("rest/v1/matches")
    suspend fun createMatch(
        @Header("Prefer") prefer: String = "return=representation",
        @Body match: Map<String, @JvmSuppressWildcards Any?>
    ): List<MatchItem>

    @PATCH("rest/v1/matches")
    suspend fun updateMatchScore(
        @Query("id") idFilter: String,
        @Body updateData: Map<String, @JvmSuppressWildcards Any?>
    )

    @GET("rest/v1/tournament_brackets")
    suspend fun getBrackets(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<BracketItem>

    @POST("rest/v1/tournament_brackets")
    suspend fun createBracket(
        @Header("Prefer") prefer: String = "return=representation",
        @Body bracket: Map<String, @JvmSuppressWildcards Any>
    ): List<BracketItem>

    @GET("rest/v1/leaderboard_view")
    suspend fun getLeaderboard(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null,
        @Query("order") order: String = "points.desc"
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

    suspend fun getSports(): List<Sport> {
        return service.getSports()
    }

    suspend fun createSport(
        name: String,
        type: String,
        pointWin: Int,
        pointDraw: Int,
        pointLoss: Int,
        isLowerBetter: Boolean
    ): Sport {
        val payload = mapOf<String, Any>(
            "name" to name,
            "type" to type,
            "level" to "ALL",
            "point_win" to pointWin,
            "point_draw" to pointDraw,
            "point_loss" to pointLoss,
            "is_lower_score_better" to isLowerBetter
        )
        val res = service.createSport(sport = payload)
        return res.first()
    }

    suspend fun getTeams(level: String? = null): List<Team> {
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getTeams(level = lFilter)
    }

    suspend fun createTeam(name: String, sportIds: List<String>, level: String): Team {
        val firstSportId = sportIds.firstOrNull()
        val payload = mutableMapOf<String, Any>(
            "name" to name,
            "level" to level
        )
        if (firstSportId != null) {
            payload["sport_id"] = firstSportId
        }
        val res = service.createTeam(team = payload)
        val createdTeam = res.first()

        if (sportIds.isNotEmpty()) {
            val list = sportIds.map { sportId ->
                mapOf<String, Any>(
                    "team_id" to createdTeam.id,
                    "sport_id" to sportId
                )
            }
            service.createTeamSportsBulk(list)
        }
        return createdTeam
    }

    suspend fun updateTeamSports(teamId: String, sportIds: List<String>) {
        try {
            service.deleteTeamSports(teamIdFilter = "eq.$teamId")
        } catch (_: Exception) {}

        if (sportIds.isNotEmpty()) {
            val list = sportIds.map { sportId ->
                mapOf<String, Any>(
                    "team_id" to teamId,
                    "sport_id" to sportId
                )
            }
            service.createTeamSportsBulk(list)
        }
    }

    suspend fun getPlayers(teamId: String? = null, level: String? = null): List<Player> {
        val tFilter = if (!teamId.isNullOrEmpty()) "eq.$teamId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getPlayers(teamId = tFilter, level = lFilter)
    }

    suspend fun createPlayer(name: String, teamId: String, grade: String, level: String): Player {
        val payload = mapOf<String, Any>(
            "name" to name,
            "team_id" to teamId,
            "grade" to grade,
            "level" to level
        )
        val res = service.createPlayer(player = payload)
        return res.first()
    }

    suspend fun createPlayersBulk(playersList: List<Map<String, Any>>): List<Player> {
        if (playersList.isEmpty()) return emptyList()
        return service.createPlayersBulk(playersList = playersList)
    }

    suspend fun getMatches(sportId: String? = null, level: String? = null): List<MatchItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getMatches(sportId = sFilter, level = lFilter)
    }

    suspend fun createMatch(
        sportId: String,
        teamAId: String,
        teamBId: String,
        level: String,
        roundInfo: String
    ): MatchItem {
        val payload = mapOf<String, Any?>(
            "sport_id" to sportId,
            "team_a_id" to teamAId,
            "team_b_id" to teamBId,
            "level" to level,
            "status" to "scheduled",
            "round_info" to roundInfo
        )
        val res = service.createMatch(match = payload)
        return res.first()
    }

    suspend fun updateMatchScore(
        matchId: String,
        winnerTeamId: String?,
        isDraw: Boolean,
        scoreSummary: String,
        status: String = "completed"
    ) {
        val payload = mapOf<String, Any?>(
            "status" to status,
            "winner_team_id" to winnerTeamId,
            "is_draw" to isDraw,
            "score_summary" to scoreSummary
        )
        service.updateMatchScore(idFilter = "eq.$matchId", updateData = payload)
    }

    suspend fun getBrackets(sportId: String? = null, level: String? = null): List<BracketItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getBrackets(sportId = sFilter, level = lFilter)
    }

    suspend fun createBracket(sportId: String, level: String, type: String): BracketItem {
        val allTeams = getTeams(level = level)
        val teams = allTeams.filter { it.sportIds.contains(sportId) }
        val rounds = mutableListOf<Map<String, Any>>()
        val generatedMatches = mutableListOf<MatchItem>()

        if (type == "single_elimination") {
            val pairs = mutableListOf<Map<String, String>>()
            for (i in teams.indices step 2) {
                val teamA = teams[i]
                val teamB = if (i + 1 < teams.size) teams[i + 1] else null
                pairs.add(
                    mapOf(
                        "team_a_name" to teamA.name,
                        "team_b_name" to (teamB?.name ?: "BYE")
                    )
                )
                if (teamB != null) {
                    val m = createMatch(
                        sportId = sportId,
                        teamAId = teamA.id,
                        teamBId = teamB.id,
                        level = level,
                        roundInfo = "Round 1"
                    )
                    generatedMatches.add(m)
                }
            }
            rounds.add(mapOf("round_name" to "Round 1", "pairs" to pairs))
        } else {
            for (i in teams.indices) {
                for (j in i + 1 until teams.size) {
                    val teamA = teams[i]
                    val teamB = teams[j]
                    val m = createMatch(
                        sportId = sportId,
                        teamAId = teamA.id,
                        teamBId = teamB.id,
                        level = level,
                        roundInfo = "Round Robin (${teamA.name} vs ${teamB.name})"
                    )
                    generatedMatches.add(m)
                }
            }
            rounds.add(mapOf("round_name" to "Round Robin Matches", "matches_count" to generatedMatches.size))
        }

        val structureJson = mapOf("rounds" to rounds, "matches" to generatedMatches)
        val payload = mapOf<String, Any>(
            "sport_id" to sportId,
            "level" to level,
            "type" to type,
            "structure_json" to structureJson
        )

        val res = service.createBracket(bracket = payload)
        return res.first()
    }

    suspend fun getLeaderboard(sportId: String? = null, level: String? = null): List<LeaderboardItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getLeaderboard(sportId = sFilter, level = lFilter)
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
