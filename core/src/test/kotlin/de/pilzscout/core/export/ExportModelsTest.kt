package de.pilzscout.core.export

import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.ConfidenceDescriptor
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportModelsTest {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Test
    fun roundTripAndFieldNames() {
        val ref = ExportSpeciesRef("amanita-muscaria", "Amanita muscaria (L.) Lam.", 8168319)
        val obs = ExportObservation(
            observationId = "abc",
            capturedAt = "2026-09-12T14:00:00+02:00",
            exportedAt = "2026-09-12T15:00:00+02:00",
            approxLocation = ExportLocation(52.52, 13.41),
            photos = listOf(
                ExportPhoto("photos/cap.jpg", ViewType.CAP, 0, 2048, 1536, ExportPhotoPrediction(ExportCandidate(0, ref, 0.94f), emptyList()), Agreement.SUPPORTS, 812),
            ),
            combined = listOf(ExportCandidate(0, ref, 0.96f)),
            descriptor = ConfidenceDescriptor.STRONG,
            contextualInputs = ExportContext(9, true, true, 0.5f, 1),
            comparison = null,
            model = ExportModel("full_vits16_v3_320+float32", "vit_small_patch16_dinov3", "float32", 320, 812),
            userCorrection = null,
            userConfirmed = false,
            mode = "offline",
            appVersion = "0.1.0",
        )
        val text = json.encodeToString(ExportObservation.serializer(), obs)
        val back = json.decodeFromString(ExportObservation.serializer(), text)
        assertEquals(obs, back)
        for (key in listOf("\"observationId\"", "\"capturedAt\"", "\"approxLocation\"", "\"photos\"", "\"viewType\"", "\"combined\"", "\"gbifKey\"", "\"contextualInputs\"", "\"model\"", "\"userCorrection\"", "\"schemaVersion\"")) {
            assertTrue(key in text, key)
        }
        assertTrue("\"viewType\": \"CAP\"" in text)
    }
}
