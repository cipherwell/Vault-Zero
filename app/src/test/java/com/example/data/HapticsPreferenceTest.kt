package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HapticsPreferenceTest {

    private lateinit var context: Context
    private lateinit var prefs: VaultPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
    }

    @After
    fun tearDown() = prefs.resetAll()

    @Test
    fun `haptics default on and persist off`() {
        assertTrue("subtle haptics must be on by default", prefs.hapticsEnabled)
        prefs.hapticsEnabled = false
        assertFalse(
            "the preference must survive a new instance",
            VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME).hapticsEnabled,
        )
        prefs.hapticsEnabled = true
        assertTrue(prefs.hapticsEnabled)
    }
}
