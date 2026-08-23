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

    @GET("rest/v1/teams")
    suspend fun getTeams(
        @Query("select") select: String = "*,sports(name)",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<Team>

    @GET("rest/v1/players")
    suspend fun getPlayers(
        @Query("select") select: String = "*,teams(name)",
        @Query("team_id") teamId: String? = null,
        @Query("level") level: String? = null
    ): List<Player>

    @GET("rest/v1/matches")
    suspend fun getMatches(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
    ): List<MatchItem>

    @GET("rest/v1/tournament_brackets")
    suspend fun getBrackets(
        @Query("select") select: String = "*",
        @Query("sport_id") sportId: String? = null,
        @Query("level") level: String? = null
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
                            val request = chain.request().newBuilder()
                                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                                .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
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

    suspend fun getMatches(sportId: String? = null, level: String? = null): List<MatchItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getMatches(sportId = sFilter, level = lFilter)
    }

    suspend fun getBrackets(sportId: String? = null, level: String? = null): List<BracketItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getBrackets(sportId = sFilter, level = lFilter)
    }

    suspend fun getLeaderboard(sportId: String? = null, level: String? = null): List<LeaderboardItem> {
        val sFilter = if (!sportId.isNullOrEmpty()) "eq.$sportId" else null
        val lFilter = if (!level.isNullOrEmpty() && level != "ALL") "eq.$level" else null
        return service.getLeaderboard(sportId = sFilter, level = lFilter)
    }

    suspend fun login(email: String, pass: String): String {
        val res = service.loginWithPassword(mapOf("email" to email, "password" to pass))
        return res.accessToken
    }
}
