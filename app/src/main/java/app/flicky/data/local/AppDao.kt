package app.flicky.data.local

import androidx.room.*
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM apps")
    fun observeAll(): Flow<List<FDroidApp>>

    @Query("SELECT * FROM apps WHERE name LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' OR packageName LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<FDroidApp>>

    @Query("SELECT DISTINCT category FROM apps ORDER BY category ASC")
    fun categories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<FDroidApp>)

    @Query("DELETE FROM apps")
    suspend fun clear()
}