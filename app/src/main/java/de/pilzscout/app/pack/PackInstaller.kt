package de.pilzscout.app.pack

import de.pilzscout.core.model.PackComponent
import de.pilzscout.core.model.PackComponentManifest
import de.pilzscout.core.model.PackFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

@Serializable
data class InstalledComponent(val id: String, val version: String, val packVersion: String, val bytes: Long, val installedAt: Long)

data class InstallProgress(val component: PackComponent, val bytesDone: Long, val bytesTotal: Long) {
    val fraction: Float get() = if (bytesTotal == 0L) 1f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

/**
 * Copies one component's files from a PackSource into filesDir/packs/<component>/, verifying SHA-256,
 * then writes installed.json. Files are written to a .part name and renamed so a crash never leaves a
 * half-written file that looks valid.
 */
class PackInstaller @Inject constructor(
    private val files: PackFiles,
    private val json: Json,
) {
    fun install(source: PackSource, packVersion: String, component: PackComponentManifest): Flow<InstallProgress> = flow {
        val kind = component.component
        val dir = files.componentDir(kind)
        dir.mkdirs()
        files.installedMarker(kind).delete()
        val total = component.files.sumOf { it.bytes }
        var done = 0L
        emit(InstallProgress(kind, 0, total))
        for (pf in component.files) {
            val target = files.file(pf.path)
            if (target.exists() && target.length() == pf.bytes && sha256(target) == pf.sha256) {
                done += pf.bytes
                emit(InstallProgress(kind, done, total))
                continue
            }
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            source.open(pf.path).use { input ->
                part.outputStream().buffered(1 shl 16).use { output ->
                    val buf = ByteArray(1 shl 16)
                    var lastEmit = done
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done - lastEmit >= EMIT_EVERY_BYTES) {
                            lastEmit = done
                            emit(InstallProgress(kind, done, total))
                        }
                    }
                }
            }
            val actual = digest.digest().toHex()
            if (actual != pf.sha256) {
                part.delete()
                throw IOException("Checksum mismatch for ${pf.path}: expected ${pf.sha256}, got $actual")
            }
            if (!part.renameTo(target)) throw IOException("Could not move ${part.name} into place")
            emit(InstallProgress(kind, done, total))
        }
        removeStaleFiles(dir, component)
        files.installedMarker(kind).writeText(
            json.encodeToString(
                InstalledComponent.serializer(),
                InstalledComponent(kind.id, component.version, packVersion, total, System.currentTimeMillis()),
            ),
        )
        emit(InstallProgress(kind, total, total))
    }.flowOn(Dispatchers.IO)

    /** Deletes files from a previous pack version that the current manifest no longer lists. */
    private fun removeStaleFiles(dir: File, component: PackComponentManifest) {
        val keep = component.files.map { files.file(it.path).canonicalPath }.toSet()
        dir.walkBottomUp().forEach { f ->
            if (f.isFile && f.name != "installed.json" && f.canonicalPath !in keep) f.delete()
            else if (f.isDirectory && f != dir && f.listFiles()?.isEmpty() == true) f.delete()
        }
    }

    fun uninstall(component: PackComponent) {
        files.componentDir(component).deleteRecursively()
    }

    fun readInstalled(component: PackComponent): InstalledComponent? {
        val marker = files.installedMarker(component)
        if (!marker.exists()) return null
        return runCatching { json.decodeFromString(InstalledComponent.serializer(), marker.readText()) }.getOrNull()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1 shl 16).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun currentCoroutineContext() = kotlin.coroutines.coroutineContext

    companion object {
        private const val EMIT_EVERY_BYTES = 512L * 1024
        fun PackFile.sizeOrZero(): Long = bytes
    }
}
