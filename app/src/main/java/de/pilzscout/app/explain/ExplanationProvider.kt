package de.pilzscout.app.explain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.core.compare.ComparisonResult
import de.pilzscout.core.compare.Statement
import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.Feature
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ExplanationRequest(
    val primaryName: String,
    val primaryBinomial: String,
    val alternativeName: String,
    val alternativeBinomial: String,
    val comparison: ComparisonResult,
    val language: String,
)

/** One AI-generated statement per feature, always marked AI_GENERATED with source references. */
data class Explanation(val perFeature: Map<Feature, Statement>, val model: String)

/** Online, optional. v1 ships a stub; a real provider (e.g. Claude API) can be bound in ExplainModule. */
interface ExplanationProvider {
    val id: String
    suspend fun explain(request: ExplanationRequest): Explanation
}

class StubExplanationProvider @Inject constructor() : ExplanationProvider {
    override val id = "stub"

    override suspend fun explain(request: ExplanationRequest): Explanation {
        delay(800)
        val de = request.language == "de"
        val per = request.comparison.rows.associate { row ->
            val refs = (row.primary + row.alternative).mapNotNull { it.source }.distinct().joinToString(", ")
            val text = if (de) {
                "Beispieltext (Platzhalter): Für „${featureName(row.feature, true)}“ unterscheiden sich ${request.primaryName} und ${request.alternativeName} laut den Referenzangaben${if (refs.isNotEmpty()) " ($refs)" else ""}. Ein echter Sprachmodell-Anbieter kann hier eine natürliche Erklärung erzeugen."
            } else {
                "Sample text (placeholder): For “${featureName(row.feature, false)}”, ${request.primaryName} and ${request.alternativeName} differ according to the reference data${if (refs.isNotEmpty()) " ($refs)" else ""}. A real language-model provider can generate a natural explanation here."
            }
            row.feature to Statement(text, Basis.AI_GENERATED, source = "ai:stub")
        }
        return Explanation(per, model = "stub")
    }

    private fun featureName(f: Feature, de: Boolean) = when (f) {
        Feature.CAP -> if (de) "Hut" else "Cap"
        Feature.GILLS_PORES -> if (de) "Lamellen oder Poren" else "Gills or pores"
        Feature.STEM -> if (de) "Stiel" else "Stem"
        Feature.RING -> if (de) "Ring" else "Ring"
        Feature.BASE_VOLVA -> if (de) "Stielbasis oder Volva" else "Stem base or volva"
        Feature.SURFACE_TEXTURE -> if (de) "Oberfläche und Textur" else "Surface and texture"
        Feature.HABITAT_SUBSTRATE -> if (de) "Habitat und Substrat" else "Habitat and substrate"
        Feature.REGION_SEASON -> if (de) "Region und Saison" else "Region and season"
    }
}

@Singleton
class ConnectivityObserver @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    val online: Flow<Boolean> = callbackFlow {
        trySend(isOnline())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            override fun onLost(network: Network) { trySend(isOnline()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) { trySend(isOnline()) }
        }
        manager?.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }
}
