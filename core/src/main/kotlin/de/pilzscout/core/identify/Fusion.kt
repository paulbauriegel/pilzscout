package de.pilzscout.core.identify

import kotlin.math.exp
import kotlin.math.ln

/**
 * Combines per-photo logits into one probability distribution.
 *
 * In plain words: each photo's probabilities are multiplied together (geometric mean of the
 * per-photo softmax), species not recorded in Germany are excluded, and the season is used as
 * a mild prior. All maths is in log space for stability.
 */
object Fusion {

    const val DEFAULT_SEASON_WEIGHT = 0.5f

    /**
     * @param photoLogits raw model outputs, one FloatArray per photo, all of the same length
     * @param mask true where a class is allowed (e.g. recorded in Germany); null = no masking
     * @param prior per-class prior probability for the current month (sums to 1); null = no prior
     * @param priorWeight lambda applied to log(prior)
     * @return combined probabilities (sum to 1 over allowed classes)
     */
    fun fuse(
        photoLogits: List<FloatArray>,
        mask: BooleanArray? = null,
        prior: FloatArray? = null,
        priorWeight: Float = DEFAULT_SEASON_WEIGHT,
    ): FloatArray {
        require(photoLogits.isNotEmpty()) { "at least one photo is required" }
        val n = photoLogits[0].size
        require(photoLogits.all { it.size == n }) { "all photos must have the same number of classes" }
        require(mask == null || mask.size == n)
        require(prior == null || prior.size == n)
        // A mask that allows nothing (e.g. a species DB whose class indices do not match this
        // model) would make every log-softmax NaN; ignore it instead of producing garbage.
        val mask = mask?.takeIf { m -> m.any { it } }

        val score = FloatArray(n)
        for (logits in photoLogits) {
            val logProbs = logSoftmax(logits, mask)
            for (i in 0 until n) score[i] += logProbs[i] / photoLogits.size
        }
        if (prior != null && priorWeight > 0f) {
            for (i in 0 until n) score[i] += priorWeight * ln(prior[i].coerceAtLeast(1e-9f))
        }
        return softmax(score, mask)
    }

    fun logSoftmax(logits: FloatArray, mask: BooleanArray? = null): FloatArray {
        val mask = mask?.takeIf { m -> m.any { it } }
        var max = Float.NEGATIVE_INFINITY
        for (i in logits.indices) if (mask == null || mask[i]) max = maxOf(max, logits[i])
        var sum = 0.0
        for (i in logits.indices) if (mask == null || mask[i]) sum += exp((logits[i] - max).toDouble())
        val logSum = ln(sum).toFloat() + max
        return FloatArray(logits.size) { i -> if (mask == null || mask[i]) logits[i] - logSum else Float.NEGATIVE_INFINITY }
    }

    fun softmax(logits: FloatArray, mask: BooleanArray? = null): FloatArray {
        val lp = logSoftmax(logits, mask)
        return FloatArray(lp.size) { i -> if (lp[i] == Float.NEGATIVE_INFINITY) 0f else exp(lp[i]) }
    }

    /** Indices of the k largest values, descending. */
    fun topK(values: FloatArray, k: Int): IntArray {
        val idx = values.indices.sortedByDescending { values[it] }
        return idx.take(k).toIntArray()
    }

    /** Laplace-smoothed month prior from per-class 12-bin histograms; rows with no data get a flat prior. */
    fun monthPrior(monthHistograms: Array<IntArray?>, month: Int): FloatArray {
        require(month in 1..12)
        val n = monthHistograms.size
        val prior = FloatArray(n)
        var total = 0f
        for (i in 0 until n) {
            val h = monthHistograms[i]
            prior[i] = if (h == null) 1f / 12f else (h[month - 1] + 1f) / (h.sum() + 12f)
            total += prior[i]
        }
        for (i in 0 until n) prior[i] /= total
        return prior
    }
}

object Descriptors {
    fun describe(sortedProbs: FloatArray): ConfidenceDescriptor {
        val top1 = sortedProbs.getOrElse(0) { 0f }
        val top2 = sortedProbs.getOrElse(1) { 0f }
        val top3 = top1 + top2 + sortedProbs.getOrElse(2) { 0f }
        return when {
            top1 >= 0.60f && top1 - top2 >= 0.25f -> ConfidenceDescriptor.STRONG
            top1 - top2 < 0.10f && top3 >= 0.50f -> ConfidenceDescriptor.SEVERAL_PLAUSIBLE
            else -> ConfidenceDescriptor.UNCERTAIN
        }
    }

    /** Between 2 and 4 alternatives: candidates after the primary with prob >= 3 %, but at least two. */
    fun alternatives(combined: List<Candidate>): List<Candidate> {
        val rest = combined.drop(1)
        val strong = rest.takeWhile { it.prob >= 0.03f }.take(4)
        return if (strong.size >= 2) strong else rest.take(2)
    }

    fun agreement(photoTop: List<Candidate>, primaryClass: Int): Agreement = when {
        photoTop.firstOrNull()?.classIndex == primaryClass -> Agreement.SUPPORTS
        photoTop.take(3).any { it.classIndex == primaryClass } -> Agreement.PARTIAL
        else -> Agreement.CONFLICTS
    }
}
