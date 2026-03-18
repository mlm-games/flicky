package app.flicky.data.local

import androidx.room.withTransaction
import app.flicky.data.model.RepositoryInfo

object DefaultRepositorySeeder {
    suspend fun seedIfEmpty(db: AppDatabase) {
        db.withTransaction {
            val repoDao = db.repositoryDao()
            val cfgDao = db.repoConfigDao()

            if (repoDao.getAll().isNotEmpty()) return@withTransaction

            RepositoryInfo.defaults().forEach { def ->
                val base = def.url.trim().removeSuffix("/")

                repoDao.upsert(
                    RepositoryEntity(
                        baseUrl = base,
                        name = def.name
                    )
                )

                cfgDao.insertIgnore(
                    RepoConfig(
                        baseUrl = base,
                        enabled = def.enabled,
                        rotateMirrors = base.equals("https://f-droid.org/repo", ignoreCase = true),
                        strategy = if (base.equals("https://f-droid.org/repo", ignoreCase = true)) {
                            "RoundRobin"
                        } else {
                            "StickyLastGood"
                        }
                    )
                )
            }
        }
    }
}
