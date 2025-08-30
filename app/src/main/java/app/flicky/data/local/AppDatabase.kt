package app.flicky.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.flicky.data.model.FDroidApp
import kotlinx.serialization.json.Json

@Database(
    entities = [FDroidApp::class, AppVariant::class, RepoConfig::class, RepositoryEntity::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun repoConfigDao(): RepoConfigDao
    abstract fun repositoryDao(): RepositoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op */ }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apps ADD COLUMN whatsNew TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_name` ON `apps` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_summary` ON `apps` (`summary`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_packageName` ON `apps` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_category` ON `apps` (`category`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apps ADD COLUMN isCompatible INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE apps ADD COLUMN repositoryUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_repositoryUrl` ON `apps` (`repositoryUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apps_isCompatible` ON `apps` (`isCompatible`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_variants (
                        packageName TEXT NOT NULL,
                        repositoryUrl TEXT NOT NULL,
                        repositoryName TEXT NOT NULL,
                        versionName TEXT NOT NULL,
                        versionCode INTEGER NOT NULL,
                        apkUrl TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        isCompatible INTEGER NOT NULL,
                        PRIMARY KEY(packageName, repositoryUrl, versionCode)
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_variants_packageName ON app_variants(packageName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_variants_repositoryUrl ON app_variants(repositoryUrl)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS repo_config (
                        baseUrl TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        rotateMirrors INTEGER NOT NULL DEFAULT 0,
                        includeOnion INTEGER NOT NULL DEFAULT 0,
                        strategy TEXT NOT NULL DEFAULT 'StickyLastGood',
                        trustMode TEXT NOT NULL DEFAULT 'HttpsOnly',
                        pins TEXT NOT NULL DEFAULT '',
                        caPem TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_repo_config_baseUrl ON repo_config(baseUrl)")

                // Helpful sort indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_name_nocase ON apps(name COLLATE NOCASE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_lastUpdated ON apps(lastUpdated DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_added ON apps(added DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_apps_size ON apps(size ASC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_variants_pkg_repo_vc ON app_variants(packageName, repositoryName, versionCode DESC)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS repositories (
                        baseUrl TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        webBaseUrl TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        fingerprint TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_repositories_baseUrl ON repositories(baseUrl)")

                // Ensure trust columns exist (if 7->8 was skipped or schema drift)
                fun columnExists(table: String, column: String): Boolean {
                    db.query("PRAGMA table_info($table)").use { c ->
                        while (c.moveToNext()) {
                            if (c.getString(1) == column) return true
                        }
                    }
                    return false
                }
                if (!columnExists("repo_config", "trustMode")) {
                    db.execSQL("ALTER TABLE repo_config ADD COLUMN trustMode TEXT NOT NULL DEFAULT 'HttpsOnly'")
                }
                if (!columnExists("repo_config", "pins")) {
                    db.execSQL("ALTER TABLE repo_config ADD COLUMN pins TEXT NOT NULL DEFAULT ''")
                }
                if (!columnExists("repo_config", "caPem")) {
                    db.execSQL("ALTER TABLE repo_config ADD COLUMN caPem TEXT NOT NULL DEFAULT ''")
                }
            }
        }
    }
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun listToString(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun stringToList(s: String): List<String> = try {
        json.decodeFromString(s)
    } catch(_: Exception) {
        emptyList()
    }
}