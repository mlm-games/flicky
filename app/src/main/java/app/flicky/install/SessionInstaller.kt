package app.flicky.install

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.resume

class SessionInstaller(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false

        // Unknown sources permission (Android 8.0+)
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

        // Download APK (basic; can extend with progress reporting if needed)
        val req = DownloadManager.Request(app.apkUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val downloadId = dm.enqueue(req)
        val apkUri = awaitDownload(downloadId) ?: return false

        // Verify APK hash before installing
        if (app.sha256.isNotBlank() && !verifySha256(apkUri, app.sha256)) {
            return false
        }

        // Create install session
        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
        }
        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)

        // Pipe APK into session
        context.contentResolver.openFileDescriptor(apkUri, "r")?.use { pfd ->
            val totalLength = app.size.coerceAtLeast(1L)
            FileInputStream(pfd.fileDescriptor).use { inStream ->
                session.openWrite("base.apk", 0, -1).use { out ->
                    val buf = ByteArray(8192)
                    var read = inStream.read(buf)
                    var written = 0L
                    while (read != -1) {
                        out.write(buf, 0, read)
                        written += read
                        // Map copy progress to 0.5..1.0 (when adding DM progress, map that to 0..0.5)
                        onProgress(0.5f + 0.5f * (written.toFloat() / totalLength.toFloat()))
                        read = inStream.read(buf)
                    }
                    session.fsync(out)
                }
            }
        }

        // Commit session
        val intent = Intent("app.flicky.INSTALL_RESULT")
        val pending = PendingIntent.getBroadcast(
            context, sessionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.commit(pending.intentSender)
        session.close()

        // Await result from InstallResultReceiver -> SessionInstallBus
        val (_, status) = SessionInstallBus.events.first { it.first == sessionId }
        return status == PackageInstaller.STATUS_SUCCESS
    }

    private suspend fun awaitDownload(id: Long): Uri? = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                val completedId = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId == id) {
                    context.unregisterReceiver(this)
                    val fileUri = dm.getUriForDownloadedFile(id)
                    cont.resume(fileUri)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        cont.invokeOnCancellation { context.unregisterReceiver(receiver) }
    }

    private fun verifySha256(uri: Uri, expectedHex: String): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(8192)
                    var r = fis.read(buf)
                    while (r != -1) {
                        md.update(buf, 0, r)
                        r = fis.read(buf)
                    }
                    val actual = md.digest().joinToString("") { b -> "%02x".format(b) }
                    actual.equals(expectedHex, ignoreCase = true)
                }
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}