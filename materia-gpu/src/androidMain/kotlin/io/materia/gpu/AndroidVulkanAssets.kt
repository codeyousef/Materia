package io.materia.gpu

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

private const val ANDROID_VULKAN_TAG = "AndroidVulkanAssets"
private const val VULKAN_1_0 = 0x00400000

/**
 * Backward-compatible Android Vulkan helper retained for example activities.
 *
 * The Android GPU backend no longer loads SPIR-V through this object, but the
 * examples still use it to cache application context and gate startup on Vulkan
 * feature advertisement.
 */
object AndroidVulkanAssets {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var supportCache: Boolean? = null

    fun initialise(context: Context) {
        appContext = context.applicationContext
        supportCache = null
    }

    fun hasVulkanSupport(): Boolean {
        supportCache?.let { return it }

        val context = appContext
        if (context == null) {
            Log.w(ANDROID_VULKAN_TAG, "hasVulkanSupport() called before initialise(); assuming unsupported")
            return false
        }

        val packageManager = context.packageManager
        val hasLevel = packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasCompute = packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
        val hasVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_0)
        } else {
            false
        }

        val supported = hasLevel || hasCompute || hasVersion
        supportCache = supported

        if (supported) {
            Log.i(
                ANDROID_VULKAN_TAG,
                "Vulkan advertised (level=$hasLevel, compute=$hasCompute, version=$hasVersion)"
            )
        } else {
            Log.w(
                ANDROID_VULKAN_TAG,
                "Device ${Build.MANUFACTURER} ${Build.MODEL} does not advertise Vulkan support"
            )
        }

        return supported
    }
}