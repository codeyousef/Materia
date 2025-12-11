# Changelog

All notable changes to the Materia library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.1.1] - 2025-01-10

### Fixed

- **Moved Effect Composer classes to published artifact**: `FullScreenEffectPass`, `EffectComposer`, and `EffectPipelineFactory` are now in the `io.materia.effects` package (root module) instead of `io.materia.engine.render` (materia-engine). This ensures they are included in the published Maven artifacts.

### Added

- **`Disposable` interface** (`io.materia.core`): Standard interface for resource cleanup
- **`renderToScreen` property** on `FullScreenEffectPass`: Marks the final pass in a chain that renders directly to the screen

## [0.3.1.0] - 2025-01-10

### Added

#### New: Effect Composer & Rendering Pipeline (`io.materia.engine.render`)

A complete post-processing orchestration layer that bridges `FullScreenEffect` with the WebGPU rendering pipeline, inspired by Three.js's `EffectComposer`.

**FullScreenEffectPass**
- Wraps `FullScreenEffect` for use in rendering pipelines
- Dirty tracking for uniform buffer updates
- Automatic resolution uniform updates on resize
- Shader code caching for performance
- DSL builder: `FullScreenEffectPass.create { fragmentShader = "..."; uniforms { ... } }`

**EffectComposer**
- Three.js-style pass chain management
- Add, remove, insert, and reorder passes
- Size propagation to all passes
- Enable/disable individual passes
- Automatic cleanup with `dispose()`

**EffectPipelineFactory**
- Generates GPU pipeline descriptors from passes
- Blend mode translation (`OPAQUE`, `ALPHA_BLEND`, `ADDITIVE`, `MULTIPLY`)
- Bind group layout generation for uniforms and input textures
- Ready for WebGPU `createRenderPipeline()` integration

### Technical Details

- **44 new unit tests** covering all new functionality
- TDD approach: tests written before implementation
- Integrates with existing `EngineRenderer` FXAA pattern
- Pure Kotlin implementation in `commonMain`

### Example Usage

```kotlin
// Create effect passes
val vignettePass = FullScreenEffectPass.create {
    fragmentShader = vignetteShader
    blendMode = BlendMode.ALPHA_BLEND
}

val colorGradingPass = FullScreenEffectPass.create {
    fragmentShader = colorGradingShader
    uniforms { float("gamma") }
}

// Compose into a chain
val composer = EffectComposer(width = 1920, height = 1080)
composer.addPass(vignettePass)
composer.addPass(colorGradingPass)

// Update uniforms in render loop
colorGradingPass.updateUniforms { set("gamma", 2.2f) }

// Get pipeline descriptor for GPU
val descriptor = EffectPipelineFactory.createDescriptor(colorGradingPass)
```

## [0.3.0.0] - 2025-12-10

### Added

#### New: Effects Module (`io.materia.effects`)

A comprehensive set of high-level APIs for fullscreen shader effects and WebGPU rendering, designed to dramatically reduce boilerplate code for common use cases.

**UniformBlock Builder**
- Type-safe uniform buffer layout management with automatic WebGPU alignment
- Supports `float`, `int`, `vec2`, `vec3`, `vec4`, `mat3`, `mat4`, and arrays
- Automatic padding calculations for 8-byte (vec2) and 16-byte (vec3/vec4/mat) alignment
- WGSL struct generation with `toWGSL(structName)`
- DSL builder: `uniformBlock { float("time"); vec4("color") }`
- `UniformUpdater` for type-safe value updates

**FullScreenEffect Class**
- Simplified API for fullscreen shader effects
- Automatic fullscreen triangle vertex shader (optimized 3-vertex approach, no vertex buffer needed)
- Fragment-shader-only API - just provide your WGSL fragment code
- Built-in UV coordinates passed to fragment shader
- Integration with `UniformBlock` for type-safe uniforms
- Configurable blend modes (`OPAQUE`, `ALPHA_BLEND`, `ADDITIVE`, `MULTIPLY`, `PREMULTIPLIED_ALPHA`)
- DSL builder: `fullScreenEffect { fragmentShader = "..."; uniforms { ... } }`

**WGSLLib Snippet Library**
- Reusable WGSL shader code snippets for common operations
- `Hash`: `HASH_21`, `HASH_22`, `HASH_31`, `HASH_33` - pseudo-random functions
- `Noise`: `VALUE_2D`, `PERLIN_2D`, `SIMPLEX_2D`, `WORLEY_2D` - procedural noise
- `Fractal`: `FBM`, `TURBULENCE`, `RIDGED` - multi-octave noise
- `Color`: `COSINE_PALETTE`, `HSV_TO_RGB`, `RGB_TO_HSV`, `SRGB_TO_LINEAR`, `LINEAR_TO_SRGB`
- `Math`: `REMAP`, `SMOOTHSTEP_CUBIC`, `SMOOTHSTEP_QUINTIC`, `ROTATION_2D`
- `SDF`: `CIRCLE`, `BOX`, `ROUNDED_BOX`, `LINE` - signed distance field primitives

**RenderLoop Utility**
- Animation loop management with timing utilities
- `FrameInfo` with `deltaTime`, `totalTime`, `realTime`, `frameCount`, `fps`
- `timeScale` for slow motion / fast forward effects
- `pause()` / `resume()` functionality
- `maxDeltaTime` clamping to handle lag spikes gracefully
- `reset()` to clear all timing state

**WebGPUCanvasConfig**
- Configuration and state management for WebGPU canvas
- `WebGPUCanvasOptions` with `alphaMode`, `powerPreference`, DPR handling
- `CanvasState` tracking logical/physical size and aspect ratio
- `InitResult` sealed class for comprehensive error handling
- Resize callback support
- DSL builder: `webGPUCanvasOptions { alphaMode = AlphaMode.PREMULTIPLIED }`

### Technical Details

- **113 unit tests** covering all new functionality
- All tests passing on JVM target
- Pure Kotlin implementation in `commonMain` (cross-platform)
- Zero external dependencies beyond existing Materia core

### Example Usage

```kotlin
// Aurora-style fullscreen effect
val aurora = fullScreenEffect {
    fragmentShader = """
        ${WGSLLib.Hash.HASH_22}
        ${WGSLLib.Fractal.FBM}
        ${WGSLLib.Color.COSINE_PALETTE}
        
        @fragment
        fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
            let n = fbm(uv * 3.0 + u.time * 0.1, 6) * 0.5 + 0.5;
            let color = cosinePalette(n, u.paletteA.rgb, u.paletteB.rgb, u.paletteC.rgb, u.paletteD.rgb);
            return vec4<f32>(color, 0.85);
        }
    """
    uniforms {
        float("time")
        vec2("resolution")
        vec4("paletteA")
        vec4("paletteB")
        vec4("paletteC")
        vec4("paletteD")
    }
    blendMode = BlendMode.ALPHA_BLEND
}

val loop = RenderLoop { frame ->
    aurora.updateUniforms {
        set("time", frame.totalTime)
        set("resolution", canvas.width.toFloat(), canvas.height.toFloat())
    }
    // render...
}
loop.start()
```

## [0.2.0.0] - 2025-12-06

### Changed

#### Major: Migration to wgpu4k and korlibs-math

This release represents a major architectural change, replacing custom GPU backends and math implementations with battle-tested libraries while maintaining the same API surface.

**GPU Abstraction Layer**
- **Migrated to wgpu4k-toolkit v0.1.1** - Unified cross-platform GPU abstraction using wgpu-native
  - Single `wgpu4k` dependency in commonMain replaces all platform-specific GPU code
  - WebGPU backend for JavaScript/Browser (via browser's native WebGPU API)
  - Vulkan/Metal backend for JVM and Android (via wgpu-native)
  - Removed ~1500 lines of custom LWJGL Vulkan code from JVM
  - Removed ~1000 lines of custom WebGPU bindings from JS
  - Removed custom C++/JNI native Vulkan bridge for Android (`materia-gpu-android-native` module deleted)

**Math Library**
- **Migrated to korlibs-math v6.0.0** - Production-ready math library from korge ecosystem
  - Replaced custom `Vector3`, `Matrix4`, `Quaternion` with korma equivalents
  - Added `KormaInterop.kt` bridge for seamless integration
  - Removed custom `MathObjectPools.kt` (korma handles pooling internally)

### Added

- **JS Browser Examples**
  - Fixed webpack `publicPath` configuration for all examples
  - Added `index.html` for force-graph example
  - Fixed canvas positioning and sizing issues
  - Improved WebGPU adapter detection and SwiftShader fallback warnings

- **Build Improvements**
  - Added `binaries.executable()` to JS examples for webpack dev server
  - Webpack config files (`publicPath.js`) for proper asset loading

### Fixed

- **Android Examples**
  - Copied missing SPIR-V shader assets to embedding-galaxy-android and force-graph-android
  - Android minSdk set to 28 (required by wgpu4k-toolkit)

- **JS/Browser Examples**  
  - Fixed "Automatic publicPath is not supported" webpack error
  - Fixed canvas not finding correct element ID (materia-canvas vs triangle-canvas)
  - Fixed force-graph rendering outside visible viewport
  - Disabled verbose frame logging that was impacting performance

### Removed

- **Deleted `materia-gpu-android-native` module** - Custom C++/JNI Vulkan bridge replaced by wgpu4k
- **Removed LWJGL Vulkan dependencies from JVM** - wgpu4k-toolkit handles GPU abstraction
- **Removed custom WebGPU JS bindings** - wgpu4k provides unified API

### Technical Details

- **Runtime Requirements**:
  - **JVM: Java 22+** (wgpu4k-toolkit is compiled with class file version 66.0)
  - Android: minSdk 28
  
- **Dependencies**:
  - `wgpu4k-toolkit`: 0.1.1 (commonMain - all platforms)
  - `korlibs-math`: 6.0.0
  - Android minSdk: 28 (up from 24)
  
- **Platform Support** (all via wgpu4k-toolkit):
  - ✅ JVM (Vulkan/Metal via wgpu-native) - requires Java 22+
  - ✅ JS/Browser (native WebGPU API)
  - ✅ Android (Vulkan via wgpu-native)

- **Architecture**: All GPU code now uses a single unified API:
  - `expect`/`actual` pattern wraps wgpu4k types
  - Platform context initialization: `glfwContextRenderer()` (JVM), `canvasContextRenderer()` (JS), `androidContextRenderer()` (Android)
  - Type conversion functions bridge Materia types to wgpu4k types

### Migration Guide

The public API remains largely unchanged. Internal math types now use korma:

```kotlin
// Types are compatible - Vec3/Vector3 interop via KormaInterop
import korlibs.math.geom.Vector3
import korlibs.math.geom.Matrix4
import korlibs.math.geom.Quaternion

// Use extension functions for conversion if needed
import io.materia.core.math.toVec3
import io.materia.core.math.toKormaVector3
```

## [0.1.0] - 2025-10-04

### Added
- Initial library structure and Kotlin Multiplatform setup
- Core math library (Vector3, Matrix4, Quaternion)
- Scene graph system (Object3D, Scene, Camera)
- Basic geometry classes (Box, Sphere, Plane)
- WebGL renderer implementation (baseline)

[Unreleased]: https://github.com/yousef/Materia/compare/v0.1.0...HEAD

[0.1.0]: https://github.com/yousef/Materia/releases/tag/v0.1.0
