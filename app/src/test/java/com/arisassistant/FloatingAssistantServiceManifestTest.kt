package com.arisassistant

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Keeps the Android manifest contract aligned with the runtime
 * startForeground() call. This catches accidental removal or drift of the
 * specialUse service declaration and its required permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingAssistantServiceManifestTest {
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun floatingServiceDeclaresSpecialUseAndIsNotExported() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, FloatingAssistantService::class.java),
            PackageManager.GET_META_DATA
        )

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo.foregroundServiceType
        )
        assertFalse(serviceInfo.exported)
    }

    @Test
    fun manifestRequestsThePermissionsRequiredByTheFloatingShell() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(requested.contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(
            requested.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
        )
    }

    @Test
    fun targetSdkUsesTheTypedForegroundServiceContract() {
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            0
        )

        assertTrue(applicationInfo.targetSdkVersion >= 34)
    }
}
