package app.flicky.data.local

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.flicky.data.model.FDroidApp
import kotlinx.serialization.json.Json

@Database(
    entities = [FDroidApp::class],
    version = 2, // Not that imp, but to prevent CCLauncher style update setting corruption
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                /* givemesql? migrate me (ADD MIGRATIONS(name) to HERE) */
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