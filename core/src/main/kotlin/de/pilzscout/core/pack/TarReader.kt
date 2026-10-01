package de.pilzscout.core.pack

import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Minimal streaming reader for the uncompressed ustar archives written by `packs package`
 * (regular files and directories only, names up to 255 characters via the ustar prefix field).
 * Anything else (links, PAX or GNU extensions) is rejected, as are absolute names and `..`.
 */
class TarReader(private val input: InputStream) {
    private var remaining = 0L
    private var padding = 0L
    private var current: InputStream? = null

    data class Entry(val name: String, val size: Long)

    /** Advances to the next regular file; returns null at the end of the archive. */
    fun next(): Entry? {
        skipRest()
        while (true) {
            val header = ByteArray(BLOCK)
            if (!readBlock(header)) return null
            if (header.all { it == 0.toByte() }) return null
            if (!checksumOk(header)) throw IOException("Corrupt tar header")
            val type = header[156].toInt().toChar()
            val size = octal(header, 124, 12)
            val name = name(header)
            if (name.startsWith("/") || name.split('/').any { it == ".." }) throw IOException("Unsafe tar entry name: $name")
            when (type) {
                '0', '\u0000' -> {
                    remaining = size
                    padding = (BLOCK - size % BLOCK) % BLOCK
                    current = EntryStream()
                    return Entry(name, size)
                }
                '5' -> if (size != 0L) throw IOException("Directory entry with data: $name")
                else -> throw IOException("Unsupported tar entry type '$type' for $name")
            }
        }
    }

    /** The current entry's bytes. Valid until the next call to [next]. */
    fun stream(): InputStream = current ?: throw IllegalStateException("No current entry")

    private fun skipRest() {
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n <= 0) {
                if (input.read() < 0) throw EOFException("Truncated tar entry")
                remaining--
            } else {
                remaining -= n
            }
        }
        while (padding > 0) {
            if (input.read() < 0) throw EOFException("Truncated tar padding")
            padding--
        }
        current = null
    }

    private fun readBlock(buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) {
                if (off == 0) return false
                throw EOFException("Truncated tar header")
            }
            off += n
        }
        return true
    }

    private fun name(h: ByteArray): String {
        val base = cString(h, 0, 100)
        val magic = cString(h, 257, 6)
        val prefix = if (magic.startsWith("ustar")) cString(h, 345, 155) else ""
        return if (prefix.isEmpty()) base else "$prefix/$base"
    }

    private fun checksumOk(h: ByteArray): Boolean {
        val stored = octal(h, 148, 8)
        var sum = 0L
        for (i in h.indices) sum += if (i in 148 until 156) ' '.code else (h[i].toInt() and 0xff)
        return sum == stored
    }

    private inner class EntryStream : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = input.read()
            if (b < 0) throw EOFException("Truncated tar entry")
            remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) throw EOFException("Truncated tar entry")
            remaining -= n
            return n
        }

        override fun skip(n: Long): Long = throw UnsupportedOperationException()
        override fun available(): Int = minOf(input.available().toLong(), remaining).toInt()
        override fun close() = Unit
        override fun markSupported() = false
    }

    companion object {
        const val BLOCK = 512

        private fun cString(h: ByteArray, off: Int, len: Int): String {
            var end = off
            while (end < off + len && h[end] != 0.toByte()) end++
            return String(h, off, end - off, Charsets.UTF_8)
        }

        private fun octal(h: ByteArray, off: Int, len: Int): Long {
            val s = cString(h, off, len).trim()
            if (s.isEmpty()) return 0
            return s.toLongOrNull(8) ?: throw IOException("Bad octal field '$s' in tar header")
        }
    }
}
