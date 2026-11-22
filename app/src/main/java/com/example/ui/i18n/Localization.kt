package com.example.ui.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** The language currently applied to the composition (already resolved from SYSTEM). */
val LocalAppLanguageTag = staticCompositionLocalOf { "en-US" }

/**
 * Observable language controller owned by the Activity. Nullable so previews and isolated
 * composables (dialog previews, tests that render a single screen) do not have to provide one.
 */
val LocalLanguageController = staticCompositionLocalOf<LanguageController?> { null }

/**
 * Provides a context/configuration bound to [languageTag] for the whole app.
 *
 * Every `stringResource()` and every `LocalContext.getString()` below this provider resolves in
 * the selected language, so switching languages recomposes the tree instantly — no activity
 * recreation, which matters because recreating would drop an unlocked vault back to the lock
 * screen. The language state itself lives in [LanguageController] and is updated from the UI.
 */
@Composable
fun AppLocalization(languageTag: String, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val locale = remember(languageTag) { Locale.forLanguageTag(languageTag) }
    val configuration = remember(languageTag, baseConfiguration, locale) {
        Configuration(baseConfiguration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(configuration, baseContext) {
        baseContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalAppLanguageTag provides languageTag,
        content = content,
    )
}
