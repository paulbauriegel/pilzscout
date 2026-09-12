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
)

data class ModelInfo(
    val version: String,
    val name: String,
    val precision: String,
    val inputSize: Int,
    val numClasses: Int,
    val accelerator: String,
)
