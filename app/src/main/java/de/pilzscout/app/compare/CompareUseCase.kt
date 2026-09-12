package de.pilzscout.app.compare

import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.data.species.TraitEntity
import de.pilzscout.core.compare.ComparisonEngine
import de.pilzscout.core.compare.ComparisonResult
import de.pilzscout.core.compare.PhotoRanking
import de.pilzscout.core.compare.SpeciesTraits
import de.pilzscout.core.compare.Statement
import de.pilzscout.core.model.Basis
import de.pilzscout.core.model.Feature
import de.pilzscout.core.model.ViewType
import javax.inject.Inject

/** Loads traits + month histograms from the species DB and runs the offline comparison engine. */
class CompareUseCase @Inject constructor(private val species: SpeciesRepository) {

    suspend fun traits(speciesId: String, lang: String): SpeciesTraits {
        var rows = species.traits(speciesId, lang)
        if (rows.isEmpty() && lang != "en") rows = species.traits(speciesId, "en")
        return SpeciesTraits(speciesId, rows.groupBy { Feature.valueOf(it.feature) }.mapValues { (_, list) -> list.map { it.toStatement() } })
    }

    suspend fun monthHist(speciesId: String): IntArray? =
        species.stats(speciesId)?.monthHistJson?.trim('[', ']')?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toIntArray()?.takeIf { it.size == 12 }

    suspend fun compare(
        primaryId: String,
        alternativeId: String,
        lang: String,
        capturedViews: Set<ViewType>,
        photoRanking: PhotoRanking,
        month: Int?,
    ): ComparisonResult = ComparisonEngine.compare(
        primary = traits(primaryId, lang),
        alternative = traits(alternativeId, lang),
        capturedViews = capturedViews,
        photoRanking = photoRanking,
        month = month,
        monthHistPrimary = monthHist(primaryId),
        monthHistAlternative = monthHist(alternativeId),
    )

    private fun TraitEntity.toStatement() = Statement(
        text = text,
        basis = runCatching { Basis.valueOf(basis) }.getOrDefault(Basis.REFERENCE),
        source = source,
        valueKey = valueKey,
    )
}
