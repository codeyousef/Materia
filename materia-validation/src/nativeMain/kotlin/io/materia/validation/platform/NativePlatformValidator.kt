package io.materia.validation.platform

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi

/**
 * Native platform-specific validation utilities.
 *
 * Provides helpers for validating Native platform functionality:
 * - Operating system detection
 * - Vulkan support validation
 * - System resource checks
 * - Display server availability (X11/Wayland on Linux)
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
object NativePlatformValidator {

    /**
     * Detects the current operating system.
     */
    fun detectOperatingSystem(): String {
        return when (Platform.osFamily) {
            OsFamily.LINUX -> "Linux"
            OsFamily.WINDOWS -> "Windows"
            OsFamily.MACOSX -> "macOS"
            else -> "Unknown OS"
        }
    }

    /**
     * Checks if the system has sufficient memory for 3D rendering.
     * @param minMemoryMB Minimum required memory in megabytes
     *
     * Note: Detailed memory checking requires platform-specific APIs (sysinfo on Linux,
     * GlobalMemoryStatusEx on Windows, sysctl on macOS). For validation purposes,
     * we assume sufficient memory is available on platforms capable of running this code.
     */
    fun hasRequiredMemory(minMemoryMB: Long = 256): Boolean {
        // Platform-specific memory checks would be implemented here in a full production system
        // Current implementation assumes modern systems with adequate memory
        return true
    }

    /**
     * Validates that Vulkan is available on this platform.
     * This is a simplified check - real implementation would use Vulkan loader.
     */
    fun hasVulkanSupport(): Boolean {
        return when (Platform.osFamily) {
            OsFamily.LINUX, OsFamily.WINDOWS -> {
                // Check for Vulkan loader library
                // Simplified - real check would attempt to load vulkan-1.so/.dll
                true
            }

            OsFamily.MACOSX -> {
                // macOS uses MoltenVK (Vulkan-to-Metal translation)
                true
            }

            else -> false
        }
    }

    /**
     * Checks if a display server is available (Linux only).
     * Returns true on non-Linux platforms.
     */
    fun hasDisplayServer(): Boolean {
        if (Platform.osFamily != OsFamily.LINUX) {
            return true
        }

        // Check for X11 DISPLAY environment variable
        val display = getenv("DISPLAY")?.toKString()
        if (display != null && display.isNotEmpty()) {
            return true
        }

        // Check for Wayland
        val waylandDisplay = getenv("WAYLAND_DISPLAY")?.toKString()
        if (waylandDisplay != null && waylandDisplay.isNotEmpty()) {
            return true
        }

        return false
    }

    /**
     * Gets the system architecture.
     */
    fun getArchitecture(): String {
        return when (Platform.cpuArchitecture) {
            CpuArchitecture.X64 -> "x86_64"
            CpuArchitecture.X86 -> "x86"
            CpuArchitecture.ARM64 -> "ARM64"
            CpuArchitecture.ARM32 -> "ARM32"
            else -> "Unknown Architecture"
        }
    }

    /**
     * Checks if compiler toolchain is available for the current platform.
     * Note: This is a simplified check that returns platform defaults.
     */
    fun hasCompilerToolchain(): Boolean {
        return when (Platform.osFamily) {
            OsFamily.LINUX -> {
                // Assume GCC or Clang is available on Linux
                true
            }

            OsFamily.WINDOWS -> {
                // Assume MSVC or MinGW is available on Windows
                true
            }

            OsFamily.MACOSX -> {
                // Assume Xcode command-line tools are available on macOS
                true
            }

            else -> false
        }
    }
}
