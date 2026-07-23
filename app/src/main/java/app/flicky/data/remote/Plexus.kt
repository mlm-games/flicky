package app.flicky.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlexusAppResponse(
    val data: PlexusApp,
)

@Serializable
data class PlexusApp(
    val name: String? = null,
    @SerialName("package") val packageName: String,
    val icon_url: String? = null,
    val updated_at: String? = null,
    val scores: PlexusScores? = null,
)

@Serializable
data class PlexusScores(
    val native: PlexusScore? = null,
    val micro_g: PlexusScore? = null,
)

@Serializable
data class PlexusScore(
    val numerator: Double = 0.0,
    val denominator: Int = 4,
    val total_count: Long = 0,
    val rating_type: String? = null,
) {
    val average: Double get() = if (denominator > 0) numerator else 0.0
    val badge: PlexusBadge get() = when {
        average >= 3.5 -> PlexusBadge.GOLD
        average >= 2.5 -> PlexusBadge.SILVER
        average >= 1.5 -> PlexusBadge.BRONZE
        average > 0 -> PlexusBadge.BROKEN
        else -> PlexusBadge.UNRATED
    }
    val display: String get() = if (total_count > 0) "%.1f / %d".format(average, denominator) else "—"
}

enum class PlexusBadge { GOLD, SILVER, BRONZE, BROKEN, UNRATED }
