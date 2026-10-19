package app.flicky.data.repository

import android.os.Build
import android.util.Log
import androidx.core.os.LocaleListCompat
import androidx.room.withTransaction
import app.flicky.data.local.AppDao
import app.flicky.data.local.AppDatabase
import app.flicky.data.local.AppVariant
import app.flicky.data.local.RepositoryDao
import app.flicky.data.local.RepositoryEntity
import app.flicky.data.model.FDroidApp
import app.flicky.data.remote.MirrorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.Locale

/**
 * A verified v2 index diff for a repository, ready to be applied as a partial update.
 * [payload] is owned by the applier, which deletes it after applying.
 */
data class RepoIndexDiff(
    val baseUrl: String,
    val repoName: String,
    val timestamp: Long,
    val fingerprint: String,
    val payload: File
)

/**
 * Applies a v2 index diff to the database as a partial update, mirroring the reference client:
 * `packages[pkg] = null` deletes app and variants, `metadata = null` deletes the app,
 * `metadata = {...}` is merged field by field, `versions = null` deletes all variants,
 * `versions[cv] = null` deletes one variant and `versions[cv] = {...}` is merged or parsed for a
 * version not seen before. Localized maps are merged per language.
 */
class IndexDiffApplier(private val db: AppDatabase) {

    private companion object {
        const val TAG = "IndexDiffApplier"
    }

    private val appDao: AppDao = db.appDao()
    private val repositoryDao: RepositoryDao = db.repositoryDao()
    private val locales: LocaleListCompat = LocaleListCompat.getDefault()
    private val diffJson = Json { ignoreUnknownKeys = true }

    /**
     * Streams [diff] from disk in a background producer and merges one package at a time on the
     * transaction thread. A decode failure is delivered through the channel, so the whole
     * transaction rolls back.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun apply(diff: RepoIndexDiff): Int = try {
        db.withTransaction {
            coroutineScope {
                val events = Channel<DiffEvent>(8)
                launch(Dispatchers.IO) {
                    try {
                        diff.payload.inputStream().use { stream ->
                            diffJson.decodeFromStream(DiffStreamSerializer(events), stream)
                        }
                        events.close()
                    } catch (t: Throwable) {
                        events.close(t)
                    }
                }

                var touched = 0
                var repo: JsonObject? = null
                try {
                    for (event in events) {
                        when (event) {
                            is DiffEvent.Repo -> {
                                repo = event.repo
                                applyRepoDiff(diff, event.repo)
                            }

                            is DiffEvent.Package -> {
                                applyPackageDiff(diff, event.name, event.value)
                                touched++
                            }
                        }
                    }
                } finally {
                    events.close()
                }

                val diffTimestamp = (repo?.get("timestamp") as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                val timestamp = diffTimestamp?.takeIf { it > 0 } ?: diff.timestamp
                mergeRepositoryRow(repositoryDao, diff.baseUrl, timestamp, diff.fingerprint)
                touched
            }
        }
    } finally {
        diff.payload.delete()
    }

    private suspend fun applyRepoDiff(diff: RepoIndexDiff, repo: JsonObject) {
        val address = {
            (repo["address"] as? JsonPrimitive)?.contentOrNull?.trim()?.trimEnd('/')
                ?.takeIf { it.isNotBlank() }
        }() ?: diff.baseUrl

        val mirrors = repo["mirrors"]
        if (mirrors is JsonArray) {
            val urls = mirrors.mapNotNull { entry -> urlOf(entry) }
            val primary = mirrors.firstOrNull { entry ->
                ((entry as? JsonObject)?.get("isPrimary") as? JsonPrimitive)?.booleanOrNull == true
            }?.let { urlOf(it) }
            runCatching { MirrorRegistry.register(address, listOf(address) + urls, primary) }
        }

        val existing = repositoryDao.get(address) ?: repositoryDao.get(diff.baseUrl) ?: return
        val name = mergeLocalized(repo["name"], existing.name)
        val description = mergeLocalized(repo["description"], existing.description)
        val webBaseUrl = (repo["webBaseUrl"] as? JsonPrimitive)?.contentOrNull
        repositoryDao.upsert(
            existing.copy(
                name = name.ifBlank { existing.name },
                description = description.ifBlank { existing.description },
                webBaseUrl = webBaseUrl?.takeIf { it.isNotBlank() } ?: existing.webBaseUrl
            )
        )
    }

    private fun urlOf(element: JsonElement): String? =
        ((element as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
            ?: (element as? JsonPrimitive)?.contentOrNull

    private suspend fun applyPackageDiff(diff: RepoIndexDiff, pkg: String, value: JsonElement) {
        if (value is JsonNull) {
            appDao.deleteAppForRepo(pkg, diff.baseUrl)
            appDao.deleteVariantsForPackage(pkg, diff.baseUrl)
            return
        }
        val packageDiff = value as? JsonObject ?: return

        val metadata = packageDiff["metadata"]
        if (metadata is JsonNull) {
            appDao.deleteAppForRepo(pkg, diff.baseUrl)
            appDao.deleteVariantsForPackage(pkg, diff.baseUrl)
        } else if (metadata is JsonObject) {
            mergeMetadata(diff, pkg, metadata)
        }

        val versions = packageDiff["versions"]
        when {
            versions is JsonNull -> {
                appDao.deleteVariantsForPackage(pkg, diff.baseUrl)
                refreshDerivedFields(diff, pkg, null)
            }

            versions is JsonObject -> applyVersionsDiff(diff, pkg, versions)

            else -> refreshDerivedFields(diff, pkg, null)
        }
    }

    private suspend fun applyVersionsDiff(diff: RepoIndexDiff, pkg: String, versions: JsonObject) {
        val existing = appDao.variantsForRepo(pkg, diff.baseUrl)
        versions.forEach { (versionId, element) ->
            when (element) {
                is JsonNull -> appDao.deleteVariantBySha(pkg, diff.baseUrl, versionId)
                is JsonObject -> {
                    val known = existing.firstOrNull { it.sha256.equals(versionId, ignoreCase = true) }
                    val merged = mergeVariant(diff, pkg, versionId, element, known)
                    if (merged != null) appDao.upsertVariant(merged)
                }

                else -> Unit
            }
        }
        refreshDerivedFields(diff, pkg, versions)
    }

    private suspend fun mergeMetadata(diff: RepoIndexDiff, pkg: String, metadata: JsonObject) {
        val row = appDao.getApp(pkg)
        if (row != null && row.repositoryUrl != diff.baseUrl) return
        val base = row ?: FDroidApp(
            packageName = pkg,
            name = pkg,
            summary = "",
            description = "",
            iconUrl = "",
            version = "",
            versionCode = 0,
            size = 0,
            apkUrl = "",
            license = "Unknown",
            category = "Other",
            author = "Unknown",
            website = "",
            sourceCode = "",
            added = 0,
            lastUpdated = 0,
            repository = diff.repoName,
            repositoryUrl = diff.baseUrl
        )

        val localized = metadata["localized"] as? JsonObject
        val name = mergeLocalized(localizedField(localized, "name"), mergeLocalized(metadata["name"], base.name))
        val summary = mergeLocalized(localizedField(localized, "summary"), mergeLocalized(metadata["summary"], base.summary))
        val description = mergeLocalized(localizedField(localized, "description"), mergeLocalized(metadata["description"], base.description))

        val license = if (metadata.containsKey("license")) text(metadata["license"]) ?: "Unknown" else base.license
        val author = if (metadata.containsKey("authorName")) text(metadata["authorName"]) ?: "Unknown" else base.author
        val website = if (metadata.containsKey("webSite")) text(metadata["webSite"]).orEmpty() else base.website
        val sourceCode = if (metadata.containsKey("sourceCode")) text(metadata["sourceCode"]).orEmpty() else base.sourceCode
        val added = if (metadata.containsKey("added")) long(metadata["added"]) ?: 0L else base.added
        val lastUpdated = if (metadata.containsKey("lastUpdated")) long(metadata["lastUpdated"]) ?: 0L else base.lastUpdated
        val category = if (metadata.containsKey("categories")) {
            (metadata["categories"] as? JsonArray)?.mapNotNull { text(it) }?.firstOrNull() ?: "Other"
        } else base.category
        val antiFeatures = if (metadata.containsKey("antiFeatures")) {
            mergeAntiFeatures(base.antiFeatures, metadata["antiFeatures"])
        } else base.antiFeatures
        val screenshots = if (metadata.containsKey("screenshots")) {
            resolveUrls(diff.baseUrl, collectScreenshotNames(metadata["screenshots"], mutableListOf()))
        } else base.screenshots
        val iconUrl = if (metadata.containsKey("icon")) {
            mergeIcon(diff.baseUrl, metadata["icon"], base.iconUrl)
        } else {
            base.iconUrl.ifBlank { "${diff.baseUrl}/icons/$pkg.png" }
        }

        appDao.upsertApp(
            base.copy(
                name = name.ifBlank { pkg },
                summary = summary,
                description = description,
                iconUrl = iconUrl,
                license = license,
                category = category,
                author = author,
                website = website,
                sourceCode = sourceCode,
                added = added,
                lastUpdated = lastUpdated,
                screenshots = screenshots,
                antiFeatures = antiFeatures
            )
        )
    }

    private fun mergeVariant(
        diff: RepoIndexDiff,
        pkg: String,
        versionId: String,
        version: JsonObject,
        existing: AppVariant?
    ): AppVariant? {
        val file = version["file"] as? JsonObject
        val manifest = version["manifest"] as? JsonObject

        val fileName = text(file?.get("name")) ?: text(version["apkName"])
        val fileHash = text(file?.get("sha256")) ?: text(version["sha256"]) ?: text(version["sha256sum"])
        val fileSize = long(file?.get("size")) ?: long(version["size"])
        val versionCode = int(manifest?.get("versionCode")) ?: int(version["versionCode"])
        val versionName = text(manifest?.get("versionName")) ?: text(version["versionName"])
        val usesSdk = (manifest?.get("usesSdk") ?: manifest?.get("sdk")) as? JsonObject
        val minSdk = int(usesSdk?.get("minSdkVersion")) ?: int(usesSdk?.get("min"))
        val nativecode = when (val element = manifest?.get("nativecode") ?: version["nativecode"]) {
            is JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { text(it) }
            else -> null
        }

        val resolvedName = fileName ?: existing?.apkUrl?.substringAfterLast('/')
        val resolvedCode = versionCode ?: existing?.versionCode
        if (resolvedName == null || resolvedCode == null) {
            Log.w(TAG, "Skipping version $versionId of $pkg, no file name or version code in diff")
            return null
        }

        val previousCompatible = existing?.isCompatible ?: true
        val compatible = when {
            minSdk != null && nativecode != null -> compatible(minSdk, nativecode)
            minSdk != null -> Build.VERSION.SDK_INT >= minSdk && previousCompatible
            nativecode != null -> abiCompatible(nativecode) && previousCompatible
            else -> previousCompatible
        }

        return AppVariant(
            packageName = pkg,
            repositoryUrl = diff.baseUrl,
            repositoryName = existing?.repositoryName ?: diff.repoName,
            versionName = versionName ?: existing?.versionName ?: "1.0",
            versionCode = resolvedCode,
            apkUrl = resolveUrl(diff.baseUrl, fileName ?: existing?.apkUrl.orEmpty()),
            sha256 = fileHash ?: existing?.sha256 ?: versionId,
            size = fileSize ?: existing?.size ?: 0L,
            isCompatible = compatible,
            reproducible = if (version.containsKey("reproducible")) {
                (version["reproducible"] as? JsonPrimitive)?.booleanOrNull == true
            } else {
                existing?.reproducible ?: false
            },
            releaseChannels = if (version.containsKey("releaseChannels")) {
                (version["releaseChannels"] as? JsonArray)?.mapNotNull { text(it) } ?: emptyList()
            } else {
                existing?.releaseChannels ?: emptyList()
            }
        )
    }

    private suspend fun refreshDerivedFields(diff: RepoIndexDiff, pkg: String, versions: JsonObject?) {
        val row = appDao.getApp(pkg) ?: return
        if (row.repositoryUrl != diff.baseUrl) return

        val variants = appDao.variantsForRepo(pkg, diff.baseUrl)
        val best = bestVariant(variants)
        var whatsNew = row.whatsNew
        var antiFeatures = row.antiFeatures
        if (best != null && versions != null) {
            val versionDiff = versions.entries
                .firstOrNull { it.key.equals(best.sha256, ignoreCase = true) }
                ?.value as? JsonObject
            if (versionDiff != null) {
                if (versionDiff.containsKey("whatsNew")) {
                    whatsNew = mergeLocalized(versionDiff["whatsNew"], row.whatsNew)
                }
                if (versionDiff.containsKey("antiFeatures")) {
                    antiFeatures = mergeAntiFeatures(antiFeatures, versionDiff["antiFeatures"])
                }
            }
        }

        appDao.upsertApp(
            if (best == null) {
                row.copy(isCompatible = variants.any { it.isCompatible })
            } else {
                row.copy(
                    version = best.versionName,
                    versionCode = best.versionCode,
                    size = best.size,
                    apkUrl = best.apkUrl,
                    sha256 = best.sha256,
                    whatsNew = whatsNew,
                    antiFeatures = antiFeatures,
                    isCompatible = variants.any { it.isCompatible }
                )
            }
        )
    }

    private fun bestVariant(variants: List<AppVariant>): AppVariant? {
        var best: AppVariant? = null
        for (variant in variants) {
            best = when {
                best == null -> variant
                variant.versionCode > best.versionCode -> variant
                variant.versionCode == best.versionCode &&
                        variant.size in 1..Long.MAX_VALUE &&
                        best.size in 1..Long.MAX_VALUE &&
                        variant.size < best.size -> variant

                else -> best
            }
        }
        return best
    }

    private fun mergeAntiFeatures(existing: List<String>, element: JsonElement?): List<String> = when (element) {
        null -> existing
        is JsonNull -> emptyList()
        is JsonArray -> element.mapNotNull { text(it) }.distinct().sorted()
        is JsonObject -> {
            val removed = element.entries.filter { it.value is JsonNull }.map { it.key }
            val added = element.entries.filter { it.value !is JsonNull }.map { it.key }
            (existing - removed.toSet() + added).distinct().sorted()
        }

        else -> existing
    }

    private fun mergeIcon(baseUrl: String, element: JsonElement?, stored: String): String {
        val picked = when (element) {
            null, is JsonNull -> null
            is JsonPrimitive -> text(element)
            is JsonObject -> {
                val candidates = element.mapNotNull { (locale, value) ->
                    val name = text(value) ?: text((value as? JsonObject)?.get("name"))
                    if (name == null) null else name to JsonPrimitive(locale)
                }
                pickBest(candidates)
            }

            else -> null
        }
        if (picked.isNullOrBlank()) return stored
        return resolveUrl(baseUrl, picked)
    }

    private fun mergeLocalized(element: JsonElement?, stored: String): String {
        if (element == null) return stored
        return when (element) {
            is JsonNull -> ""
            is JsonPrimitive -> element.contentOrNull ?: stored
            is JsonObject -> {
                val candidates = element.mapNotNull { (locale, value) ->
                    val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                    if (text == null) null else text to JsonPrimitive(locale)
                }
                pickBest(candidates) ?: stored
            }

            else -> stored
        }
    }

    private fun localizedField(localized: JsonObject?, field: String): JsonObject? {
        if (localized == null) return null
        val entries = localized.mapNotNull { (locale, value) ->
            val text = (value as? JsonObject)?.get(field)
            if (text == null) null else locale to text
        }
        return if (entries.isEmpty()) null else JsonObject(entries.toMap())
    }

    private fun pickBest(candidates: List<Pair<String, JsonElement>>): String? {
        val device = locales[0]
        val deviceTag = device?.toLanguageTag()?.lowercase(Locale.ROOT)
        val languageTag = device?.language?.lowercase(Locale.ROOT)
        var bestScore = 3
        var best: String? = null
        candidates.forEach { (value, localeElement) ->
            val locale = (localeElement as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val score = localeScore(locale, deviceTag, languageTag)
            if (score < bestScore) {
                bestScore = score
                best = value
            }
        }
        return best
    }

    private fun localeScore(locale: String, deviceTag: String?, languageTag: String?): Int {
        val tag = locale.replace('_', '-').lowercase(Locale.ROOT)
        return when {
            deviceTag != null && tag == deviceTag -> 0
            languageTag != null && tag.substringBefore('-') == languageTag -> 1
            tag == "en-us" || tag == "en" -> 2
            else -> 3
        }
    }

    private fun collectScreenshotNames(element: JsonElement?, out: MutableList<String>): List<String> {
        when (element) {
            is JsonArray -> element.forEach { entry ->
                when (entry) {
                    is JsonPrimitive -> entry.contentOrNull?.let { out.add(it) }
                    is JsonObject -> {
                        val name = text(entry["name"])
                        if (name != null) out.add(name) else collectScreenshotNames(entry, out)
                    }

                    is JsonArray -> collectScreenshotNames(entry, out)
                    else -> Unit
                }
            }

            is JsonObject -> element.values.forEach { value ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNull?.let { out.add(it) }
                    is JsonObject -> collectScreenshotNames(value, out)
                    is JsonArray -> collectScreenshotNames(value, out)
                    else -> Unit
                }
            }

            else -> Unit
        }
        return out
    }

    private fun resolveUrls(baseUrl: String, paths: List<String>): List<String> =
        paths.map { resolveUrl(baseUrl, it) }

    private fun resolveUrl(baseUrl: String, path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> "$baseUrl$path"
        else -> "$baseUrl/$path"
    }

    private fun compatible(minSdkVersion: Int, nativecode: List<String>): Boolean =
        Build.VERSION.SDK_INT >= minSdkVersion && abiCompatible(nativecode)

    private fun abiCompatible(nativecode: List<String>): Boolean = nativecode.isEmpty() ||
            nativecode.any { repoAbi -> Build.SUPPORTED_ABIS.any { it.equals(repoAbi, ignoreCase = true) } }

    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun long(element: JsonElement?): Long? =
        (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun int(element: JsonElement?): Int? =
        (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}

/**
 * Updates timestamp and fingerprint of a repository row without touching its other fields.
 * A null timestamp or an empty fingerprint keeps the stored value.
 */
suspend fun mergeRepositoryRow(
    dao: RepositoryDao,
    baseUrl: String,
    timestamp: Long?,
    fingerprint: String?
) {
    val base = baseUrl.trim().trimEnd('/')
    if (base.isBlank()) return
    val existing = dao.get(base)
    if (existing == null && timestamp == null && fingerprint.isNullOrEmpty()) return
    val entity = (existing ?: RepositoryEntity(baseUrl = base)).copy(
        timestamp = timestamp ?: existing?.timestamp ?: 0L,
        fingerprint = fingerprint?.takeIf { it.isNotEmpty() } ?: existing?.fingerprint.orEmpty()
    )
    dao.upsert(entity)
}

@Serializable
private data class DiffEnvelope(
    val repo: JsonObject? = null,
    val packages: Map<String, JsonElement?>? = null
)

private sealed interface DiffEvent {
    class Repo(val repo: JsonObject) : DiffEvent

    class Package(val name: String, val value: JsonElement) : DiffEvent
}

/**
 * Streaming decoder ported from the reference client's IndexV2DiffStreamProcessor: it walks the
 * diff payload one top-level entry at a time, so only the current package is materialized.
 * [DiffEnvelope] stands in for IndexV2, so the element indices match the wire format.
 */
@OptIn(ExperimentalSerializationApi::class)
private class DiffStreamSerializer(
    private val events: SendChannel<DiffEvent>
) : KSerializer<DiffEnvelope?> {

    override val descriptor: SerialDescriptor = DiffEnvelope.serializer().descriptor

    override fun deserialize(decoder: Decoder): DiffEnvelope? {
        decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")

        decoder.beginStructure(descriptor)
        val repoIndex = descriptor.getElementIndex("repo")
        val packagesIndex = descriptor.getElementIndex("packages")

        when (val startIndex = decoder.decodeElementIndex(descriptor)) {
            repoIndex -> {
                diffRepo(decoder, startIndex)
                val index = decoder.decodeElementIndex(descriptor)
                if (index == packagesIndex) diffPackages(decoder, index)
            }

            packagesIndex -> {
                diffPackages(decoder, startIndex)
                val index = decoder.decodeElementIndex(descriptor)
                if (index == repoIndex) diffRepo(decoder, index)
            }

            else -> error("Unexpected startIndex: $startIndex")
        }
        var currentIndex = 0
        while (currentIndex != CompositeDecoder.DECODE_DONE) {
            currentIndex = decoder.decodeElementIndex(descriptor)
        }
        decoder.endStructure(descriptor)
        return null
    }

    private fun diffRepo(decoder: JsonDecoder, index: Int) {
        require(index == descriptor.getElementIndex("repo"))
        send(DiffEvent.Repo(decoder.decodeJsonElement().jsonObject))
    }

    private fun diffPackages(decoder: JsonDecoder, index: Int) {
        require(index == descriptor.getElementIndex("packages"))
        val mapDescriptor = descriptor.getElementDescriptor(index)
        val compositeDecoder = decoder.beginStructure(mapDescriptor)
        while (true) {
            val packageIndex = compositeDecoder.decodeElementIndex(descriptor)
            if (packageIndex == CompositeDecoder.DECODE_DONE) break
            readMapEntry(compositeDecoder, packageIndex)
        }
        compositeDecoder.endStructure(mapDescriptor)
    }

    private fun readMapEntry(decoder: CompositeDecoder, index: Int) {
        val packageName = decoder.decodeStringElement(descriptor, index)
        decoder.decodeElementIndex(descriptor)
        val packageV2 = decoder.decodeSerializableElement(
            descriptor = descriptor,
            index = index + 1,
            deserializer = JsonElement.serializer()
        )
        send(DiffEvent.Package(packageName, packageV2))
    }

    private fun send(event: DiffEvent) {
        runBlocking { events.send(event) }
    }

    override fun serialize(encoder: Encoder, value: DiffEnvelope?) {
        error("Not implemented")
    }
}
