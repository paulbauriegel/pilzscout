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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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

/** Colour theme. SYSTEM follows the device's dark-mode setting. */
enum class ThemeMode(val key: String, val nightMode: Int) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val useGpu = booleanPreferencesKey("use_gpu")
        val includeLocationByDefault = booleanPreferencesKey("include_location")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val themeMode = stringPreferencesKey("theme_mode")
        val packWifiOnly = booleanPreferencesKey("pack_wifi_only")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromKey(it[Keys.themeMode]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.key }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /**
     * Re-applies the stored theme on process start, before any activity is created. AppCompat does not
     * persist the night mode itself (unlike locales with autoStoreLocales), so this is a blocking read of
     * a tiny preferences file.
     */
    fun applyStoredThemeMode() {
        val mode = runBlocking { themeMode.first() }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.dynamicColor] ?: false }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { AppLanguage.fromTag(it[Keys.language]) }
    val useGpu: Flow<Boolean> = context.dataStore.data.map { it[Keys.useGpu] ?: false }

    /** Download the offline pack only over unmetered networks (`play` flavour). */
    val packWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.packWifiOnly] ?: false }

    suspend fun setPackWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.packWifiOnly] = enabled }
    }
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
