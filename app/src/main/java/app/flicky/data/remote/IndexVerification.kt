package app.flicky.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarFile

class IndexVerificationException(
    val reason: Reason,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail ?: reason.name, cause) {

    enum class Reason {
        NOT_SIGNED,
        SIGNATURE_INVALID,
        FINGERPRINT_MISMATCH,
        HASH_MISMATCH,
        MISSING_SIGNED_INDEX,
        MALFORMED
    }
}

@Serializable
data class IndexEntryFile(
    val name: String = "",
    val sha256: String = "",
    val size: Long = 0
)

@Serializable
data class IndexEntry(
    val version: Long = 0,
    val timestamp: Long = 0,
    val index: IndexEntryFile = IndexEntryFile(),
    val diffs: Map<String, IndexEntryFile> = emptyMap()
)

val knownRepoFingerprints: Map<String, String> = mapOf(
    "https://f-droid.org/repo" to "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
    "https://f-droid.org/archive" to "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
    "https://apt.izzysoft.de/fdroid/repo" to "3bf0d6abfeae2f401707b6d966be743bf0eee49c2561b9ba39073711f628937a",
    "https://archive.newpipe.net/fdroid/repo" to "e2402c78f9b97c6c89e97db914a2751fda1d02fe2039cc0897a462bdb57e7501",
    "https://briarproject.org/fdroid/repo" to "1fb874bee7276d28ecb2c9b06e8a122ec4bcb4008161436ce474c257cbf49bd6",
    "https://guardianproject.info/fdroid/repo" to "b7c2eefd8dac7806af67dfcd92eb18126bc08312a7f2d6f3862e46013c7a6135",
    "https://microg.org/fdroid/repo" to "9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165"
)

fun expectedSigningKey(baseUrl: String, stored: String?): String? =
    knownRepoFingerprints[baseUrl.trim().trimEnd('/').lowercase()]
        ?: stored?.trim()?.takeIf { it.isNotEmpty() }

object IndexJarVerifier {

    const val ENTRY_JSON = "entry.json"
    const val INDEX_V1_JSON = "index-v1.json"

    private val FORBIDDEN_DIGESTS = listOf("MD5-Digest", "SHA1-Digest")

    suspend fun verifyEntryJar(jar: File, expectedFingerprint: String?, json: Json): Pair<IndexEntry, String> =
        verify(jar, ENTRY_JSON, expectedFingerprint) { stream ->
            json.decodeFromString(IndexEntry.serializer(), stream.readBytes().decodeToString())
        }

    /**
     * Verifies the JAR signature of [entryName], returns the extracted value and the SHA-256
     * fingerprint of the signing certificate. Fails when the entry is unsigned, the signature is
     * invalid, weak digests are used or [expectedFingerprint] is set and does not match.
     */
    suspend fun <T> verify(
        jar: File,
        entryName: String,
        expectedFingerprint: String?,
        extract: suspend (InputStream) -> T
    ): Pair<T, String> {
        JarFile(jar, true).use { file ->
            val entry = file.getEntry(entryName) as? JarEntry
                ?: throw IndexVerificationException(
                    IndexVerificationException.Reason.MISSING_SIGNED_INDEX,
                    "$entryName is missing from the index JAR"
                )
            val attributes = entry.attributes
                ?: throw IndexVerificationException(
                    IndexVerificationException.Reason.NOT_SIGNED,
                    "No signature attributes for $entryName"
                )
            attributes.keys.forEach { key ->
                if (key.toString() in FORBIDDEN_DIGESTS) {
                    throw IndexVerificationException(
                        IndexVerificationException.Reason.SIGNATURE_INVALID,
                        "Unsupported digest $key in $entryName"
                    )
                }
            }
            val value = try {
                file.getInputStream(entry).use { input ->
                    val extracted = extract(input)
                    // the signature check only completes once the entry is read to the end
                    val buffer = ByteArray(4096)
                    while (input.read(buffer) >= 0) {
                    }
                    extracted
                }
            } catch (e: SecurityException) {
                throw IndexVerificationException(
                    IndexVerificationException.Reason.SIGNATURE_INVALID,
                    "Signature verification failed for $entryName",
                    e
                )
            }
            val fingerprint = fingerprintOf(certificateOf(entry))
            if (!expectedFingerprint.isNullOrEmpty() && !fingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                throw IndexVerificationException(
                    IndexVerificationException.Reason.FINGERPRINT_MISMATCH,
                    "Signing key of $entryName does not match the stored key"
                )
            }
            return value to fingerprint
        }
    }

    private fun certificateOf(entry: JarEntry): X509Certificate {
        val signers = entry.codeSigners
        if (signers.isNullOrEmpty()) {
            throw IndexVerificationException(
                IndexVerificationException.Reason.NOT_SIGNED,
                "Index is not signed"
            )
        }
        if (signers.size != 1) {
            throw IndexVerificationException(
                IndexVerificationException.Reason.SIGNATURE_INVALID,
                "Index must have exactly one signer"
            )
        }
        val certificates = signers[0].signerCertPath.certificates
        if (certificates.size != 1) {
            throw IndexVerificationException(
                IndexVerificationException.Reason.SIGNATURE_INVALID,
                "Signer must have exactly one certificate"
            )
        }
        return certificates[0] as? X509Certificate
            ?: throw IndexVerificationException(
                IndexVerificationException.Reason.SIGNATURE_INVALID,
                "Signer certificate is not X.509"
            )
    }

    private fun fingerprintOf(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).toHex()
}

private const val HEX_DIGITS = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xff
        out.append(HEX_DIGITS[value ushr 4])
        out.append(HEX_DIGITS[value and 0x0f])
    }
    return out.toString()
}
