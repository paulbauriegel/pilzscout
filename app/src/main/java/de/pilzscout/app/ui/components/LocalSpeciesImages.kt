package de.pilzscout.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import de.pilzscout.app.data.species.SpeciesImages
import de.pilzscout.app.data.species.rememberThumb
import java.io.File

val LocalSpeciesImages = staticCompositionLocalOf<SpeciesImages> { error("SpeciesImages not provided") }

@Composable
fun speciesThumb(speciesId: String?): State<File?> = LocalSpeciesImages.current.rememberThumb(speciesId, contentLanguage())
