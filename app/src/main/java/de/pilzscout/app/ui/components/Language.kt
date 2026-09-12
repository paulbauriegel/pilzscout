package de.pilzscout.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** "de" when the app runs in German, otherwise "en". Drives which species names and Wikipedia texts are shown. */
@Composable
fun contentLanguage(): String {
    val locales = LocalConfiguration.current.locales
    val language = if (locales.isEmpty) "en" else locales[0].language
    return if (language == "de") "de" else "en"
}
