package com.scoreboard.app.network

import com.scoreboard.app.BuildConfig
import com.scoreboard.app.models.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    @GET("api/health")
    suspend fun getHealth(): HealthInfo

    @GET("api/sports")
    suspend fun getSports(): List<Sport>

    @GET("api/teams")
    suspend fun getTeams(
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<Team>

    @GET("api/players")
    suspend fun getPlayers(
        @Query("team_id") teamId: String? = null,
        @Query("level") level: String? = null
    ): List<Player>

    @GET("api/matches")
    suspend fun getMatches(
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<MatchItem>

    @GET("api/brackets")
    suspend fun getBrackets(
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<BracketItem>

    @GET("api/leaderboard")
    suspend fun getLeaderboard(
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<LeaderboardItem>

    @GET("api/version")
    suspend fun getVersion(): VersionInfo

    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): LoginResponse
}

object RetrofitClient {
    var baseUrl: String = BuildConfig.API_BASE_URL

    val instance: ApiService
        get() = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}
