package de.pilzscout.core.identify

import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FusionTest {
    private fun close(a: Float, b: Float, eps: Float = 1e-4f) = abs(a - b) < eps

    @Test
    fun singlePhotoIsPlainSoftmax() {
        val logits = floatArrayOf(1f, 2f, 3f)
        val p = Fusion.fuse(listOf(logits))
        val expected = Fusion.softmax(logits)
        assertTrue(p.indices.all { close(p[it], expected[it]) })
        assertTrue(close(p.sum(), 1f))
    }

    @Test
    fun geometricMeanOfTwoPhotos() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(ln(9f), 0f) // photo b: 90 % class 0
        val p = Fusion.fuse(listOf(a, b))
        // geometric mean of (0.5, 0.5) and (0.9, 0.1) -> sqrt(0.45):sqrt(0.05) = 3:1
        assertTrue(close(p[0], 0.75f, 1e-3f))
    }

    @Test
    fun maskAllowingNothingIsIgnored() {
        // A mask with no allowed class must not turn everything into NaN (JSON export would fail).
        val logits = floatArrayOf(1f, 2f, 3f)
        val p = Fusion.fuse(listOf(logits), mask = booleanArrayOf(false, false, false))
        assertTrue(p.all { it.isFinite() })
        val expected = Fusion.softmax(logits)
        assertTrue(p.indices.all { close(p[it], expected[it]) })
        assertTrue(Fusion.softmax(logits, booleanArrayOf(false, false, false)).all { it.isFinite() })
    }

    @Test
    fun maskedClassesGetZero() {
        val p = Fusion.fuse(listOf(floatArrayOf(10f, 0f, 0f)), mask = booleanArrayOf(false, true, true))
        assertEquals(0f, p[0])
        assertTrue(close(p[1], 0.5f))
        assertTrue(close(p.sum(), 1f))
    }

    @Test
    fun priorShiftsTowardsSeason() {
        val logits = floatArrayOf(0f, 0f)
        val prior = floatArrayOf(0.9f, 0.1f)
        val p = Fusion.fuse(listOf(logits), prior = prior, priorWeight = 1f)
        assertTrue(close(p[0], 0.9f, 1e-3f))
        val half = Fusion.fuse(listOf(logits), prior = prior, priorWeight = 0.5f)
        assertTrue(half[0] > 0.5f && half[0] < 0.9f)
    }

    @Test
    fun monthPriorIsSmoothedAndNormalised() {
        val hist = arrayOf(IntArray(12) { if (it == 8) 100 else 0 }, null)
        val prior = Fusion.monthPrior(hist, month = 9)
        assertTrue(close(prior.sum(), 1f))
        assertTrue(prior[0] > prior[1])
        val off = Fusion.monthPrior(hist, month = 1)
        assertTrue(off[0] < off[1]) // smoothed, but well below the flat prior
    }

    @Test
    fun descriptors() {
        assertEquals(ConfidenceDescriptor.STRONG, Descriptors.describe(floatArrayOf(0.82f, 0.08f, 0.03f)))
        assertEquals(ConfidenceDescriptor.SEVERAL_PLAUSIBLE, Descriptors.describe(floatArrayOf(0.30f, 0.28f, 0.20f)))
        assertEquals(ConfidenceDescriptor.UNCERTAIN, Descriptors.describe(floatArrayOf(0.40f, 0.15f, 0.05f)))
    }

    @Test
    fun alternativesBetweenTwoAndFour() {
        fun c(i: Int, p: Float) = Candidate(i, "s$i", p)
        val many = listOf(c(0, .5f), c(1, .2f), c(2, .1f), c(3, .05f), c(4, .04f), c(5, .03f), c(6, .02f))
        assertEquals(listOf(1, 2, 3, 4), Descriptors.alternatives(many).map { it.classIndex })
        val few = listOf(c(0, .9f), c(1, .01f), c(2, .005f), c(3, .001f))
        assertEquals(listOf(1, 2), Descriptors.alternatives(few).map { it.classIndex })
    }

    @Test
    fun agreement() {
        fun c(i: Int) = Candidate(i, null, 0.1f)
        assertEquals(Agreement.SUPPORTS, Descriptors.agreement(listOf(c(7), c(1)), 7))
        assertEquals(Agreement.PARTIAL, Descriptors.agreement(listOf(c(1), c(7)), 7))
        assertEquals(Agreement.CONFLICTS, Descriptors.agreement(listOf(c(1), c(2), c(3), c(7)), 7))
    }

    @Test
    fun recommendation() {
        assertEquals(
            ViewType.STEM_BASE,
            RecommendationEngine.recommend(setOf(ViewType.CAP, ViewType.UNDERSIDE), ConfidenceDescriptor.STRONG, setOf(Feature.RING)),
        )
        assertEquals(ViewType.UNDERSIDE, RecommendationEngine.recommend(setOf(ViewType.CAP), ConfidenceDescriptor.UNCERTAIN, emptySet()))
        assertNull(RecommendationEngine.recommend(setOf(ViewType.CAP), ConfidenceDescriptor.STRONG, emptySet()))
        assertNull(RecommendationEngine.recommend(ViewType.entries.toSet(), ConfidenceDescriptor.UNCERTAIN, setOf(Feature.CAP)))
    }
}
