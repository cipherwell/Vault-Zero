package com.example.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.R
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LanguageController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Device-level checks for the bilingual build: the Spanish (US) resources ship inside the APK and
 * resolve on Android, and the in-app selection persists outside the vault preferences.
 */
@RunWith(AndroidJUnit4::class)
class LanguageDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun spanishResources() = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocale(Locale("es", "US"))
        }
    ).resources

    @Test
    fun spanishResourcesShipInTheApkAndResolve() {
        val es = spanishResources()
        assertEquals("Bóveda", es.getString(R.string.nav_vault))
        assertEquals("Ajustes", es.getString(R.string.nav_settings))
        assertEquals("Contraseña maestra", es.getString(R.string.lock_master_password))
        assertEquals("¿Olvidaste tu contraseña maestra? Usa el kit de emergencia", es.getString(R.string.lock_forgot_password))
        assertTrue(es.getString(R.string.lock_setup_body).contains("AES-256-GCM"))
        // Format arguments survive the translation.
        assertEquals(
            "Copia de seguridad: demo.json",
            es.getString(R.string.lock_backup_file, "demo.json"),
        )
    }

    @Test
    fun everyStringResourceResolvesInBothLanguages() {
        val en = context.resources
        val es = spanishResources()
        R.string::class.java.fields.forEach { field ->
            val id = field.getInt(null)
            if (id == 0) return@forEach
            assertTrue("missing EN value for ${field.name}", en.getString(id).isNotEmpty())
            assertTrue("missing ES value for ${field.name}", es.getString(id).isNotEmpty())
        }
    }

    @Test
    fun selectionPersistsOutsideTheVaultPreferences() {
        val controller = LanguageController(context)
        controller.updateLanguage(AppLanguage.SPANISH_US)
        assertEquals("es-US", controller.resolvedTag)

        val restarted = LanguageController(context)
        assertEquals(AppLanguage.SPANISH_US, restarted.language)

        val vaultPrefs = context.getSharedPreferences("vaultzero_secure_prefs", Context.MODE_PRIVATE)
        assertEquals(false, vaultPrefs.contains("app_language"))

        controller.updateLanguage(AppLanguage.ENGLISH_US)
        assertEquals("en-US", LanguageController(context).resolvedTag)
        assertNotEquals(AppLanguage.SYSTEM, LanguageController(context).language)
    }
}
