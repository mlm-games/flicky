package app.flicky.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "apps",
    indices = [
        Index(value = ["name"]),
        Index(value = ["summary"]),
        Index(value = ["packageName"]),
        Index(value = ["category"])
    ]
)
data class FDroidApp(
    @PrimaryKey val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconUrl: String,
    val version: String,
    val versionCode: Int,
    val size: Long,
    val apkUrl: String,
    val license: String,
    val category: String,
    val author: String,
    val website: String,
    val sourceCode: String,
    val added: Long,
    val lastUpdated: Long,
    val screenshots: List<String> = emptyList(),
    val antiFeatures: List<String> = emptyList(),
    val downloads: Long = 0,
    val isInstalled: Boolean = false,
    val repository: String = "F-Droid",
    val sha256: String = "",
    val whatsNew: String = ""
)
