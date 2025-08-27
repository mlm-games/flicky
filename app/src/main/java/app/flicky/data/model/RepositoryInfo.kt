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
            RepositoryInfo("microG", "https://microg.org/fdroid/repo", true),
            RepositoryInfo("NewPipe upstream", "https://archive.newpipe.net/fdroid/repo", true),

            RepositoryInfo("Guardian Project", "https://guardianproject.info/fdroid/repo", false),
            RepositoryInfo("Briar Project", "https://briarproject.org/fdroid/repo", false),

            RepositoryInfo("KDE Android Release", "https://cdn.kde.org/android/stable-releases/fdroid/repo", false),
            RepositoryInfo("KDE Android Nightly", "https://cdn.kde.org/android/nightly/fdroid/repo", false),

            RepositoryInfo("Kali NetHunter", "https://store.nethunter.com/repo", false),

            RepositoryInfo("Molly", "https://molly.im/fdroid/repo", false),
            RepositoryInfo("Molly FOSS", "https://molly.im/fdroid/foss/fdroid/repo", false),
            RepositoryInfo("Bitwarden", "https://mobileapp.bitwarden.com/fdroid/repo", false),
            RepositoryInfo("Cromite", "https://www.cromite.org/fdroid/repo", false),
            RepositoryInfo("IronFox", "https://fdroid.ironfoxoss.org/fdroid/repo", false)

        )
    }
}