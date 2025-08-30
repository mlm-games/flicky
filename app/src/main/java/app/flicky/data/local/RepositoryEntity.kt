package app.flicky.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Repository metadata from parsed index files (name/description/web/timestamp).
 * Joined with repo_config for UI and behavior.
 */
@Entity(
    tableName = "repositories",
    indices = [Index(value = ["baseUrl"], unique = true)]
)
data class RepositoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "baseUrl")
    val baseUrl: String,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "webBaseUrl")
    val webBaseUrl: String = "",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = 0L,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String = ""
)

/**
 * Joined UI row: repository metadata + enabled flag from repo_config.
 */
data class RepoWithConfigRow(
    @ColumnInfo(name = "baseUrl") val baseUrl: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "webBaseUrl") val webBaseUrl: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean
)

@Dao
interface RepositoryDao {
    @Query("SELECT * FROM repositories")
    suspend fun getAll(): List<RepositoryEntity>

    @Query("SELECT * FROM repositories WHERE baseUrl = :base LIMIT 1")
    suspend fun get(base: String): RepositoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RepositoryEntity)

    @Query("DELETE FROM repositories")
    suspend fun clearAll()

    /**
     * Observe the union of repositories and repo_config.
     * This ensures rows appear even if only one side has been created.
     */
    @Query("""
        SELECT b.baseUrl AS baseUrl,
               COALESCE(r.name, '') AS name,
               COALESCE(r.description, '') AS description,
               COALESCE(r.webBaseUrl, '') AS webBaseUrl,
               COALESCE(rc.enabled, 1) AS enabled
        FROM (
            SELECT baseUrl FROM repositories
            UNION
            SELECT baseUrl FROM repo_config
        ) b
        LEFT JOIN repositories r ON r.baseUrl = b.baseUrl
        LEFT JOIN repo_config rc ON rc.baseUrl = b.baseUrl
        ORDER BY name COLLATE NOCASE, baseUrl COLLATE NOCASE
    """)
    fun observeWithConfig(): Flow<List<RepoWithConfigRow>>
}

@Dao
interface RepoConfigDao {
    @Query("SELECT * FROM repo_config")
    suspend fun all(): List<RepoConfig>

    @Query("SELECT * FROM repo_config WHERE baseUrl = :base")
    suspend fun get(base: String): RepoConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cfg: RepoConfig)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(cfg: RepoConfig)

    @Query("UPDATE repo_config SET enabled = :enabled WHERE baseUrl = :base")
    suspend fun setEnabled(base: String, enabled: Boolean)

    @Query("DELETE FROM repo_config WHERE baseUrl = :base")
    suspend fun delete(base: String)

    @Query("DELETE FROM repo_config")
    suspend fun clearAll()
}