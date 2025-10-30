/**
 * T011: BackendType Enum
 * Feature: 019-we-should-not
 *
 * Identifies which graphics API is active.
 *
 * Used by:
 * - RendererFactory.detectAvailableBackends()
 * - Renderer.backend property
 * - RendererCapabilities.backend property
 */

package io.materia.renderer

/**
 * Graphics backend type.
 *
 * Defines the available rendering backends in priority order:
 * - Primary backends (WEBGPU, VULKAN): Modern, high-performance APIs
 * - Fallback backend (WEBGL): Compatibility fallback
 */
enum class BackendType {
    /**
     * WebGPU - Primary for JavaScript/browser targets.
     * Modern GPU API available in Chrome 113+, Edge 113+.
     */
    WEBGPU,

    /**
     * Vulkan - Primary for JVM/Native targets.
     * Cross-platform, low-overhead GPU API.
     */
    VULKAN,

    /**
     * WebGL 2.0 - Fallback only for browser compatibility.
     * Used when WebGPU is unavailable (older browsers, experimental flags disabled).
     */
    WEBGL;

    /**
     * Returns true if this is a primary backend (WebGPU or Vulkan).
     *
     * Primary backends are preferred for performance and features.
     */
    val isPrimary: Boolean
        get() = this == WEBGPU || this == VULKAN

    /**
     * Returns true if this is a fallback backend (WebGL).
     *
     * Fallback backends are used only when primary backends are unavailable.
     */
    val isFallback: Boolean
        get() = this == WEBGL
}
