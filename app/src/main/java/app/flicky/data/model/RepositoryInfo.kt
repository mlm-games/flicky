package app.flicky.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RepositoryInfo(
    val name: String,
    val url: String,
    val enabled: Boolean = true
) {
    companion object {
        fun defaults() = listOf(
            RepositoryInfo("F-Droid", "https://f-droid.org/repo", true),
            RepositoryInfo("IzzyOnDroid", "https://apt.izzysoft.de/fdroid/repo", true),
            RepositoryInfo("F-Droid Archive", "https://f-droid.org/archive", false),
        )
    }
}