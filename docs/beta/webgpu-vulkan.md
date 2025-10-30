# WebGPU/Vulkan Backend - Beta Documentation

**Status**: Public Beta
**Version**: 0.1.0-alpha01
**Last Updated**: 2025-10-06

## Overview

Materia now features a fully functional WebGPU/Vulkan rendering backend that automatically selects
the optimal graphics API based on platform and device capabilities. This beta release includes:

- ✅ **Automatic Backend Selection**: Detects and selects WebGPU (web) or Vulkan (desktop/mobile)
- ✅ **Feature Parity**: Compute shaders, ray tracing, XR surface support
- ✅ **Fail-Fast Behavior**: Clear error messaging when backend requirements aren't met
- ✅ **Performance Monitoring**: 60 FPS target, 30 FPS minimum enforcement
- ✅ **Comprehensive Telemetry**: Backend denial diagnostics and performance metrics
- ✅ **Cross-Platform**: Web, Desktop (Windows/Linux/macOS), Android API 33+, iOS 17+

## Platform Support Matrix

| Platform | Backend | Status | Min Requirements |
|----------|---------|--------|------------------|
| **Web** | WebGPU | ✅ Beta | Chrome 128+, Safari TP |
| **Desktop (Windows)** | Vulkan 1.3 | ✅ Beta | Vulkan 1.3 drivers |
| **Desktop (Linux)** | Vulkan 1.3 | ✅ Beta | Vulkan 1.3 drivers |
| **Desktop (macOS)** | MoltenVK | ✅ Beta | macOS 14+, MoltenVK 1.2+ |
| **Android** | Vulkan 1.3 | ✅ Beta | API 33+, Vulkan 1.3 |
| **iOS** | MoltenVK | ✅ Beta | iOS 17+, A15/M1+ chip |

## Device Baselines

### Integrated GPU Baseline

Materia is optimized for integrated GPUs and meets constitutional performance requirements:

- **Apple M1**: 60 FPS avg, <2.5s init (via MoltenVK)
- **Intel Iris Xe**: 60 FPS avg, <3s init (Vulkan 1.3)
- **Qualcomm Adreno 730**: 60 FPS avg, <2.8s init (Android Vulkan)
- **ARM Mali-G710**: 60 FPS avg, <2.9s init (Android Vulkan)

### Feature Support

| Feature | WebGPU | Vulkan | Notes |
|---------|--------|--------|-------|
| **Compute Shaders** | ✅ Supported | ✅ Supported | Full parity |
| **Ray Tracing** | ⚠️ Limited | ✅ Supported | WebGPU: experimental, Vulkan: VK_KHR_ray_tracing |
| **XR Surfaces** | ✅ Supported | ✅ Supported | WebXR (web), ARKit/ARCore (mobile) |
| **PBR Shading** | ✅ Supported | ✅ Supported | Full parity |
| **Omni Shadows** | ✅ Supported | ✅ Supported | Full parity |

## Getting Started

### Basic Usage

```kotlin
import io.materia.renderer.*
import io.materia.renderer.backend.*

// Create renderer with automatic backend selection
val rendererFactory = createRendererFactory()

val result = rendererFactory.createRenderer(
    RendererConfig(
        antialias = true,
        powerPreference = PowerPreference.HIGH_PERFORMANCE
    )
)

when (result) {
    is RendererResult.Success -> {
        val renderer = result.value
        // Backend automatically selected and initialized
        println("Renderer ready with backend: ${renderer.capabilities}")
    }
    is RendererResult.Error -> {
        // Fail-fast error with actionable details
        println("Backend initialization failed: ${result.exception.message}")
    }
}
```

### Backend Integration Details

The backend initialization process:

1. **Capability Detection**: Probes GPU features and driver support
2. **Backend Selection**: Chooses best backend based on priority and capabilities
3. **Parity Evaluation**: Ensures required features are available
4. **Fail-Fast Check**: Denies initialization if requirements aren't met
5. **Performance Monitoring**: Tracks initialization time and frame rates
6. **Telemetry Emission**: Logs backend selection and performance data

### Handling Backend Denial

```kotlin
val integration = BackendIntegration(config)

val initResult = integration.initializeBackend(surface)

when (initResult) {
    is BackendInitializationResult.Success -> {
        println("Backend: ${initResult.backendHandle.backendId}")
        println("Init time: ${initResult.initializationStats.initTimeMs}ms")
        println("Parity score: ${initResult.parityReport.parityScore}")
    }

    is BackendInitializationResult.Denied -> {
        println("Backend denied: ${initResult.message}")
        println("Device: ${initResult.report.deviceId}")
        initResult.report.limitations.forEach {
            println("  - $it")
        }
    }

    is BackendInitializationResult.InitializationFailed -> {
        println("Initialization failed: ${initResult.message}")
    }
}
```

## Performance Monitoring

### Constitutional Requirements

Materia enforces these performance requirements:

- **Target FPS**: ≥60 FPS average
- **Minimum FPS**: ≥30 FPS floor
- **Initialization**: <3 seconds on integrated GPUs

### Using the Performance Monitor

```kotlin
val perfMonitor = createPerformanceMonitor()

// Track initialization
perfMonitor.beginInitializationTrace(BackendId.VULKAN)
// ... initialize backend ...
val stats = perfMonitor.endInitializationTrace(BackendId.VULKAN)

println("Init time: ${stats.initTimeMs}ms (budget: ${stats.budgetMs}ms)")
println("Within budget: ${stats.withinBudget}")

// Track frame performance
perfMonitor.recordFrameMetrics(
    FrameMetrics(
        backendId = BackendId.VULKAN,
        frameTimeMs = 16.7,
        gpuTimeMs = 12.0,
        cpuTimeMs = 4.7
    )
)

// Evaluate budget over rolling window
val assessment = perfMonitor.evaluateBudget()
println("Avg FPS: ${assessment.avgFps}")
println("Min FPS: ${assessment.minFps}")
println("Meets requirements: ${assessment.meetsRequirements()}")
```

## Telemetry & Diagnostics

### Event Types

1. **INITIALIZED**: Backend successfully initialized
2. **DENIED**: Backend denied due to missing features
3. **DEVICE_LOST**: GPU device lost during rendering
4. **PERFORMANCE_DEGRADED**: FPS dropped below thresholds

### Privacy & Compliance

- Session IDs are SHA-256 hashes (no PII)
- Device info limited to vendor/product IDs
- Telemetry retained for 24 hours locally
- Events transmitted within 500ms with 3 retry attempts

### Example Telemetry Payload

```json
{
  "eventId": "evt-1728230400-1234",
  "eventType": "INITIALIZED",
  "backendId": "VULKAN",
  "device": {
    "vendorId": "0x8086",
    "productId": "0x9a49"
  },
  "driverVersion": "30.0.101.1191",
  "osBuild": "Windows 11 22H2",
  "featureFlags": {
    "COMPUTE": "SUPPORTED",
    "RAY_TRACING": "EMULATED",
    "XR_SURFACE": "SUPPORTED"
  },
  "performance": {
    "initMs": 2450,
    "avgFps": 62.1,
    "minFps": 31.5
  },
  "sessionId": "a1b2c3d4...",
  "timestamp": "2025-10-06T12:30:45Z"
}
```

## Troubleshooting

### Common Issues

#### Backend Denied: "Missing required features"

**Cause**: GPU doesn't support compute shaders, ray tracing, or XR surfaces
**Solution**: Ensure drivers are up to date. Check minimum requirements in platform matrix.

#### Initialization Timeout

**Cause**: Backend initialization exceeded 3-second budget
**Solution**: Check GPU driver stability. Review telemetry for performance bottlenecks.

#### Low Frame Rate

**Cause**: Performance below 60 FPS target or 30 FPS minimum
**Solution**: Reduce scene complexity, enable LOD systems, check GPU utilization.

### Debug Mode

Enable debug telemetry for verbose logging:

```kotlin
val config = RendererConfig(debug = true)
```

## Known Limitations (Beta)

1. **WebGPU Ray Tracing**: Limited browser support (experimental in Chrome/Safari)
2. **Mobile Ray Tracing**: Only available on high-end devices (Adreno 7xx, Mali G710+)
3. **Vulkan Environment Maps/Mipmaps**: Albedo textures sample correctly, but environment reflection maps still fall
   back to neutral probes and GPU-side mipmap generation is pending.
4. **Legacy GPU Support**: No fallback to reduced-fidelity modes (fail-fast by design)

## Roadmap

### Post-Beta (v0.2.0)

- [ ] Expanded ray tracing support across more GPUs
- [ ] WebGL2 fallback option (opt-in)
- [ ] Enhanced XR features (hand tracking, spatial audio)
- [ ] Performance profiling tools

## Feedback & Support

- **Issues**: https://github.com/materia/materia/issues
- **Discussions**: https://github.com/materia/materia/discussions
- **Documentation**: https://materia.io/docs

---

**Note**: This is a beta release. APIs may change before stable 1.0 release.
