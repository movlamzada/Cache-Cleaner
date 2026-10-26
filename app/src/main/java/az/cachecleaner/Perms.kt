package az.cachecleaner

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object Perms {

    enum class Step { OVERLAY, PIP, ACCESSIBILITY }

    data class Status(val overlay: Boolean, val pip: Boolean, val accessibility: Boolean) {
        fun next(): Step? = when {
            !overlay -> Step.OVERLAY
            !pip -> Step.PIP
            !accessibility -> Step.ACCESSIBILITY
            else -> null
        }
    }

    fun read(c: Context) = Status(overlayOk(c), pipOk(c), accessibilityOk(c))

    private fun overlayOk(c: Context) = Settings.canDrawOverlays(c)

    /** PiP Android 8.0-dan əvvəl və ya PiP dəstəkləməyən cihazlarda tətbiq olunmur. */
    @Suppress("DEPRECATION")
    private fun pipOk(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        if (!c.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return true
        return try {
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = ops.checkOpNoThrow("android:picture_in_picture", Process.myUid(), c.packageName)
            mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
        } catch (e: Exception) {
            true
        }
    }

    private fun accessibilityOk(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(c, CleanerService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }

    fun open(a: Activity, step: Step) {
        val pkgUri = Uri.parse("package:${a.packageName}")
        val intent = when (step) {
            Step.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            Step.PIP -> Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", pkgUri)
            Step.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        if (!safeStart(a, intent)) openAppInfo(a)
    }

    /** Android 13+: yan yüklənmiş tətbiqlərdə "Restricted setting" üçün (⋮ → Allow restricted settings). */
    fun openAppInfo(a: Activity) {
        safeStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
    }

    private fun safeStart(a: Activity, i: Intent): Boolean = try {
        a.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}
