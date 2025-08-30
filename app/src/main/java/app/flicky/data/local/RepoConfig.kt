package app.flicky.data.local

import androidx.room.*

/**
 * Per-repository configuration stored locally.
 * - Controls enablement, mirror rotation, onion usage, and mirror selection strategy.
 * - Trust options drive how OkHttp clients are built for each repo (HttpsOnly, Pinned, CustomCA).
 * - downloadBase lets APKs come from a different host (e.g., CDN) than the index.
 */
@Entity(
    tableName = "repo_config",
    indices = [Index(value = ["baseUrl"], unique = true)]
)
data class RepoConfig(
    @PrimaryKey
    @ColumnInfo(name = "baseUrl")
    val baseUrl: String,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "rotateMirrors")
    val rotateMirrors: Boolean = false,

    @ColumnInfo(name = "includeOnion")
    val includeOnion: Boolean = false,

    // Mirror selection: StickyLastGood | RoundRobin | CanonicalFirst
    @ColumnInfo(name = "strategy")
    val strategy: String = "StickyLastGood",

    // Trust options
    // HttpsOnly (default), Pinned (CertificatePinner), CustomCA (single or chain PEM)
    @ColumnInfo(name = "trustMode")
    val trustMode: String = "HttpsOnly", // HttpsOnly | Pinned | CustomCA

    // Comma/space-separated pins, each like "sha256/BASE64=="
    @ColumnInfo(name = "pins")
    val pins: String = "",

    // PEM-encoded certificate or chain for CustomCA
    @ColumnInfo(name = "caPem")
    val caPem: String = "",

    @ColumnInfo(name = "downloadBase")
    val downloadBase: String = "" // empty => use baseUrl
)