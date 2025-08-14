package app.flicky.install

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.flicky.data.model.FDroidApp
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import androidx.core.net.toUri

class LegacyInstaller(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

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

    suspend fun install(app: FDroidApp, onProgress: (Float)->Unit = {}): Boolean {
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

        val req = DownloadManager.Request(app.apkUrl.toUri())
            .setTitle("${app.name} ${app.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = dm.enqueue(req)
        val uri = awaitDownload(id) ?: return false

        if (app.sha256.isNotBlank() && !verifySha256(uri, app.sha256)) return false

        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(install)
        return true
    }

    private suspend fun awaitDownload(id: Long): Uri? = suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
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
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            val fis = FileInputStream(pfd?.fileDescriptor)
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8 * 1024)
            var r = fis.read(buf)
            while (r != -1) {
                md.update(buf, 0, r)
                r = fis.read(buf)
            }
            fis.close()
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expectedHex, ignoreCase = true)
        } catch (_: Exception) { false }
    }
}