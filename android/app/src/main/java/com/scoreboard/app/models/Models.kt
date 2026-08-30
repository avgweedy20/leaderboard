package com.scoreboard.app.models

import com.google.gson.annotations.SerializedName

data class House(
    val id: String,
    val name: String,
    @SerializedName("color_hex") val colorHex: String,
    @SerializedName("short_code") val shortCode: String
)

data class Sport(
    val id: String,
    val name: String,
    val type: String,
    val level: String?,
    @SerializedName("point_win") val pointWin: Int = 0,
    @SerializedName("point_draw") val pointDraw: Int = 0,
    @SerializedName("point_loss") val pointLoss: Int = 0,
    @SerializedName("is_lower_score_better") val isLowerScoreBetter: Boolean = false
)

data class Team(
    val id: String,
    val name: String,
    @SerializedName("house_id") val houseId: String?,
    val gender: String?,
    @SerializedName("squad_label") val squadLabel: String?,
    @SerializedName("sport_id") val sportId: String?,
    val level: String?
)

data class Player(
    val id: String,
    val name: String,
    @SerializedName("team_id") val teamId: String?,
    @SerializedName("roll_number") val rollNumber: String?,
    val grade: String?,
    val section: String?,
    val gender: String?,
    val level: String?
)

data class MatchItem(
    val id: String,
    @SerializedName("sport_id") val sportId: String,
    @SerializedName("team_a_id") val teamAId: String?,
    @SerializedName("team_b_id") val teamBId: String?,
    val gender: String?,
    val stage: String?,
    val level: String?,
    val status: String?,
    @SerializedName("round_info") val roundInfo: String?,
    @SerializedName("winner_team_id") val winnerTeamId: String?,
    @SerializedName("is_draw") val isDraw: Boolean,
    @SerializedName("score_team_a") val scoreTeamA: Int = 0,
    @SerializedName("score_team_b") val scoreTeamB: Int = 0,
    @SerializedName("score_difference") val scoreDifference: Int = 0,
    @SerializedName("score_summary") val scoreSummary: String?
)

data class LeaderboardItem(
    @SerializedName("team_id") val teamId: String,
    @SerializedName("team_name") val teamName: String,
    @SerializedName("house_id") val houseId: String?,
    @SerializedName("house_name") val houseName: String?,
    @SerializedName("house_color") val houseColor: String?,
    @SerializedName("house_short_code") val houseShortCode: String?,
    val gender: String?,
    @SerializedName("squad_label") val squadLabel: String?,
    @SerializedName("sport_id") val sportId: String,
    @SerializedName("sport_name") val sportName: String,
    @SerializedName("sport_type") val sportType: String?,
    val level: String?,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    @SerializedName("score_difference") val scoreDifference: Int = 0,
    val points: Int,
    val rank: Int = 1
)

data class HouseOverallStanding(
    @SerializedName("house_id") val houseId: String,
    @SerializedName("house_name") val houseName: String,
    @SerializedName("color_hex") val colorHex: String,
    @SerializedName("short_code") val shortCode: String,
    @SerializedName("total_squads") val totalSquads: Int = 0,
    @SerializedName("matches_played") val matchesPlayed: Int = 0,
    @SerializedName("total_wins") val totalWins: Int = 0,
    @SerializedName("total_draws") val totalDraws: Int = 0,
    @SerializedName("total_losses") val totalLosses: Int = 0,
    @SerializedName("total_score_difference") val totalScoreDifference: Int = 0,
    @SerializedName("total_points") val totalPoints: Int = 0,
    val rank: Int = 1
)

data class BracketItem(
    val id: String,
    @SerializedName("sport_id") val sportId: String,
    val level: String?,
    val type: String
)

data class VersionInfo(
    @SerializedName("version_code") val versionCode: Int,
    @SerializedName("version_name") val versionName: String,
    @SerializedName("min_sdk") val minSdk: Int,
    @SerializedName("apk_url") val apkUrl: String,
    @SerializedName("release_notes") val releaseNotes: String,
    val mandatory: Boolean
)

data class HealthInfo(
    val status: String,
    @SerializedName("supabase_connected") val supabaseConnected: Boolean,
    val mode: String
)

data class LoginResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: UserInfo?
)

data class UserInfo(
    val id: String,
    val email: String,
    val role: String? = null
)

data class AdminMe(
    val email: String,
    val role: String
)

data class AdminAccount(
    val email: String,
    val role: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class AdminListResponse(
    val admins: List<AdminAccount> = emptyList()
)

data class AuditEntry(
    val id: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val action: String? = null,
    @SerializedName("actor_email") val actorEmail: String? = null,
    @SerializedName("target_email") val targetEmail: String? = null,
    val details: String? = null,
    @SerializedName("ip_address") val ipAddress: String? = null
)

data class AuditLogResponse(
    val entries: List<AuditEntry> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0
)
