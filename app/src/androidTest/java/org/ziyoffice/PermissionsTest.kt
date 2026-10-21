package org.ziyoffice

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The privacy promise, checked against the installed app.
 *
 * The About screen shows the permissions the APK really declares. This test
 * reads the same thing from the system, so the screen and the truth can't
 * drift apart.
 */
@RunWith(AndroidJUnit4::class)
class PermissionsTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theAppAsksForNoInternet() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions?.toList().orEmpty()

        assertFalse(
            "the app must not ask for INTERNET, got $permissions",
            permissions.contains("android.permission.INTERNET"),
        )
    }

    @Test
    fun theOnlyPermissionIsTheOneAndroidxAddsForItself() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions?.toList().orEmpty()

        val unexpected = permissions.filter {
            it != "org.ziyoffice.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        }
        assertTrue(
            "no permission outside the AndroidX one should be declared, got $unexpected",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun theAppIsActuallyInstalled() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertTrue("the app should have a launcher intent", intent != null)
    }
}
