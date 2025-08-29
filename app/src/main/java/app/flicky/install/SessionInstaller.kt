package app.flicky.install

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.security.MessageDigest

class SessionInstaller(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun installFromFile(
        file: java.io.File,
        packageName: String,
        expectedSha256: String = "",
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
                return false
            }
        }
        if (expectedSha256.isNotBlank()) {
            val ok = try { verifySha256(Uri.fromFile(file), expectedSha256) } catch (_: Exception) { false }
            if (!ok) return false
        }

        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply { setAppPackageName(packageName) }

        val sessionId = pm.createSession(params)
        val session = pm.openSession(sessionId)

        val total = file.length().coerceAtLeast(1L)
        FileInputStream(file).use { fis ->
            session.openWrite("base.apk", 0, -1).use { out ->
                val buf = ByteArray(64 * 1024)
                var written = 0L
                var r = fis.read(buf)
                while (r != -1) {
                    out.write(buf, 0, r)
                    written += r
                    onProgress(0.5f + 0.5f * (written.toFloat() / total.toFloat()))
                    r = fis.read(buf)
                }
                session.fsync(out)
            }
        }

        val intent = Intent("app.flicky.INSTALL_RESULT")
        val pending = PendingIntent.getBroadcast(
            context, sessionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.commit(pending.intentSender)
        session.close()

        val (_, status) = SessionInstallBus.events.first { it.first == sessionId }
        return status == PackageInstaller.STATUS_SUCCESS
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