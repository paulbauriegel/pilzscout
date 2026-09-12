package de.pilzscout.app.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App language override. SYSTEM follows the device language (German or English, else English). */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null), GERMAN("de"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val useGpu = booleanPreferencesKey("use_gpu")
        val includeLocationByDefault = booleanPreferencesKey("include_location")
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { AppLanguage.fromTag(it[Keys.language]) }
    val useGpu: Flow<Boolean> = context.dataStore.data.map { it[Keys.useGpu] ?: false }
    val includeLocationByDefault: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.includeLocationByDefault] ?: true }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            if (language.tag == null) prefs.remove(Keys.language) else prefs[Keys.language] = language.tag
        }
        AppCompatDelegate.setApplicationLocales(
            if (language.tag == null) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language.tag),
        )
    }

    suspend fun setUseGpu(enabled: Boolean) {
        context.dataStore.edit { it[Keys.useGpu] = enabled }
    }

    suspend fun setIncludeLocationByDefault(enabled: Boolean) {
        context.dataStore.edit { it[Keys.includeLocationByDefault] = enabled }
    }
}
