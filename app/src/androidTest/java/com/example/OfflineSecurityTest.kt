package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the "offline by construction" claim on the installed APK: the app asks
 * the platform for no network capability and exposes no component other than the
 * launcher activity.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSecurityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm: PackageManager = context.packageManager
    private val packageName: String = context.packageName

    @Test
    fun declaresNoNetworkPermissions() {
        assertEquals(
            "INTERNET must be denied",
            PackageManager.PERMISSION_DENIED,
            pm.checkPermission(Manifest.permission.INTERNET, packageName),
        )
        assertEquals(
            "ACCESS_NETWORK_STATE must be denied",
            PackageManager.PERMISSION_DENIED,
            pm.checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, packageName),
        )
    }

    @Test
    fun permissionSurfaceIsExactlyAllowlisted() {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet() ?: emptySet()
        // Exactly what the app + AndroidX biometrics need. No INTERNET, no
        // network state, no storage, no contacts, no location.
        val allowed = setOf(
            Manifest.permission.USE_BIOMETRIC,
            Manifest.permission.USE_FINGERPRINT, // legacy alias merged by androidx.biometric
            "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", // androidx.core receivers
        )
        assertTrue(
            "unexpected permissions: ${requested - allowed}",
            requested.all { it in allowed },
        )
        assertTrue(requested.contains(Manifest.permission.USE_BIOMETRIC))
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        val exported = info.activities?.filter { it.exported } ?: emptyList()
        assertEquals(
            "exactly one exported activity in the whole APK: ${exported.map { it.name }}",
            1,
            exported.size,
        )
        assertTrue("it is the launcher activity", exported.single().name.endsWith("MainActivity"))
    }
}
