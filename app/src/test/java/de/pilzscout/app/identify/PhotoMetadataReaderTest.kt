package de.pilzscout.app.identify

import de.pilzscout.app.identify.PhotoMetadataReader.Companion.parseExifDateTime
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoMetadataReaderTest {
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `wall-clock time without offset is read in the fallback zone`() {
        val expected = ZonedDateTime.of(2025, 9, 14, 23, 30, 0, 0, berlin).toInstant().toEpochMilli()
        assertEquals(expected, parseExifDateTime("2025:09:14 23:30:00", null, berlin))
    }

    @Test
    fun `offset tag wins over the fallback zone`() {
        val expected = ZonedDateTime.of(2025, 9, 14, 23, 30, 0, 0, ZoneId.of("-03:00")).toInstant().toEpochMilli()
        assertEquals(expected, parseExifDateTime("2025:09:14 23:30:00", "-03:00", berlin))
    }

    @Test
    fun `placeholders and junk yield null`() {
        assertNull(parseExifDateTime(null, null, berlin))
        assertNull(parseExifDateTime("", null, berlin))
        assertNull(parseExifDateTime("0000:00:00 00:00:00", null, berlin))
        assertNull(parseExifDateTime("2025-09-14T23:30:00", null, berlin))
    }
}
