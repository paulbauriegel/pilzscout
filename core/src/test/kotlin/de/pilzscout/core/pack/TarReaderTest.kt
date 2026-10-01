package de.pilzscout.core.pack

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TarReaderTest {
    /** Written by tools' `write_tar` (Python tarfile, ustar), so this also checks the two sides agree. */
    private fun sample(): ByteArray = javaClass.getResourceAsStream("/pack/sample.tar")!!.readBytes()

    @Test
    fun readsEntriesWrittenByThePackBuilder() {
        val reader = TarReader(ByteArrayInputStream(sample()))
        val seen = mutableMapOf<String, ByteArray>()
        while (true) {
            val e = reader.next() ?: break
            val bytes = reader.stream().readBytes()
            assertEquals(e.size, bytes.size.toLong())
            seen[e.name] = bytes
        }
        val longName = "wiki/" + "d".repeat(100) + "/e.webp"
        assertEquals(setOf("wiki/thumbs/a.webp", "wiki/thumbs/b.bin", longName), seen.keys)
        assertContentEquals("hello".toByteArray(), seen["wiki/thumbs/a.webp"])
        assertContentEquals(ByteArray(768) { (it % 256).toByte() }, seen["wiki/thumbs/b.bin"])
        assertEquals(0, seen[longName]!!.size)
    }

    @Test
    fun skipsUnreadEntries() {
        val reader = TarReader(ByteArrayInputStream(sample()))
        val names = generateSequence { reader.next()?.name }.toList()
        assertEquals(3, names.size)
        assertNull(reader.next())
    }

    @Test
    fun rejectsCorruptHeaders() {
        val bytes = sample().also { it[0] = 'X'.code.toByte() }
        assertFailsWith<IOException> { TarReader(ByteArrayInputStream(bytes)).next() }
    }

    @Test
    fun rejectsTruncatedArchives() {
        // Entries are sorted: the empty long-named file (one header block), then a.webp (header at 512, data at 1024).
        val padding = TarReader(ByteArrayInputStream(sample().copyOf(1024 + 100)))
        assertFailsWith<IOException> { repeat(3) { padding.next() } }
        val data = TarReader(ByteArrayInputStream(sample().copyOf(1024 + 2)))
        assertFailsWith<IOException> { data.next(); data.next(); data.stream().readBytes() }
    }
}
