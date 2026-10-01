package de.pilzscout.app.ml

import kotlinx.serialization.Serializable

/** Contents of model/model.json inside the model pack component. */
@Serializable
data class ModelMeta(
    val modelFile: String,
    val precision: String,
    val name: String? = null,
    val checkpoint: String? = null,
    val inputSize: Int = 320,
    val numClasses: Int,
    val inputLayout: String? = null,
    val output: String? = null,
    val evalResize: Int = 366,
    val runName: String? = null,
    /** Static batch of the graph. The app contract is 1; benchmark exports may use more (see DeviceBenchmark). */
    val batch: Int = 1,
    /**
     * The export calibrated LayerNorm/softmax so fp16 activations cannot overflow; the GPU then runs in
     * FP16_WITH_FP32_ACCUM (Pixel 7 Pro: 126 ms instead of 209 ms per photo, logits within 0.02).
     */
    val gpuFp16Safe: Boolean = false,
)

data class ModelInfo(
    val version: String,
    val name: String,
    val precision: String,
    val inputSize: Int,
    val numClasses: Int,
    /** "CPU", "GPU fp32" or "GPU fp16" as shown in settings and stored with each observation. */
    val accelerator: String,
)
