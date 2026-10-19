package app.flicky.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.flicky.data.model.FDroidApp
import kotlinx.serialization.json.Json

@Database(
    entities = [FDroidApp::class, AppVariant::class, RepoConfig::class, RepositoryEntity::class],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun repoConfigDao(): RepoConfigDao
    abstract fun repositoryDao(): RepositoryDao

    companion object {
        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE repo_config ADD COLUMN basicAuthUser TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE repo_config ADD COLUMN basicAuthPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE repo_config ADD COLUMN customMirrors TEXT NOT NULL DEFAULT '[]'")
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
