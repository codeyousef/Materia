# Changelog

All notable changes to the Materia library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0.0] - 2026-04-12

### Added

- **`Data3DTexture` public API**: Added a multiplatform 3D texture type for byte-, float-, and int-backed volume data, including solid-color and noise helpers plus voxel read/write utilities.
- **`Data3DTexture` contract coverage**: Added root multiplatform tests for 3D texture creation, mutation, cloning, and data access behavior.
- **Volume-texture material consumer**: `MeshBasicMaterial.map` can now be a `Data3DTexture`, with centered local-space volume sampling wired through the built-in WebGPU and Vulkan material pipelines.
- **Runnable volume-texture example**: Added a new multiplatform `examples:volume-texture` scene that renders a generated `Data3DTexture` on both a box and a sphere through the root renderer API.
- **Android example smoke automation**: Added adb-aware `smokeAndroid` tasks for the triangle Android example and its wrapper project, plus README guidance for install/run/smoke validation.
- **Volume texture fallback coverage**: Added tests for shared CPU volume sampling and Vulkan shader generation when `MeshBasicMaterial.map` is a `Data3DTexture`.
- **WebGL fallback smoke coverage**: Added a JS runtime smoke test that forces `RendererFactory` onto the WebGL fallback path and renders a `Data3DTexture`-mapped mesh.
- **Volume texture guide**: Added documentation covering `Data3DTexture` usage, local-space coordinate mapping, and backend-specific behavior.

### Changed

- **Android renderer implementation**: Replaced the stubbed public Android renderer with a real wgpu4k-backed renderer and routed the Android factory to the concrete implementation.
- **Basic material texture bindings**: The built-in basic material descriptor now exposes optional 3D volume texture and sampler bindings alongside existing albedo bindings.
- **Android and WebGL fallback rendering**: `MeshBasicMaterial.map = Data3DTexture` now falls back to CPU volume sampling that bakes colors into mesh vertices when the backend does not use the GPU 3D texture path.

### Fixed

- **JS WebGPU material typing**: Corrected the WebGPU material plumbing to use the shared scene material interface so `MeshStandardMaterial` texture handling reaches the intended code paths.
- **Android engine surface bootstrap**: `EngineRenderer` now pre-initializes the GPU context from the provided render surface before requesting an adapter, fixing Android example launches that previously failed with `Call GpuSurface.attachRenderSurface first`.
- **Android wgpu compatibility diagnostics**: Android renderer/bootstrap failures now detect the upstream `wgpu4k` `ValueLayout.Companion` runtime mismatch and surface an explicit compatibility error instead of the raw linkage failure.

## [0.3.6.0] - 2026-04-12

### Fixed

- **GLTFLoader top-level model fetch path**: The root model URL is now fetched exactly as provided instead of being resolved against its own derived base path, preventing duplicated prefixes such as `models/models/example.glb` and `/assets/models//assets/models/example.glb`.
- **Asset URI resolution for rooted and absolute paths**: Dependent glTF resource URIs now treat rooted paths (`/foo`), protocol-relative paths (`//foo`), and absolute URLs (`https://...`) as already resolved so they are not prefixed again.

### Added

- **GLTF loader regression tests**: Added coverage for top-level relative and rooted model URLs plus dependent relative and rooted asset URIs to prevent URL re-prefixing regressions.

## [0.3.5.0] - 2026-02-27

### Fixed

- **FirstPersonControls movement inversion**: Fixed W/S key movement direction — W now moves forward (+Z) and S moves
  backward (-Z), correcting previously inverted controls.
- **WGSL struct trailing semicolons**: Removed trailing semicolons from WGSL struct definitions in all material
  shaders (basic and PBR), fixing compatibility with stricter WGSL parsers.
- **WGSL shader variable naming**: Renamed `in`/`out` variables to `input`/`output` throughout all material shaders to
  avoid potential reserved word conflicts in WGSL.
- **Varying location mismatch**: Fixed inter-stage varying locations for UV, UV2, and tangent attributes. Varying
  locations are now assigned independently from vertex input locations, preventing mismatches in the basic and PBR
  shader pipelines.
- **MeshStandardMaterial without environment map**: MeshStandardMaterial now gracefully downgrades to MeshBasicMaterial
  when no environment map is available, instead of skipping the mesh entirely.
- **WebGPU canvas format**: Renderer now queries `navigator.gpu.getPreferredCanvasFormat()` instead of hardcoding
  `bgra8unorm`, fixing rendering on platforms that prefer `rgba8unorm`.
- **WebGPU pipeline color target format**: Pipeline color target format now matches the queried canvas format instead of
  using a hardcoded default.
- **WebGPU resize reconfiguration**: `setSize()` now reconfigures the canvas context after dimension changes, fixing
  blank rendering after resize on Firefox.
- **WebGPU render pass colorAttachments**: Changed from `arrayOf()` (Kotlin typed array) to `js("[]").push()` (native JS
  array) for compatibility with all WebGPU implementations.
- **WebGPU swapchain alpha mode**: Changed from `opaque` to `premultiplied` in `WebGPUSwapchain` for correct alpha
  compositing.
- **WebGL material color extraction**: `WebGLRenderer` now extracts color from both `MeshBasicMaterial` and
  `MeshStandardMaterial`, instead of only `MeshBasicMaterial`.
- **Reduced render loop log spam**: Removed per-frame `T010 Performance` console.log that fired every frame; metrics
  remain available via the stats property.

### Added

- **WGSL shader validation probe**: On initialization, the WebGPU renderer now compiles a representative test shader and
  checks for compilation errors (via both `getCompilationInfo()` and `pushErrorScope/popErrorScope`). If the browser
  cannot compile WGSL shaders, initialization returns an error for graceful fallback.
- **Firefox+Linux blit workaround**: Detects Firefox on Linux (Bug 1966566) and renders to an offscreen canvas, blitting
  the result to the visible canvas via a 2D context for correct presentation.
- **Diagnostic raw clear**: Added `WebGPURenderer.diagnosticRawClear()` method for debugging presentation issues —
  performs a red clear bypassing the full pipeline.
- **Diagnostic frame logging**: First 3 frames log detailed render pass, mesh, and submission diagnostics
  unconditionally for easier debugging.
- **WebGPU error listener**: Added `uncapturederror` event listener on the GPU device to surface validation errors to
  the console.
- **WebGPU type declarations**: Added `GPUCompilationInfo`, `GPUCompilationMessage` external interfaces and
  `pushErrorScope`/`popErrorScope` methods to `GPUDevice`.
- **Partial init cleanup**: Added `cleanupPartialInit()` to properly release GPU resources when initialization fails
  after partial setup.
- **MaterialDescriptorRegistry**: `COLOR` attribute is now required (not optional) for both basic and standard
  materials, ensuring vertex colors are always bound.
- **MaterialShaderLibrary replacements**: Basic and PBR shader descriptors now include default empty replacement values
  for all template placeholders, preventing unresolved `{{...}}` tokens.
- **RenderPassManager diagnostics**: Added `enableDiagnostics` flag for logging render pass descriptor details.
- **WebGPU shader source logging**: Shader module creation now logs the full WGSL source for debugging.

### Changed

- **WebGPURenderer context handling**: Canvas context is now stored as both typed (`GPUCanvasContext`) and dynamic
  references, using dynamic dispatch for `getCurrentTexture()` to match the working pattern from SigilEffectCanvas.
- **Error handling in render loop**: Catch block now uses `dynamic` instead of `Exception` to capture all JS errors
  including non-Kotlin exceptions.

## [0.3.4.6] - 2025-12-15

### Fixed

- **WebGL uniform name mismatch**: Fixed uniform lookups in WebGL using plain names without `u_` prefix, matching how GLSL shaders declare uniforms. Previously, uniforms were being looked up with an incorrect prefix causing them to not be found.

## [0.3.4.3] - 2025-12-13

### Fixed

- **WebGPUEffectComposer**: Fixed runtime error with `@JsExport` by removing redundant `.asDynamic()` calls on `js("{}")` results. The `js()` function already returns `dynamic`, so calling `.asDynamic()` on plain JavaScript objects caused errors at runtime since `asDynamic()` is a Kotlin extension that doesn't exist on JS objects.
- **Build configuration**: Added `publishingType=AUTOMATIC` to Maven Central Portal upload to auto-publish after validation.

## [0.3.4.2] - 2025-12-13

### Fixed

- **WebGPUEffectComposer**: Added `@JsExport` annotation to ensure the class and its methods (including `render()`) are properly exported with stable names in the compiled JavaScript. Without this annotation, Kotlin/JS IR backend mangles method names, making them inaccessible from JavaScript consumers.

## [0.3.4.1] - 2025-12-13

### Fixed

- **WebGPUEffectComposer**: Fixed ping-pong texture rendering logic in `render()` method. The previous implementation had incorrect state tracking that caused passes to read from the wrong intermediate texture. The fix ensures proper alternation between texture A and B for multi-pass effect chains.

## [0.3.4.0] - 2025-12-13

### Added

#### WebGPU Effect Composer (`io.materia.renderer.webgpu.WebGPUEffectComposer`)

A complete post-processing effect composer for WebGPU, providing symmetric rendering capabilities to `WebGLEffectComposer`. This bridges the API asymmetry where only WebGL had a composable render pipeline.

**WebGPUEffectComposer**
- WebGPU equivalent of `WebGLEffectComposer` for WGSL shaders
- Manages chains of `FullScreenEffectPass` objects with automatic pipeline creation
- Ping-pong texture system for multi-pass rendering
- Automatic uniform buffer management and dirty tracking
- Full blend mode support (`OPAQUE`, `ALPHA_BLEND`, `ADDITIVE`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `PREMULTIPLIED_ALPHA`)
- Lazy pipeline and resource creation for optimal performance
- Pass chain management (add, remove, insert, reorder, swap)
- Enable/disable individual passes
- Automatic size propagation and texture recreation on resize

**Example Usage**
```kotlin
// Create effect composer with WebGPU device
val composer = WebGPUEffectComposer(device, width = 1920, height = 1080)

// Add passes using existing FullScreenEffectPass API
composer.addPass(FullScreenEffectPass.create {
    fragmentShader = vignetteShader
    blendMode = BlendMode.ALPHA_BLEND
})

composer.addPass(FullScreenEffectPass.create(requiresInputTexture = true) {
    fragmentShader = colorGradingShader
    uniforms { float("gamma") }
})

// Update uniforms in render loop
passes[1].updateUniforms { set("gamma", 2.2f) }

// Render to swapchain texture
composer.render(swapchainView)

// Or render single effect directly
composer.renderSingle(singlePass, outputView)
```

### Technical Details

- **1 new source file** for WebGPU effect composer
- **1 new test file** with unit tests
- JS-only implementation in `src/jsMain/kotlin/io/materia/renderer/webgpu`
- Reuses existing `FullScreenEffectPass` and `FullScreenEffect` from `io.materia.effects`

### API Symmetry

| Component | WebGL | WebGPU |
|-----------|-------|--------|
| Composer | `WebGLEffectComposer` | `WebGPUEffectComposer` |
| Pass | `WebGLEffectPass` | `FullScreenEffectPass` |
| Effect | `WebGLFullScreenEffect` | `FullScreenEffect` |
| Has render()? | ✅ Yes | ✅ Yes |


## [0.3.3.0] - 2025-12-11

### Added

#### WebGL Effect System (`io.materia.renderer.webgl`)

A complete post-processing effect system for WebGL, providing the same high-level API as the WebGPU `FullScreenEffect` system but using GLSL shaders.

**WebGLFullScreenEffect**
- WebGL equivalent of `FullScreenEffect` for GLSL fragment shaders
- Automatic fullscreen triangle vertex shader (optimized 3-vertex approach)
- Integration with `UniformBlock` for type-safe uniforms
- Blend mode support (`OPAQUE`, `ALPHA_BLEND`, `ADDITIVE`, `MULTIPLY`, `SCREEN`, `OVERLAY`, `PREMULTIPLIED_ALPHA`)
- DSL builder: `webGLFullScreenEffect { fragmentShader = "..."; uniforms { ... } }`

**WebGLEffectPass**
- Wrapper for `WebGLFullScreenEffect` with pass chain semantics
- Dirty tracking for uniform buffer updates
- Resolution uniform auto-updates on resize
- Input texture support for post-processing chains
- DSL builder: `WebGLEffectPass.create { fragmentShader = "..." }`

**WebGLEffectComposer**
- Pass chain manager for multi-pass WebGL rendering
- Ping-pong framebuffer system for efficient multi-pass effects
- Add, remove, insert, and reorder passes
- Enable/disable individual passes
- Automatic size propagation and framebuffer management

**WebGLEffectUniforms**
- Helper class for managing common uniforms (time, resolution, mouse)
- `WebGLUniformSetter` for type-safe uniform updates by name
- `GLSLUniforms` constants for standard uniform declarations

**GLSLLib - GLSL Shader Snippet Library**
- WebGL equivalent of `WGSLLib` for GLSL shaders
- `Hash`: `HASH_21`, `HASH_22`, `HASH_31`, `HASH_33` - pseudo-random functions
- `Noise`: `VALUE_2D`, `PERLIN_2D`, `SIMPLEX_2D`, `WORLEY_2D` - procedural noise
- `Fractal`: `FBM`, `TURBULENCE`, `RIDGED` - multi-octave noise
- `Color`: `COSINE_PALETTE`, `HSV_TO_RGB`, `RGB_TO_HSV`, `SRGB_TO_LINEAR`, `LINEAR_TO_SRGB`
- `Math`: `REMAP`, `SMOOTHSTEP_CUBIC`, `SMOOTHSTEP_QUINTIC`, `ROTATION_2D`, `PI`, `TAU`
- `SDF`: `CIRCLE`, `BOX`, `ROUNDED_BOX`, `LINE`, `TRIANGLE`, `RING`
- `Effects`: `VIGNETTE`, `FILM_GRAIN`, `CHROMATIC_ABERRATION`, `SCANLINES`
- `Presets`: Combined shader snippets for common use cases

### Technical Details

- **5 new source files** for WebGL effect system
- **3 new test files** with comprehensive unit tests
- All existing tests continue to pass
- JS-only implementation in `src/jsMain/kotlin`

### Example Usage

```kotlin
// Create a WebGL fullscreen effect
val effect = webGLFullScreenEffect {
    fragmentShader = """
        ${GLSLLib.Presets.FRAGMENT_HEADER_WITH_UNIFORMS}
        ${GLSLLib.Hash.HASH_21}
        ${GLSLLib.Noise.VALUE_2D}
        ${GLSLLib.Fractal.FBM}
        ${GLSLLib.Color.COSINE_PALETTE}
        
        void main() {
            float n = fbm(vUv * 10.0, 6);
            vec3 color = cosinePalette(n, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0));
            gl_FragColor = vec4(color, 1.0);
        }
    """
    uniforms {
        float("time")
        vec2("resolution")
    }
    blendMode = BlendMode.ALPHA_BLEND
}

// Or use the composer for multi-pass effects
val composer = WebGLEffectComposer(gl, width, height)
composer.addPass(WebGLEffectPass.create {
    fragmentShader = vignetteShader
})
composer.addPass(WebGLEffectPass.create(requiresInputTexture = true) {
    fragmentShader = blurShader
})
composer.render()
```

## [0.3.2.0] - 2025-01-11

### Added

#### New Blend Modes

- **`BlendMode.SCREEN`**: Screen blending (1 - (1-src) * (1-dst)) that lightens the image. Useful for glow effects, light overlays, and brightening operations.
- **`BlendMode.OVERLAY`**: Combines multiply and screen based on base color luminance. Note: True overlay blending cannot be achieved with fixed-function blend states alone; this maps to MULTIPLY as an approximation. For accurate overlay effects, use shader-based implementation.

#### Matrix Uniform Setters

- **`UniformUpdater.setMat3(name, FloatArray)`**: Set a mat3 uniform from a 9-element float array (column-major order). Handles WGSL std140 layout with proper vec4 padding.
- **`UniformUpdater.setMat3(name, m00..m22)`**: Convenience overload with individual components.

#### Pipeline Factory Enhancements

- **`BlendStateType.SCREEN`**: New blend state type for screen blending (srcFactor=ONE, dstFactor=ONE_MINUS_SRC_COLOR).

### Technical Details

- 12 new unit tests for blend modes and matrix setters
- All existing tests continue to pass

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
