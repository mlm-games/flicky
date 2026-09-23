package app.flicky.data.remote

import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile

class IndexVerificationException(message: String, cause: Throwable? = null) :
    ClientConfigurationException(message, cause)

data class VerifiedEntry(
    val certificateHex: String,
    val fingerprint: String,
    val timestamp: Long,
    val version: Long,
    val indexName: String,
    val indexSha256: String,
    val indexSize: Long
)

object IndexVerifier {
    private const val TAG = "IndexVerifier"

    private val forbiddenEntryDigests = setOf("MD5-Digest", "SHA1-Digest")
    private val supportedV1Digests = setOf("SHA1-Digest", "SHA-256-Digest")

    fun fingerprintOf(certBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(certBytes).joinToString("") { "%02x".format(it) }
    }

    fun certificateHexOf(cert: X509Certificate): String {
        return cert.encoded.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Odd hex length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun verifyEntryJar(
        jarFile: File,
        expectedFingerprint: String?,
        expectedCertificateHex: String?
    ): VerifiedEntry {
        val fp = expectedFingerprint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val certHex = expectedCertificateHex?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        require(fp == null || certHex == null) {
            "Provide either fingerprint or certificate, not both"
        }
        return verifyJar(
            jarFile = jarFile,
            jsonName = "entry.json",
            expectedFingerprint = fp,
            expectedCertificateHex = certHex,
            digestCheck = { attrs ->
                attrs.keys.forEach { key ->
                    if (key.toString() in forbiddenEntryDigests) {
                        throw IndexVerificationException("Unsupported digest in entry.jar: $key")
                    }
                }
            },
            parse = ::parseEntryJson
        )
    }

    @Deprecated("Unused, kept for reference")
    fun verifyIndexV1JarLegacy(
        jarFile: File,
        expectedFingerprint: String?,
        expectedCertificateHex: String?,
        onJsonStream: (java.io.InputStream) -> Unit
    ): String {
        val fp = expectedFingerprint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val certHex = expectedCertificateHex?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        require(fp == null || certHex == null) {
            "Provide either fingerprint or certificate, not both"
        }
        var certOut = ""
        verifyJar(
            jarFile = jarFile,
            jsonName = "index-v1.json",
            expectedFingerprint = fp,
            expectedCertificateHex = certHex,
            digestCheck = { attrs ->
                attrs.keys.forEach { key ->
                    if (key.toString() !in supportedV1Digests) {
                        throw IndexVerificationException("Unsupported digest in index-v1.jar: $key")
                    }
                }
            },
            parse = { bytes ->
                certOut = ""
                bytes.inputStream().use(onJsonStream)
                VerifiedEntry(
                    certificateHex = "",
                    fingerprint = "",
                    timestamp = 0L,
                    version = 0L,
                    indexName = "",
                    indexSha256 = "",
                    indexSize = 0L
                )
            }
        )
        return certOut
    }

    fun verifyV1JarStreaming(
        jarBytes: ByteArray,
        expectedFingerprint: String?,
        expectedCertificateHex: String?,
        onJsonStream: (java.io.InputStream) -> Unit
    ): String {
        val fp = expectedFingerprint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val certHex = expectedCertificateHex?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        require(fp == null || certHex == null) {
            "Provide either fingerprint or certificate, not both"
        }
        val tmp = File.createTempFile("index-v1-", ".jar")
        try {
            tmp.writeBytes(jarBytes)
            var certOut = ""
            JarFile(tmp, true).use { jar ->
                val entry = (jar.getJarEntry("index-v1.json") ?: jar.getEntry("index-v1.json") as? JarEntry)
                    ?: throw IndexVerificationException("index-v1.jar has no index-v1.json")
                val attrs = entry.attributes
                    ?: throw IndexVerificationException("No manifest attributes for index-v1.json")
                attrs.keys.forEach { key ->
                    if (key.toString() !in supportedV1Digests) {
                        throw IndexVerificationException("Unsupported digest in index-v1.jar: $key")
                    }
                }
                val bytes: ByteArray = readAndVerifyEntry(jar, entry, "index-v1.jar")
                bytes.inputStream().use(onJsonStream)
                certOut = checkSignerAndPin(jar, entry, "index-v1.jar", fp, certHex)
            }
            return certOut
        } finally {
            tmp.delete()
        }
    }

    @Deprecated("Unused, kept for reference")
    fun verifyIndexV1JarWithCertLegacy(
        jarFile: File,
        expectedFingerprint: String?,
        expectedCertificateHex: String?,
        onJsonStream: (java.io.InputStream) -> Unit
    ): String {
        val fp = expectedFingerprint?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val certHex = expectedCertificateHex?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        var certOut = ""
        JarFile(jarFile, true).use { jar ->
            val entry = (jar.getJarEntry("index-v1.json") ?: jar.getEntry("index-v1.json") as? JarEntry)
                ?: throw IndexVerificationException("index-v1.jar has no index-v1.json")
            val attrs = entry.attributes
                ?: throw IndexVerificationException("No manifest attributes for index-v1.json")
            attrs.keys.forEach { key ->
                if (key.toString() !in supportedV1Digests) {
                    throw IndexVerificationException("Unsupported digest in index-v1.jar: $key")
                }
            }
            val bytes: ByteArray = readAndVerifyEntry(jar, entry, "index-v1.jar")
            bytes.inputStream().use(onJsonStream)
            certOut = checkSignerAndPin(jar, entry, "index-v1.jar", fp, certHex)
        }
        return certOut
    }

    private fun verifyJar(
        jarFile: File,
        jsonName: String,
        expectedFingerprint: String?,
        expectedCertificateHex: String?,
        digestCheck: (java.util.jar.Attributes) -> Unit,
        parse: (ByteArray) -> VerifiedEntry
    ): VerifiedEntry {
        try {
            JarFile(jarFile, true).use { jar ->
                val entry = (jar.getJarEntry(jsonName) ?: jar.getEntry(jsonName) as? JarEntry)
                    ?: throw IndexVerificationException("$jsonName not found in ${jarFile.name}")
                val attrs = entry.attributes
                    ?: throw IndexVerificationException("No manifest attributes for $jsonName")
                digestCheck(attrs)
                val bytes: ByteArray = readAndVerifyEntry(jar, entry, jsonName)
                val parsed = parse(bytes)
                val certHex = checkSignerAndPin(jar, entry, jsonName, expectedFingerprint, expectedCertificateHex)
                val fp = fingerprintOf(hexToBytes(certHex))
                return parsed.copy(certificateHex = certHex, fingerprint = fp)
            }
        } catch (e: IndexVerificationException) {
            throw e
        } catch (e: Exception) {
            throw IndexVerificationException("Failed to verify ${jarFile.name}: ${e.message}", e)
        }
    }

    private fun readAndVerifyEntry(jar: JarFile, entry: JarEntry, jsonName: String): ByteArray {
        val bytes: ByteArray = try {
            jar.getInputStream(entry).use { it.readBytes() }
        } catch (e: SecurityException) {
            throw IndexVerificationException("Jar signature check failed for $jsonName", e)
        }
        try {
            entry.codeSigners
        } catch (e: SecurityException) {
            throw IndexVerificationException("Jar signature check failed for $jsonName", e)
        }
        return bytes
    }

    private fun checkSignerAndPin(
        jar: JarFile,
        entry: JarEntry,
        jsonName: String,
        expectedFingerprint: String?,
        expectedCertificateHex: String?
    ): String {
        val signers = try {
            entry.codeSigners
        } catch (e: SecurityException) {
            throw IndexVerificationException("Jar signature check failed for $jsonName", e)
        }
        val signerCerts: List<java.security.cert.Certificate> = when {
            !signers.isNullOrEmpty() -> signers[0].signerCertPath.certificates.toList()
            else -> certsFromBlockFile(jar)
                ?: throw IndexVerificationException("No signature in ${jar.name}, entry $jsonName was not signed")
        }
        if (!signers.isNullOrEmpty() && signers.size != 1) {
            throw IndexVerificationException("$jsonName must be signed by a single signer, got ${signers.size}")
        }
        if (signerCerts.size != 1) {
            throw IndexVerificationException("$jsonName signer must have a single certificate, got ${signerCerts.size}")
        }
        val cert = signerCerts[0] as? X509Certificate
            ?: throw IndexVerificationException("Signer certificate is not X.509")
        val certHex = certificateHexOf(cert).lowercase()
        if (certHex.length < 512) {
            throw IndexVerificationException("Signer certificate too short (${certHex.length / 2} bytes)")
        }
        if (expectedCertificateHex != null && expectedCertificateHex != certHex) {
            throw IndexVerificationException("Repo signing certificate does not match pinned certificate")
        }
        if (expectedCertificateHex == null && expectedFingerprint != null) {
            val actualFp = fingerprintOf(cert.encoded).lowercase()
            if (actualFp != expectedFingerprint) {
                throw IndexVerificationException("Repo signing key fingerprint mismatch")
            }
        }
        return certHex
    }

    private fun certsFromBlockFile(jar: JarFile): List<java.security.cert.Certificate>? {
        val entries = jar.entries().asSequence().toList()
        val block = entries.firstOrNull {
            val n = it.name.uppercase()
            n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))
        } ?: return null
        return try {
            jar.getInputStream(block).use { ins ->
                val cf = CertificateFactory.getInstance("X.509")
                val path = cf.generateCertPath(ins, "PKCS7")
                path.certificates
            }
        } catch (_: Exception) {
            null
        }
    }

    fun fingerprintFromCertificateHex(certificateHex: String): String =
        fingerprintOf(hexToBytes(certificateHex.trim().lowercase()))

    @Deprecated("Unused, kept for reference")
    fun certificateFromPemOrHexLegacy(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        return if (t.contains("BEGIN CERTIFICATE")) {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = mutableListOf<X509Certificate>()
            val blocks = t.split("-----END CERTIFICATE-----")
                .map { it.trim() }
                .filter { it.contains("-----BEGIN CERTIFICATE-----") }
                .map { "$it\n-----END CERTIFICATE-----\n" }
            for (b in blocks) {
                b.byteInputStream().use { cf.generateCertificate(it) as? X509Certificate }?.let(certs::add)
            }
            if (certs.isEmpty()) throw IndexVerificationException("No certificate in PEM")
            certificateHexOf(certs.first()).lowercase()
        } else {
            t.lowercase().replace("\\s".toRegex(), "")
        }
    }

    @Deprecated("Unused, kept for reference")
    fun sha256HexOfBytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            var r = ins.read(buf)
            while (r != -1) {
                md.update(buf, 0, r)
                r = ins.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseEntryJson(bytes: ByteArray): VerifiedEntry {
        var timestamp = 0L
        var version = 0L
        var indexName = "/index-v2.json"
        var indexSha256 = ""
        var indexSize = -1L
        InputStreamReader(bytes.inputStream(), Charsets.UTF_8).use { isr ->
            JsonReader(isr).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "timestamp" -> timestamp = nextLongLenient(r)
                        "version" -> version = nextLongLenient(r)
                        "index" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "name" -> indexName = nextStringLenient(r) ?: "/index-v2.json"
                                    "sha256" -> indexSha256 = nextStringLenient(r) ?: ""
                                    "size" -> indexSize = nextLongLenient(r)
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
        }
        if (indexSha256.isBlank()) {
            throw IndexVerificationException("entry.json has no index sha256")
        }
        return VerifiedEntry("", "", timestamp, version, indexName, indexSha256.lowercase(), indexSize)
    }

    private fun nextStringLenient(r: JsonReader): String? =
        if (r.peek() == JsonToken.STRING) r.nextString() else {
            r.skipValue(); null
        }

    private fun nextLongLenient(r: JsonReader): Long = when (r.peek()) {
        JsonToken.NUMBER -> runCatching { r.nextLong() }.getOrElse { r.skipValue(); 0L }
        JsonToken.STRING -> runCatching { r.nextString().toLong() }.getOrElse { 0L }
        else -> {
            r.skipValue(); 0L
        }
    }

    @Deprecated("Unused, kept for reference")
    fun writeBytesAtomicLegacy(dest: File, bytes: ByteArray) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.outputStream().use { it.write(bytes) }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    fun streamToFile(
        body: okhttp3.ResponseBody,
        dest: File,
        expectedSha256: String?,
        expectedSize: Long = -1L,
        progress: ((bytesRead: Long, total: Long) -> Unit)? = null
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        val md = MessageDigest.getInstance("SHA-256")
        var read = 0L
        try {
            body.byteStream().use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var r = ins.read(buf)
                    while (r != -1) {
                        out.write(buf, 0, r)
                        md.update(buf, 0, r)
                        read += r
                        progress?.invoke(read, expectedSize)
                        r = ins.read(buf)
                    }
                    out.flush()
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        if (expectedSize > 0 && read != expectedSize) {
            tmp.delete()
            throw IndexVerificationException("Size mismatch: got $read, expected $expectedSize")
        }
        if (!expectedSha256.isNullOrBlank()) {
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                tmp.delete()
                throw IndexVerificationException("SHA-256 mismatch for ${dest.name}")
            }
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        Log.d(TAG, "Stored ${dest.name} ($read bytes)")
    }

    fun readFullyCapped(body: okhttp3.ResponseBody, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        body.byteStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            var total = 0L
            var r = ins.read(buf)
            while (r != -1) {
                total += r
                if (total > maxBytes) throw IndexVerificationException("Response exceeds ${maxBytes} bytes")
                out.write(buf, 0, r)
                r = ins.read(buf)
            }
        }
        return out.toByteArray()
    }
}
