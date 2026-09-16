package de.pilzscout.app.identify

import org.junit.Test
import kotlin.test.assertEquals

class CaptureGuidanceTest {
    @Test
    fun `no photos asks for the first shot`() {
        assertEquals(CaptureGuidance.START, guidanceFor(0))
        assertEquals(CaptureGuidance.START, guidanceFor(-1))
    }

    @Test
    fun `one and two photos ask for further views`() {
        assertEquals(CaptureGuidance.SECOND, guidanceFor(1))
        assertEquals(CaptureGuidance.THIRD, guidanceFor(2))
    }

    @Test
    fun `three or more photos count as covered`() {
        assertEquals(CaptureGuidance.COVERED, guidanceFor(3))
        assertEquals(CaptureGuidance.COVERED, guidanceFor(8))
    }
}
