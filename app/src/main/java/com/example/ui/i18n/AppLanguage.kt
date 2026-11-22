package com.example.ui.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Languages the app ships translations for.
 *
 * - [SYSTEM] follows the device language, falling back to US English when the device is neither
 *   English nor Spanish.
 * - The explicit entries are what the in-app language switch exposes.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH_US("en-US"),
    SPANISH_US("es-US");

    companion object {
        fun fromStorage(raw: String?): AppLanguage =
            entries.firstOrNull { it.tag == raw } ?: SYSTEM
    }
}

/**
 * Persists the in-app language outside the vault preferences on purpose: the language picker has
 * to work on a fresh install, before any vault (or recovery phrase) exists, and it must survive
 * "reset vault".
 */
class LanguagePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.fromStorage(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.tag).apply()

    companion object {
        const val PREFS_NAME = "vaultzero_language"
        private const val KEY_LANGUAGE = "app_language"

        /** BCP-47 tags declared in res/xml/locales_config.xml (and the system app-language picker). */
        val SUPPORTED_TAGS = listOf("en-US", "es-US")
    }
}

/**
 * Observable holder for the selected language. Owned by `MainActivity` so the switch is reachable
 * from the lock screen (before a vault exists) and from Settings (inside the vault).
 */
class LanguageController(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = LanguagePreferences(appContext)

    var language: AppLanguage by mutableStateOf(preferences.language)
        private set

    /** BCP-47 tag actually used to resolve resources, with [AppLanguage.SYSTEM] resolved. */
    val resolvedTag: String
        get() = when (language) {
            AppLanguage.SYSTEM -> deviceLanguageTag()
            AppLanguage.ENGLISH_US -> "en-US"
            AppLanguage.SPANISH_US -> "es-US"
        }

    fun updateLanguage(value: AppLanguage) {
        if (value == language) return
        language = value
        preferences.language = value
        // Keep the OS-level per-app language (Android 13+) in sync so the system picker and
        // anything reading application locales agree with the in-app switch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val manager = appContext.getSystemService(LocaleManager::class.java) ?: return@runCatching
                when (value) {
                    AppLanguage.SYSTEM -> manager.applicationLocales = LocaleList.getEmptyLocaleList()
                    else -> manager.applicationLocales = LocaleList.forLanguageTags(resolvedTag)
                }
            }
        }
    }

    private fun deviceLanguageTag(): String {
        val current = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault()[0]
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
        return if (current.language.equals("es", ignoreCase = true)) "es-US" else "en-US"
    }
}
