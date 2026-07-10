# Materia Documentation

Welcome to the Materia documentation! Materia is a Kotlin Multiplatform 3D rendering engine that works across JVM, JavaScript/WebAssembly, Android, and Apple beta targets with Three.js-style ergonomics.

## Table of Contents

### Getting Started
- [Quickstart Guide](quickstart.md) - Get up and running in minutes
- [Getting Started](guides/getting-started.md) - Detailed setup and first scene
- [Platform-Specific Guide](guides/platform-specific.md) - JVM, JS, Android, and Apple beta setup
- [Volume Textures](guides/volume-textures.md) - `Data3DTexture` usage and backend behavior
- [Examples](examples/basic-usage.md) - Code samples and demos

### Core Concepts
- [Scene Graph](concepts/scene-graph.md) - Understanding the 3D hierarchy
- [Transformations](concepts/transformations.md) - Position, rotation, and scale
- [Materials & Shading](concepts/materials.md) - PBR and custom materials
- [Lighting](concepts/lighting.md) - Light types and shadows
- [Animation](concepts/animation.md) - Keyframes, skeletal, and morph targets

### API Reference
- [Core API](api-reference/core.md) - Math, scene graph, Object3D
- [Geometry API](api-reference/geometry.md) - Primitives and buffer geometry
- [Material API](api-reference/material.md) - Materials and shaders
- [Camera API](api-reference/camera.md) - Camera types and controls
- [Lighting API](api-reference/lighting.md) - Lights and shadows
- [Animation API](api-reference/animation.md) - Animation system
- [Renderer API](api-reference/renderer.md) - Rendering backends
- [Loader API](api-reference/loader.md) - Asset loading (GLTF, OBJ, textures)
- [Controls API](api-reference/controls.md) - Camera and object controls
- [Texture API](api-reference/texture.md) - Textures and samplers
- [Audio API](api-reference/audio.md) - Positional audio
- [XR API](api-reference/xr.md) - VR/AR support

### Advanced Topics
- [Performance Optimization](advanced/performance.md) - GPU optimization techniques
- [Custom Shaders](advanced/custom-shaders.md) - WGSL and SPIR-V
- [Instancing](advanced/instancing.md) - Efficient rendering of many objects
- [Post-Processing](advanced/post-processing.md) - Effects pipeline
- [Physics Integration](advanced/physics.md) - Collision and dynamics

### Architecture
- [Architecture Overview](architecture/overview.md) - System design
- [Backend Abstraction](architecture/backends.md) - WebGPU vs Vulkan
- [Shader Pipeline](architecture/shaders.md) - Shader compilation flow

---

## Key Features

- **Cross-Platform**: Write once, render on JVM (Vulkan), Browser (WebGPU), and Android
- **Three.js-Style API**: Familiar scene graph, materials, cameras, and lighting patterns
- **Performance-First**: 
  - Arena allocators and uniform ring buffers
  - GPU resource pooling
  - Automatic instancing
  - 60+ FPS on integrated GPUs
- **Complete Asset Pipeline**:
  - GLTF 2.0 with Draco compression
  - OBJ, FBX, PLY, STL loaders
  - KTX2 and EXR texture support
- **Modern Rendering**:
  - Physically-based rendering (PBR)
  - Image-based lighting (IBL)
  - Shadow mapping
  - Post-processing effects
- **Animation System**:
  - Keyframe animation
  - Skeletal animation with GPU skinning
  - Morph targets
  - Animation blending

---

## Platform Support

| Platform | Backend | Status |
|----------|---------|--------|
| **JVM (Desktop)** | Vulkan via LWJGL 3.3.6 | ✅ Ready |
| **Browser (JS/WASM)** | WebGPU (WebGL2 fallback) | ✅ Ready |
| **Android** | Vulkan (API 24+) | ✅ Ready |
| **iOS** | MoltenVK via host-owned `MTKView` / `CAMetalLayer` | 🟡 Beta |
| **macOS Apple Native** | MoltenVK via shared engine/gpu path | 🟡 Beta |

Apple beta runtime model:
- `:examples:triangle` exercises the shared Apple engine path. Use `./gradlew :examples:triangle:runMacos` on macOS, or embed the exported `MateriaTriangle` framework through `examples/triangle-ios-app/MateriaTriangleDemo.xcodeproj` on iOS.
- `examples:volume-texture` currently uses the legacy root `RendererFactory` API. On Apple, the working path is `examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj`, which bundles the generated JS/WebGL build inside a native iOS / Mac Catalyst shell.

---

## Quick Example

```kotlin
import io.materia.engine.scene.Scene
import io.materia.engine.scene.EngineMesh
import io.materia.engine.camera.PerspectiveCamera
import io.materia.engine.material.StandardMaterial
import io.materia.engine.renderer.WebGPURenderer
import io.materia.engine.renderer.WebGPURendererConfig
import io.materia.engine.core.RenderLoop
import io.materia.engine.core.DisposableContainer
import io.materia.geometry.BufferGeometry
import io.materia.core.math.Color
import io.materia.core.math.Vector3

// Create scene
val scene = Scene()

// Add a camera
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 0.1f,
    far = 1000f
).apply {
    position.set(0f, 2f, 5f)
    lookAt(Vector3.ZERO)
}

// Create a mesh with BufferGeometry
val geometry = BufferGeometry().apply {
    setAttribute("position", boxVertices, 3)
}
val material = StandardMaterial(
    color = Color(0f, 1f, 0f),
    metalness = 0.3f,
    roughness = 0.4f
)
val cube = EngineMesh(geometry, material)
scene.add(cube)

// Initialize renderer (works on JVM and JS!)
val renderer = WebGPURenderer(WebGPURendererConfig(
    surface = window.surface,
    width = 1280,
    height = 720
))

// Render loop
val renderLoop = RenderLoop { deltaTime ->
    cube.rotateY(deltaTime * 0.5f)
    renderer.render(scene, camera)
}
renderLoop.start()

// Cleanup
renderLoop.stop()
cube.dispose()
material.dispose()
geometry.dispose()
renderer.dispose()
```

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.1.20"
}

kotlin {
    jvm()
    js(IR) { browser() }
    
    sourceSets {
        commonMain.dependencies {
            implementation("io.materia:materia-engine:0.4.2.0")
        }
    }
}
```

---

## Running Examples

```bash
# Clone the repository
git clone https://github.com/codeyousef/Materia.git
cd Materia

# Run Triangle demo
./gradlew :examples:triangle:runJvm          # Desktop
./gradlew :examples:triangle:jsBrowserRun    # Browser
./gradlew :examples:triangle:runMacos        # Apple Native (macOS host only)
open examples/triangle-ios-app/MateriaTriangleDemo.xcodeproj

# Run Volume Texture demo
./gradlew :examples:volume-texture:runJvm
./gradlew :examples:volume-texture:jsBrowserRun
open examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj

# Run Embedding Galaxy (particles)
./gradlew :examples:embedding-galaxy:runJvm

# Run Force Graph (network visualization)
./gradlew :examples:force-graph:runJvm
```

---

## Getting Help

- Check the documentation in this repository
- Browse the [examples/](../examples/) directory
- [Open an issue](https://github.com/codeyousef/Materia/issues) for bugs
- [Start a discussion](https://github.com/codeyousef/Materia/discussions) for questions

---

## See Also

- [Quickstart Guide](quickstart.md) - Get started quickly
- [API Reference](api-reference/README.md) - Complete API documentation
- [Architecture Overview](architecture/overview.md) - How Materia works
