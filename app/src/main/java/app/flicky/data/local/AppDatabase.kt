package app.flicky.data.local

import androidx.room.*
import app.flicky.data.model.FDroidApp
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(entities = [FDroidApp::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}

class Converters {
    @TypeConverter fun listToString(list: List<String>) = Json.encodeToString(list)
    @TypeConverter fun stringToList(s: String): List<String> = try { Json.decodeFromString(s) } catch(_:Exception){ emptyList() }
}
