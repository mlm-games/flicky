package app.flicky.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.flow.Flow


@Entity(
    tableName = "app_variants",
    primaryKeys = ["packageName", "repositoryUrl", "versionCode"],
    indices = [Index(value = ["packageName"]), Index(value = ["repositoryUrl"])]
)
data class AppVariant(
    val packageName: String,
    val repositoryUrl: String,
    val repositoryName: String,
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val isCompatible: Boolean
)

@Dao
interface AppDao {
    @Query("SELECT * FROM apps")
    fun observeAll(): Flow<List<FDroidApp>>

    @Query("SELECT COUNT(*) FROM apps")
    suspend fun count(): Int

    @Query("SELECT * FROM apps WHERE name LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%' OR packageName LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<FDroidApp>>

    @Query("SELECT DISTINCT category FROM apps ORDER BY category ASC")
    fun categories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<FDroidApp>)

    @Query("SELECT * FROM app_variants WHERE packageName = :pkg ORDER BY repositoryName, versionCode DESC")
    suspend fun variantsFor(pkg: String): List<AppVariant>

    @Query("DELETE FROM apps")
    suspend fun clear()

    @Query("DELETE FROM apps WHERE repositoryUrl = :repoUrl")
    suspend fun deleteByRepositoryUrl(repoUrl: String)

    @Query("""
SELECT * FROM apps
WHERE (:q == '' OR name LIKE '%'||:q||'%' OR summary LIKE '%'||:q||'%' OR packageName LIKE '%'||:q||'%')
AND (:hideAnti = 0 OR antiFeatures = '[]')
AND (:showIncompat = 1 OR isCompatible = 1)
ORDER BY lastUpdated DESC
""")
    fun pagingByUpdated(q: String, hideAnti: Int, showIncompat: Int): PagingSource<Int, FDroidApp>

    @Query("""
SELECT * FROM apps
WHERE (:q == '' OR name LIKE '%'||:q||'%' OR summary LIKE '%'||:q||'%' OR packageName LIKE '%'||:q||'%')
AND (:hideAnti = 0 OR antiFeatures = '[]')
AND (:showIncompat = 1 OR isCompatible = 1)
ORDER BY name COLLATE NOCASE ASC
""")
    fun pagingByName(q: String, hideAnti: Int, showIncompat: Int): PagingSource<Int, FDroidApp>

    @Query("""
SELECT * FROM apps
WHERE (:q == '' OR name LIKE '%'||:q||'%' OR summary LIKE '%'||:q||'%' OR packageName LIKE '%'||:q||'%')
AND (:hideAnti = 0 OR antiFeatures = '[]')
AND (:showIncompat = 1 OR isCompatible = 1)
ORDER BY size ASC
""")
    fun pagingBySize(q: String, hideAnti: Int, showIncompat: Int): PagingSource<Int, FDroidApp>

    @Query("""
SELECT * FROM apps
WHERE (:q == '' OR name LIKE '%'||:q||'%' OR summary LIKE '%'||:q||'%' OR packageName LIKE '%'||:q||'%')
AND (:hideAnti = 0 OR antiFeatures = '[]')
AND (:showIncompat = 1 OR isCompatible = 1)
ORDER BY added DESC
""")
    fun pagingByAdded(q: String, hideAnti: Int, showIncompat: Int): PagingSource<Int, FDroidApp>
}