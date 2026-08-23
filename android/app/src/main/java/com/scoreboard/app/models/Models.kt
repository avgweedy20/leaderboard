package com.scoreboard.app.models

import com.google.gson.annotations.SerializedName

data class Sport(
    val id: String,
    val name: String,
    val type: String,
    val level: String?,
    @SerializedName("point_win") val pointWin: Int,
    @SerializedName("point_draw") val pointDraw: Int,
    @SerializedName("point_loss") val pointLoss: Int,
    @SerializedName("is_lower_score_better") val isLowerScoreBetter: Boolean
)

data class TeamSportNested(
    @SerializedName("sport_id") val sportId: String
)

data class Team(
    val id: String,
    val name: String,
    @SerializedName("sport_id") val sportId: String?, // Backwards compatibility / single sport column if exists
    val level: String,
    @SerializedName("team_sports") val teamSports: List<TeamSportNested>? = emptyList()
)

val Team.sportIds: List<String>
    get() {
        val nested = teamSports?.map { it.sportId } ?: emptyList()
        if (nested.isEmpty() && sportId != null) {
            return listOf(sportId)
        }
        return nested
    }

data class Player(
    val id: String,
    val name: String,
    @SerializedName("team_id") val teamId: String,
    val grade: String?,
    val level: String
)

data class MatchItem(
    val id: String,
    @SerializedName("sport_id") val sportId: String,
    @SerializedName("team_a_id") val teamAId: String?,
    @SerializedName("team_b_id") val teamBId: String?,
    val level: String,
    val status: String,
    @SerializedName("round_info") val roundInfo: String?,
    @SerializedName("winner_team_id") val winnerTeamId: String?,
    @SerializedName("is_draw") val isDraw: Boolean,
    @SerializedName("score_summary") val scoreSummary: String?
)

data class LeaderboardItem(
    @SerializedName("team_id") val teamId: String,
    @SerializedName("team_name") val teamName: String,
    @SerializedName("sport_id") val sportId: String,
    @SerializedName("sport_name") val sportName: String,
    val level: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val points: Int
)

data class BracketItem(
    val id: String,
    @SerializedName("sport_id") val sportId: String,
    val level: String,
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
    @SerializedName("access_token") val accessToken: String
)
