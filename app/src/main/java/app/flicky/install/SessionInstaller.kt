package app.flicky.install

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileInputStream
import kotlin.coroutines.resume
import androidx.core.net.toUri

class SessionInstaller(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun install(app: FDroidApp, onProgress: (Float) -> Unit = {}): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false

        // Download APK
        val req = DownloadManager.Request(app.apkUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val downloadId = dm.enqueue(req)
        val apkUri = awaitDownload(downloadId) ?: return false

        // Create install session
        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
        }
        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)

        // Pipe APK into session
        val pfd = context.contentResolver.openFileDescriptor(apkUri, "r") ?: return false
        pfd.use {
            val inStream = FileInputStream(it.fileDescriptor)
            session.openWrite("base.apk", 0, -1).use { out ->
                val buffer = ByteArray(8192)
                var read = inStream.read(buffer)
                var total = 0L
                while (read != -1) {
                    out.write(buffer, 0, read)
                    total += read
                    read = inStream.read(buffer)
                    onProgress(0.5f + 0.5f * (total / (app.size.toFloat().coerceAtLeast(1f))))
                }
                session.fsync(out)
            }
        }

        // Commit session (user confirmation will occur)
        val intent = Intent("app.flicky.INSTALL_RESULT")
        val pending = PendingIntent.getBroadcast(
            context, sessionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.commit(pending.intentSender)
        session.close()

        // Await result
        val (sid, status) = SessionInstallBus.events.first { it.first == sessionId }
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
}