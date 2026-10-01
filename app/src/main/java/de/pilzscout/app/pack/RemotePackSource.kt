package de.pilzscout.app.pack

import de.pilzscout.app.BuildConfig
import de.pilzscout.app.data.species.SpeciesDatabase
import de.pilzscout.core.model.PackCatalog
import de.pilzscout.core.model.PackCatalogEntry
import de.pilzscout.core.model.PackManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the pack from the pack repository (a Hugging Face dataset repo, or a local mirror with the
 * same URL layout; see tools/src/mushroom_packs/publish.py):
 *
 *     <base>/resolve/main/catalog.json            every published pack, newest first
 *     <base>/resolve/<revision>/<version>/<path>  pack files, pinned to the commit they were published in
 *
 * Only the catalog is read from the branch. The manifest is checked against the SHA-256 in the catalog,
 * and every file against the manifest, so a file served from a pinned commit cannot change under the app.
 * The last manifest is cached in filesDir so the app knows its pack while offline.
 */
@Singleton
class RemotePackSource @Inject constructor(
    private val files: PackFiles,
    private val json: Json,
) : PackSource {
    override val id: String = "remote"
    override val remote: Boolean = true

    private val baseUrl = BuildConfig.PACK_BASE_URL.trimEnd('/')

    @Volatile
    private var entry: PackCatalogEntry? = null

    override suspend fun manifest(refresh: Boolean): PackManifest = withContext(Dispatchers.IO) {
        val cached = readCache()
        val fresh = cached != null && System.currentTimeMillis() - files.remoteManifest.lastModified() < CHECK_INTERVAL_MS
        if (cached != null && !refresh && fresh) return@withContext cached
        try {
            fetch()
        } catch (e: IOException) {
            // Offline or the repository is unreachable: keep working with the pack we already know.
            if (e is NoCompatiblePackException || cached == null) throw e
            cached
        }
    }

    private fun fetch(): PackManifest {
        val catalog = json.decodeFromString(PackCatalog.serializer(), get("$baseUrl/resolve/main/catalog.json").decodeToString())
        if (catalog.formatVersion > PackCatalog.SUPPORTED_FORMAT) throw NoCompatiblePackException("Catalog format ${catalog.formatVersion} is too new")
        val e = catalog.newestCompatible(SpeciesDatabase.VERSION, BuildConfig.VERSION_CODE)
            ?: throw NoCompatiblePackException("No pack for schema ${SpeciesDatabase.VERSION} and app ${BuildConfig.VERSION_CODE}")
        val bytes = get(url(e, e.manifest))
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (sha != e.manifestSha256) throw IOException("Manifest checksum mismatch for ${e.packVersion}")
        val manifest = json.decodeFromString(PackManifest.serializer(), bytes.decodeToString())
        files.root.mkdirs()
        writeAtomically(files.remoteManifest, bytes)
        writeAtomically(files.remoteEntry, json.encodeToString(PackCatalogEntry.serializer(), e).toByteArray())
        entry = e
        return manifest
    }

    private fun readCache(): PackManifest? = runCatching {
        if (!files.remoteManifest.exists() || !files.remoteEntry.exists()) return null
        val e = json.decodeFromString(PackCatalogEntry.serializer(), files.remoteEntry.readText())
        val m = json.decodeFromString(PackManifest.serializer(), files.remoteManifest.readText())
        if (e.schemaVersion != SpeciesDatabase.VERSION || m.packVersion != e.packVersion) return null
        entry = e
        m
    }.getOrNull()

    override fun open(path: String, offset: Long): PackStream {
        val e = entry ?: throw IOException("Pack manifest not loaded")
        val versionPath = "${e.packVersion}/$path"
        val conn = connect(url(e, versionPath), offset)
        return when (conn.responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> PackStream(conn.inputStream, offset)
            HttpURLConnection.HTTP_OK -> PackStream(conn.inputStream, 0)
            416 -> {
                // Requested range beyond the end: the partial file is stale or complete; start over.
                conn.disconnect()
                val again = connect(url(e, versionPath), 0)
                if (again.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(again.responseCode, versionPath)
                PackStream(again.inputStream, 0)
            }
            else -> {
                val code = conn.responseCode
                conn.disconnect()
                throw HttpStatusException(code, versionPath)
            }
        }
    }

    private fun url(e: PackCatalogEntry, path: String) = "$baseUrl/resolve/${e.revision}/$path"

    private fun get(url: String): ByteArray {
        val conn = connect(url, 0)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpStatusException(conn.responseCode, url)
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun connect(url: String, offset: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true // the Hub redirects /resolve/ to its CDN
            setRequestProperty("User-Agent", "PilzScout/${BuildConfig.VERSION_NAME} (Android)")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) throw IOException("Could not write ${target.name}")
    }

    companion object {
        /** How often the catalog is checked for a newer pack without an explicit refresh. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
