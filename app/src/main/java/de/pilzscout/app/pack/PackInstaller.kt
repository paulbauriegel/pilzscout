package de.pilzscout.app.pack

import de.pilzscout.core.model.PackComponent
import de.pilzscout.core.model.PackComponentManifest
import de.pilzscout.core.model.PackFile
import de.pilzscout.core.pack.TarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import javax.inject.Inject

@Serializable
data class InstalledComponent(val id: String, val version: String, val packVersion: String, val bytes: Long, val installedAt: Long)

data class InstallProgress(val component: PackComponent, val bytesDone: Long, val bytesTotal: Long) {
    val fraction: Float get() = if (bytesTotal == 0L) 1f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

/**
 * Installs one component from a PackSource into filesDir/packs/<component>/.
 *
 * The component is assembled in filesDir/packs/.staging/<component>/ and swapped in only once every file
 * has matched its SHA-256, so a failed or cancelled install (a dropped connection, a full disk) leaves the
 * previously installed version working. Files that did not change are taken from the installed version
 * instead of being downloaded again. Remote installs of components with an archive download that one
 * file (resumable, named by its hash) and unpack it; everything else is fetched file by file, also
 * resumable through .part files that survive in the staging directory.
 */
class PackInstaller @Inject constructor(
    private val files: PackFiles,
    private val json: Json,
) {
    fun install(source: PackSource, packVersion: String, component: PackComponentManifest): Flow<InstallProgress> = flow {
        val kind = component.component
        val staging = files.stagingDir(kind)
        val useArchive = source.remote && component.archive != null
        val total = if (useArchive) component.archive!!.bytes + component.bytes else component.bytes
        ensureSpace(component, useArchive)
        staging.mkdirs()
        removeUnlisted(staging, component)
        val progress = Progress(this, kind, total)
        progress.emitNow()

        if (useArchive && !reuseInstalled(component, progress)) {
            installFromArchive(source, component, progress)
        } else {
            for (pf in component.files) installFile(source, kind, pf, progress)
        }

        File(staging, "installed.json").writeText(
            json.encodeToString(
                InstalledComponent.serializer(),
                InstalledComponent(kind.id, component.version, packVersion, component.bytes, System.currentTimeMillis()),
            ),
        )
        swapIn(kind)
        emit(InstallProgress(kind, total, total))
    }.flowOn(Dispatchers.IO)

    /** Copies or downloads one file into staging, unless an identical copy is already there. */
    private suspend fun installFile(source: PackSource, kind: PackComponent, pf: PackFile, progress: Progress) {
        val target = files.stagingFile(kind, pf.path)
        if (matches(target, pf)) return progress.add(pf.bytes)
        if (linkInstalled(files.file(pf.path), target, pf)) return progress.add(pf.bytes)
        download(source, pf.path, pf.sha256, pf.bytes, target, progress)
    }

    /**
     * Streams [path] into [target] via [target].part, resuming a previous partial download when the source
     * supports it. Throws if the result does not match [sha256].
     */
    private suspend fun download(source: PackSource, path: String, sha256: String, bytes: Long, target: File, progress: Progress) {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        if (part.length() > bytes) part.delete()
        for (attempt in 0..1) {
            val digest = MessageDigest.getInstance("SHA-256")
            var have = if (part.exists()) part.length() else 0L
            if (have > 0) part.inputStream().use { copy(it, null, digest) }
            val start = progress.done
            source.open(path, have).use { stream ->
                if (stream.offset != have) {
                    // The source restarted from the beginning (no range support, or assets).
                    have = 0
                    digest.reset()
                }
                progress.add(have)
                FileOutputStream(part, have > 0).buffered(BUFFER).use { out ->
                    copy(stream.input, out, digest) { n -> progress.add(n) }
                }
            }
            if (digest.digest().toHex() == sha256 && part.length() == bytes) {
                if (!part.renameTo(target)) throw IOException("Could not move ${part.name} into place")
                return
            }
            // A resumed download can combine bytes of two different files; retry once from scratch.
            part.delete()
            progress.reset(start)
            if (have == 0L) break
        }
        throw IOException("Checksum mismatch for $path")
    }

    /** Downloads the component archive (resumable) and unpacks it into staging, verifying every entry. */
    private suspend fun installFromArchive(source: PackSource, component: PackComponentManifest, progress: Progress) {
        val kind = component.component
        val archive = component.archive!!
        files.downloads.mkdirs()
        val local = File(files.downloads, "${archive.sha256}.tar")
        if (!(local.length() == archive.bytes && sha256(local) == archive.sha256)) {
            download(source, archive.path, archive.sha256, archive.bytes, local, progress)
        } else {
            progress.add(archive.bytes)
        }
        val expected = component.files.associateBy { it.path }
        val seen = HashSet<String>()
        local.inputStream().buffered(BUFFER).use { input ->
            val tar = TarReader(input)
            while (true) {
                val entry = tar.next() ?: break
                val pf = expected[entry.name] ?: throw IOException("Unexpected file in ${archive.path}: ${entry.name}")
                if (entry.size != pf.bytes) throw IOException("Size mismatch for ${entry.name}")
                val target = files.stagingFile(kind, pf.path)
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                val part = File(target.path + ".part")
                part.outputStream().buffered(BUFFER).use { out -> copy(tar.stream(), out, digest) { n -> progress.add(n) } }
                if (digest.digest().toHex() != pf.sha256) {
                    part.delete()
                    throw IOException("Checksum mismatch for ${entry.name}")
                }
                if (!part.renameTo(target)) throw IOException("Could not move ${part.name} into place")
                seen += entry.name
            }
        }
        val missing = expected.keys - seen
        if (missing.isNotEmpty()) throw IOException("${archive.path} is missing ${missing.size} files, e.g. ${missing.first()}")
        local.delete()
    }

    /**
     * An update whose files all exist unchanged in the installed version (e.g. only the pack version moved)
     * needs no archive download.
     */
    private suspend fun reuseInstalled(component: PackComponentManifest, progress: Progress): Boolean {
        val kind = component.component
        if (component.files.any { !files.file(it.path).let { f -> f.exists() && f.length() == it.bytes } }) return false
        for (pf in component.files) {
            val target = files.stagingFile(kind, pf.path)
            if (!matches(target, pf) && !linkInstalled(files.file(pf.path), target, pf)) return false
        }
        progress.add(component.archive!!.bytes + component.bytes)
        return true
    }

    /** Hard-links (or copies) an installed file into staging if it matches; no extra storage when linking works. */
    private fun linkInstalled(installed: File, target: File, pf: PackFile): Boolean {
        if (!matches(installed, pf)) return false
        target.parentFile?.mkdirs()
        target.delete()
        try {
            Files.createLink(target.toPath(), installed.toPath())
        } catch (e: Exception) {
            installed.copyTo(target, overwrite = true)
        }
        return true
    }

    /** Moves staging/<component> into place; the old directory goes to the trash and is deleted. */
    private fun swapIn(kind: PackComponent) {
        val dir = files.componentDir(kind)
        val staging = files.stagingDir(kind)
        files.trash.mkdirs()
        val old = File(files.trash, "${kind.id}-${System.nanoTime()}")
        if (dir.exists() && !dir.renameTo(old)) throw IOException("Could not replace ${kind.id}")
        if (!staging.renameTo(dir)) {
            old.renameTo(dir)
            throw IOException("Could not move ${kind.id} into place")
        }
        old.deleteRecursively()
    }

    /** Leftovers from crashed swaps and abandoned stagings of components that are no longer wanted. */
    fun cleanUp() {
        files.trash.deleteRecursively()
    }

    private fun ensureSpace(component: PackComponentManifest, useArchive: Boolean) {
        val needed = component.bytes + (if (useArchive) component.archive!!.bytes else 0L) + SPACE_MARGIN
        files.root.mkdirs()
        if (files.root.usableSpace < needed) throw InsufficientStorageException(needed)
    }

    /** Staged files that a newer manifest no longer lists (a staging left over from an older attempt). */
    private fun removeUnlisted(staging: File, component: PackComponentManifest) {
        val keep = component.files.flatMap {
            val f = files.stagingFile(component.component, it.path).canonicalPath
            listOf(f, "$f.part")
        }.toSet()
        staging.walkBottomUp().forEach { f ->
            if (f.isFile && f.canonicalPath !in keep) f.delete()
            else if (f.isDirectory && f != staging && f.listFiles()?.isEmpty() == true) f.delete()
        }
    }

    fun uninstall(component: PackComponent) {
        files.componentDir(component).deleteRecursively()
        files.stagingDir(component).deleteRecursively()
    }

    fun readInstalled(component: PackComponent): InstalledComponent? {
        val marker = files.installedMarker(component)
        if (!marker.exists()) return null
        return runCatching { json.decodeFromString(InstalledComponent.serializer(), marker.readText()) }.getOrNull()
    }

    private fun matches(file: File, pf: PackFile) = file.exists() && file.length() == pf.bytes && sha256(file) == pf.sha256

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { copy(it, null, digest) }
        return digest.digest().toHex()
    }

    private inline fun copy(input: InputStream, out: OutputStream?, digest: MessageDigest, onBytes: (Int) -> Unit = {}) {
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out?.write(buf, 0, n)
            digest.update(buf, 0, n)
            onBytes(n)
        }
    }

    /** Byte counter that emits at most every [EMIT_EVERY_BYTES] and checks for cancellation. */
    private class Progress(private val collector: FlowCollector<InstallProgress>, private val kind: PackComponent, private val total: Long) {
        var done = 0L
            private set
        private var lastEmit = 0L

        suspend fun add(n: Long) {
            done += n
            if (done - lastEmit >= EMIT_EVERY_BYTES) emitNow()
        }

        suspend fun add(n: Int) = add(n.toLong())

        fun reset(to: Long) {
            done = to
            lastEmit = to
        }

        suspend fun emitNow() {
            currentCoroutineContext().ensureActive()
            lastEmit = done
            collector.emit(InstallProgress(kind, done, total))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val EMIT_EVERY_BYTES = 512L * 1024
        private const val BUFFER = 1 shl 16
        private const val SPACE_MARGIN = 20L * 1024 * 1024
    }
}
