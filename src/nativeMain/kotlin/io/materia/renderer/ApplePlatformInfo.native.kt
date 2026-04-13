package io.materia.renderer

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSClassFromString
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

internal data class ApplePlatformInfo(
    val platformName: String,
    val osBuild: String,
    val deviceId: String,
    val driverVersion: String,
    val supportsRayTracing: Boolean,
    val supportsXrSurface: Boolean
)

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal fun currentApplePlatformInfo(): ApplePlatformInfo {
    val processInfo = NSProcessInfo.processInfo
    val versionString = processInfo.operatingSystemVersionString
    val isMobileApplePlatform = NSClassFromString("UIDevice") != null
    val platformName = when {
        versionString.contains("visionOS", ignoreCase = true) -> "visionOS"
        isMobileApplePlatform -> "iOS"
        else -> "macOS"
    }
    val deviceId = appleMachineIdentifier()

    return ApplePlatformInfo(
        platformName = platformName,
        osBuild = "$platformName ($versionString)",
        deviceId = deviceId,
        driverVersion = "MoltenVK 1.2.x / Metal 3",
        supportsRayTracing = supportsAppleRayTracing(deviceId),
        supportsXrSurface = platformName == "iOS" || platformName == "visionOS"
    )
}

internal fun currentApplePlatformName(): String = currentApplePlatformInfo().platformName

@OptIn(ExperimentalForeignApi::class)
internal fun appleMachineIdentifier(): String = memScoped {
    val systemInfo = alloc<utsname>()
    if (uname(systemInfo.ptr) != 0) {
        return@memScoped "Apple-Device"
    }
    systemInfo.machine.toKString()
}

private fun supportsAppleRayTracing(deviceId: String): Boolean {
    return deviceId == "arm64" ||
        deviceId.startsWith("iPhone14") ||
        deviceId.startsWith("iPhone15") ||
        deviceId.startsWith("iPhone16") ||
        deviceId.startsWith("iPad13") ||
        deviceId.startsWith("iPad14") ||
        deviceId.startsWith("Mac13") ||
        deviceId.startsWith("Mac14") ||
        deviceId.startsWith("Mac15")
}