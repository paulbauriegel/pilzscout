package de.pilzscout.app.ui.result

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.pilzscout.app.R
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.ui.components.contentLanguage

/** Display name in the content language, falling back to the other language and then the binomial. */
@Composable
fun SpeciesEntity?.displayName(): String {
    if (this == null) return stringResource(R.string.unknown_species)
    val lang = contentLanguage()
    return (if (lang == "de") commonDe ?: commonEn else commonEn ?: commonDe) ?: binomial
}

@Composable
fun SpeciesEntity?.commonNameOrNull(): String? {
    if (this == null) return null
    val lang = contentLanguage()
    return if (lang == "de") commonDe ?: commonEn else commonEn ?: commonDe
}
