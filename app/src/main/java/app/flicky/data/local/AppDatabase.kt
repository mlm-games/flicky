package app.flicky.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.flicky.data.model.FDroidApp
import kotlinx.serialization.json.Json

@Database(
    entities = [FDroidApp::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                /* kept intentionally empty (no-op migration retained for history) */
            }
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
