package app.flicky.data.repository

import app.flicky.data.local.AppDao
import app.flicky.data.local.AppVariant
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdatesSnapshot(
    val updates: List<FDroidApp>,
    val suppressed: List<FDroidApp>,
    val upToDate: List<FDroidApp>,
    val candidates: Map<String, AppVariant>
)

object UpdatesCalculator {

    suspend fun compute(
        allApps: List<FDroidApp>,
        installedDetails: List<InstalledAppsRepository.InstalledDetailed>,
        appDao: AppDao,
        prefs: Map<String, AppUpdatePreference>,
        preferredRepo: PreferredRepo,
        ignoreUnstable: Boolean
    ): UpdatesSnapshot {
        val installedMap = installedDetails.associateBy { it.packageName }
        val installedFDroidApps = allApps.filter { installedMap.containsKey(it.packageName) }

        val allVariantsByPackage = withContext(Dispatchers.IO) {
            appDao.variantsForPackages(installedFDroidApps.map { it.packageName })
        }.groupBy { it.packageName }

        val candidates = mutableMapOf<String, AppVariant>()
        val latestCompatByPkg = installedFDroidApps.associate { app ->
            val pref = prefs[app.packageName] ?: AppUpdatePreference()
            val variantsForApp = allVariantsByPackage[app.packageName] ?: emptyList()
            val effectiveIgnoreUnstable = pref.ignoreUnstable ?: ignoreUnstable
            val chosen = VariantSelector.pickCompatible(
                variants = variantsForApp,
                preferred = preferredRepo,
                preferredRepoUrl = pref.preferredRepoUrl,
                strict = pref.lockToRepo,
                ignoreUnstable = effectiveIgnoreUnstable
            )
            if (chosen != null) {
                candidates[app.packageName] = chosen
            }
            app.packageName to (chosen?.versionCode?.toLong() ?: 0L)
        }

        val (updates, suppressed, upToDate) =
            partition(installedFDroidApps, installedMap, latestCompatByPkg, prefs, candidates)

        return UpdatesSnapshot(
            updates = updates,
            suppressed = suppressed,
            upToDate = upToDate,
            candidates = candidates
        )
    }

    private fun partition(
        installedApps: List<FDroidApp>,
        installedMap: Map<String, InstalledAppsRepository.InstalledDetailed>,
        latestCompatMap: Map<String, Long>,
        prefsMap: Map<String, AppUpdatePreference>,
        candidatesMap: Map<String, AppVariant>
    ): Triple<List<FDroidApp>, List<FDroidApp>, List<FDroidApp>> {
        val updates = mutableListOf<FDroidApp>()
        val suppressed = mutableListOf<FDroidApp>()
        val upToDate = mutableListOf<FDroidApp>()

        for (app in installedApps) {
            val currentVersion = installedMap[app.packageName]?.versionCode ?: 0L
            val latestVersion = latestCompatMap[app.packageName] ?: 0L

            val pref = prefsMap[app.packageName] ?: AppUpdatePreference()
            val candidateVersion = candidatesMap[app.packageName]?.versionCode?.toLong() ?: 0L
            val hasUpdate = latestVersion > currentVersion && candidateVersion == latestVersion
            if (hasUpdate) {
                val isIgnored = pref.ignoreUpdates ||
                        (pref.ignoreVersionCode != 0L && latestVersion <= pref.ignoreVersionCode)
                if (isIgnored) {
                    suppressed.add(app)
                } else {
                    updates.add(app)
                }
            } else {
                upToDate.add(app)
            }
        }
        return Triple(
            updates.sortedByDescending { it.lastUpdated },
            suppressed.sortedByDescending { it.lastUpdated },
            upToDate
        )
    }
}
