package app.flicky.install

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal object Signatures {

    private fun digest(cert: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert).joinToString("") { "%02x".format(it) }
    }

    private fun certDigestsFromPackageInfo(pi: PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= 28) {
            val s = pi.signingInfo ?: return emptySet()
            val signers = s.apkContentsSigners ?: return emptySet()
            signers.map { digest(it.toByteArray()) }.toSet()
        } else {
            @Suppress("DEPRECATION")
            (pi.signatures ?: emptyArray()).map { digest(it.toByteArray()) }.toSet()
        }
    }

    fun installedCertDigests(pm: PackageManager, packageName: String): Set<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                certDigestsFromPackageInfo(pi)
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                certDigestsFromPackageInfo(pi)
            }
        }.getOrDefault(emptySet())
    }

    fun archiveCertDigests(pm: PackageManager, apk: File): Set<String> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val pi = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
                // On some devices we need to set these to populate ApplicationInfo fields:
                pi?.applicationInfo?.sourceDir = apk.path
                pi?.applicationInfo?.publicSourceDir = apk.path
                if (pi != null) certDigestsFromPackageInfo(pi) else emptySet()
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
                if (pi != null) certDigestsFromPackageInfo(pi) else emptySet()
            }
        }.getOrDefault(emptySet())
    }

    fun isReplaceAllowed(installed: Set<String>, incoming: Set<String>): Boolean {
        if (installed.isEmpty()) return true // not installed
        if (incoming.isEmpty()) return true // couldn’t read archive; don’t block, let pm decide
        // Any overlap is OK (covers same key and common rotations with shared cert)
        return installed.intersect(incoming).isNotEmpty()
    }
}