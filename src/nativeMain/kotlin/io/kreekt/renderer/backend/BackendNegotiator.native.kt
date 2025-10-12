/**
 * Native stub for BackendNegotiator.
 * Native platforms are not primary targets for KreeKt.
 */

package io.kreekt.renderer.backend

/**
 * Native actual for createBackendNegotiator function.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual fun createBackendNegotiator(): BackendNegotiator {
    return object : BackendNegotiator {
        override suspend fun detectCapabilities(request: CapabilityRequest): DeviceCapabilityReport {
            throw UnsupportedOperationException("Native platforms are not supported")
        }
        
        override suspend fun selectBackend(
            report: DeviceCapabilityReport,
            profiles: List<RenderingBackendProfile>
        ): BackendSelection {
            throw UnsupportedOperationException("Native platforms are not supported")
        }
        
        override suspend fun initializeBackend(
            selection: BackendSelection,
            surface: SurfaceConfig
        ): BackendHandle {
            throw UnsupportedOperationException("Native platforms are not supported")
        }
    }
}
