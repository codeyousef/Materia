# Changelog

All notable changes to the Materia library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0.0] - 2025-12-05

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
