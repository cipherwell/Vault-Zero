package com.example.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LanguageController
import com.example.ui.i18n.LanguagePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The in-app language switch has to work before a vault exists and outlive a vault reset, so its
 * storage is deliberately independent from the encrypted vault preferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageControllerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(LanguagePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `defaults to following the system language`() {
        val controller = LanguageController(context)
        assertEquals(AppLanguage.SYSTEM, controller.language)
        assertEquals("en-US", controller.resolvedTag)
    }

    @Test
    fun `an explicit selection persists across controllers`() {
        val first = LanguageController(context)
        first.updateLanguage(AppLanguage.SPANISH_US)
        assertEquals("es-US", first.resolvedTag)

        val second = LanguageController(context)
        assertEquals(AppLanguage.SPANISH_US, second.language)
        assertEquals("es-US", second.resolvedTag)
    }

    @Test
    fun `system mode resolves to Spanish on a Spanish device`() {
        Locale.setDefault(Locale("es", "PR"))
        val controller = LanguageController(context)
        controller.updateLanguage(AppLanguage.SYSTEM)
        assertEquals("es-US", controller.resolvedTag)

        Locale.setDefault(Locale("fr", "FR"))
        val frenchDevice = LanguageController(context)
        assertEquals(
            "unsupported device languages fall back to US English",
            "en-US",
            frenchDevice.resolvedTag,
        )
    }

    @Test
    fun `stored values survive round-trip and unknown values fall back to system`() {
        for (language in AppLanguage.entries) {
            LanguagePreferences(context).language = language
            assertEquals(language, LanguagePreferences(context).language)
        }
        context.getSharedPreferences(LanguagePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("app_language", "klingon").commit()
        assertEquals(AppLanguage.SYSTEM, LanguagePreferences(context).language)
    }

    @Test
    fun `the language preference is not stored in the vault preferences`() {
        LanguageController(context).updateLanguage(AppLanguage.SPANISH_US)
        val vaultPrefs = context.getSharedPreferences("vaultzero_secure_prefs", Context.MODE_PRIVATE)
        assertEquals(
            "language must live outside the encrypted vault prefs so it survives reset",
            false,
            vaultPrefs.contains("app_language"),
        )
        assertNotEquals(0L, context.getSharedPreferences(LanguagePreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .all.size.toLong())
    }
}
