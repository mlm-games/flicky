package app.flicky.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReproducibleBuildInfo(
    val appid: String,
    val tags: Map<String, List<ReproducibleBuildEntry>> = emptyMap()
) {
    fun isReproducible(versionCode: Int): Boolean? {
        for ((_, entries) in tags) {
            for (entry in entries) {
                if (entry.version_code == versionCode) {
                    return entry.reproducible
                }
            }
        }
        return null
    }

    fun getEntry(versionCode: Int): ReproducibleBuildEntry? {
        for ((_, entries) in tags) {
            for (entry in entries) {
                if (entry.version_code == versionCode) {
                    return entry
                }
            }
        }
        return null
    }
}

@Serializable
data class ReproducibleBuildEntry(
    val appid: String,
    val version_code: Int,
    val version_name: String,
    val tag: String,
    val commit: String,
    val timestamp: Long,
    val reproducible: Boolean,
    val error: String?,
    val recipe: ReproducibleBuildRecipe? = null
)

@Serializable
data class ReproducibleBuildRecipe(
    val repository: String,
    val tag: String,
    val apk_pattern: String,
    val apk_url: String,
    val build: String
)
