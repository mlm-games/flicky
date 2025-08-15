package app.flicky.install

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.resume

class Installer(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun open(packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        // Check install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
        }

        // Ensure URL is complete
        val downloadUrl = when {
            app.apkUrl.startsWith("http://") || app.apkUrl.startsWith("https://") -> app.apkUrl
            app.apkUrl.startsWith("/") -> {
                // Construct full URL from repository base
                val repoBase = when(app.repository) {
                    "F-Droid" -> "https://f-droid.org/repo"
                    "IzzyOnDroid" -> "https://apt.izzysoft.de/fdroid/repo"
                    else -> "https://f-droid.org/repo"
                }
                repoBase + app.apkUrl
            }
            else -> {
                // Assume it's a filename only
                val repoBase = when(app.repository) {
                    "F-Droid" -> "https://f-droid.org/repo"
                    "IzzyOnDroid" -> "https://apt.izzysoft.de/fdroid/repo"
                    else -> "https://f-droid.org/repo"
                }
                "$repoBase/${app.apkUrl}"
            }
        }

        return try {
            downloadAndInstall(app, downloadUrl, onProgress)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun downloadAndInstall(
        app: FDroidApp,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val req = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setDescription("Downloading from ${app.repository}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
//            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${app.packageName}.apk")

        val downloadId = dm.enqueue(req)

        // Monitor download progress
        val uri = monitorDownload(downloadId, onProgress) ?: return@withContext false

        // Verify if needed
        if (app.sha256.isNotBlank() && !verifySha256(uri, app.sha256)) {
            return@withContext false
        }

        // Install
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(install)
        true
    }

    private suspend fun monitorDownload(id: Long, onProgress: (Float) -> Unit): Uri? =
        withContext(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(id)
            var downloading = true
            var lastProgress = -1f

            while (downloading) {
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            val uri = dm.getUriForDownloadedFile(id)
                            cursor.close()
                            onProgress(1f)
                            return@withContext uri
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            cursor.close()
                            return@withContext null
                        }
                        DownloadManager.STATUS_PAUSED,
                        DownloadManager.STATUS_PENDING -> {
                            // Continue waiting
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                            if (bytesIdx != -1 && totalIdx != -1) {
                                val bytes = cursor.getLong(bytesIdx)
                                val total = cursor.getLong(totalIdx)

                                if (total > 0) {
                                    val progress = bytes.toFloat() / total.toFloat()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        onProgress(progress)
                                    }
                                } else {
                                    // Size unknown, show indeterminate
                                    onProgress(0f)
                                }
                            }
                        }
                    }
                    cursor.close()
                }

                if (downloading) {
                    delay(500) // Check every 500ms
                }
            }
            null
        }

    private fun verifySha256(uri: Uri, expectedHex: String): Boolean {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
            pfd.use {
                val fis = FileInputStream(it.fileDescriptor)
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(8192)
                var r = fis.read(buf)
                while (r != -1) {
                    md.update(buf, 0, r)
                    r = fis.read(buf)
                }
                val actual = md.digest().joinToString("") { byte -> "%02x".format(byte) }
                actual.equals(expectedHex, ignoreCase = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}