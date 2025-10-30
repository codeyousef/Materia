package io.materia.validation.models

/**
 * Represents all supported Kotlin Multiplatform target platforms.
 *
 * This enum encompasses all platforms that Materia can be compiled for,
 * including JVM, JavaScript, Native desktop, and mobile platforms.
 * Used for platform-specific validation and cross-platform consistency checks.
 */
enum class Platform {
    /**
     * Java Virtual Machine platform.
     * Includes desktop applications, servers, and Android runtime.
     */
    JVM,

    /**
     * JavaScript platform.
     * Includes browser environments and Node.js runtime.
     * Primary target for WebGPU rendering.
     */
    JS,

    /**
     * Native Linux x64 platform.
     * Desktop Linux applications using Kotlin/Native.
     */
    NATIVE_LINUX_X64,

    /**
     * Native Windows x64 platform.
     * Desktop Windows applications using Kotlin/Native.
     */
    NATIVE_WINDOWS_X64,

    /**
     * Native macOS x64 platform.
     * Desktop macOS applications for Intel-based Macs using Kotlin/Native.
     */
    NATIVE_MACOS_X64,

    /**
     * Native macOS ARM64 platform.
     * Desktop macOS applications for Apple Silicon Macs using Kotlin/Native.
     */
    NATIVE_MACOS_ARM64,

    /**
     * Android platform.
     * Mobile applications running on Android devices.
     * Uses JVM but with platform-specific APIs and constraints.
     */
    ANDROID,

    /**
     * iOS platform.
     * Mobile applications running on iPhone and iPad devices.
     * Uses Kotlin/Native with platform-specific frameworks.
     */
    IOS,

    /**
     * watchOS platform.
     * Applications running on Apple Watch devices.
     * Limited subset of iOS functionality with specific constraints.
     */
    WATCHOS,

    /**
     * tvOS platform.
     * Applications running on Apple TV devices.
     * Subset of iOS functionality optimized for TV interfaces.
     */
    TVOS
}