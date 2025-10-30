# Materia Documentation

> **Comprehensive documentation for Materia - A Kotlin Multiplatform 3D Graphics Library**

## 🚀 Quick Start

- **[Getting Started Guide](guides/getting-started.md)** - Set up your first 3D scene
- **[Basic Usage Examples](examples/basic-usage.md)** - Learn through working code
- **[API Reference](api-reference/README.md)** - Complete API documentation

## 📚 What is Materia?

Materia is a Kotlin Multiplatform library providing Three.js-inspired 3D graphics capabilities with
WebGPU/Vulkan
rendering backends. Write 3D applications once and deploy across **JVM, Web (JavaScript), and Native** platforms.

## ✨ Core Features

### 🎯 Implemented & Stable

| Feature          | Status              | Description                                           |
|------------------|---------------------|-------------------------------------------------------|
| **Math Library** | ✅ Stable            | Vector3, Matrix4, Quaternion, Euler, Color            |
| **Scene Graph**  | ✅ Stable            | Object3D hierarchy, transformations, parenting        |
| **Cameras**      | ✅ Stable            | Perspective, Orthographic, Array, Cube, Stereo        |
| **Geometries**   | ✅ Stable            | 15+ primitive and advanced geometry types             |
| **Materials**    | ✅ Stable            | Basic, Lambert, Phong, Standard, Physical, Toon       |
| **Animation**    | ✅ Stable            | Skeletal animation, IK, morph targets, state machines |
| **Physics**      | ✅ Platform-specific | Rapier (Web), Bullet (JVM)                            |
| **Controls**     | ✅ Stable            | Orbit, FirstPerson, Fly, Trackball, Arcball           |
| **Audio**        | ✅ Platform-specific | Spatial audio, analysis, positional                   |
| **XR Support**   | ✅ Beta              | VR/AR sessions, hand tracking, gaze tracking          |

### 🚧 In Development

| Feature              | Status         | Notes                                               |
|----------------------|----------------|-----------------------------------------------------|
| **Lighting System**  | ⚠️ Partial     | Type definitions exist, full implementation pending |
| **Asset Loaders**    | 📋 Planned     | GLTF, OBJ, FBX support planned                      |
| **Post-Processing**  | 🚫 Disabled    | Implemented but disabled on Windows build           |
| **Native Renderers** | ⚠️ In Progress | Vulkan implementation in progress                   |

## 📖 Documentation Structure

### [API Reference](api-reference/README.md)

Complete API documentation organized by module:

#### Core Modules

- **[Core](api-reference/core/README.md)** - Math primitives, scene graph, utilities ✅
- **[Camera](api-reference/camera/README.md)** - Camera systems ✅ (planned)
- **[Geometry](api-reference/geometry/README.md)** - Geometry classes ✅ (planned)
- **[Material](api-reference/material/README.md)** - Material system ✅ (planned)
- **[Renderer](api-reference/renderer/README.md)** - Rendering system ✅ (planned)

#### Advanced Modules

- **[Animation](api-reference/animation/README.md)** - Animation system ✅ (planned)
- **[Physics](api-reference/physics/README.md)** - Physics integration ✅ (planned)
- **[Controls](api-reference/controls/README.md)** - Camera controls ✅ (planned)
- **[Audio](api-reference/audio/README.md)** - Audio system ✅ (planned)
- **[XR](api-reference/xr/README.md)** - VR/AR support ✅ (planned)

### [User Guides](guides/)

Step-by-step tutorials and guides:

- **[Getting Started](guides/getting-started.md)** - Your first Materia application
- **[Platform-Specific Setup](guides/platform-specific.md)** - JVM, JS, Native configuration
- **Materials Guide** (planned) - Working with materials
- **Animation Guide** (planned) - Animation system
- **Physics Guide** (planned) - Physics integration

### [Architecture](architecture/)

System architecture and design:

- **[Overview](architecture/overview.md)** - System architecture
- **Rendering Pipeline** (planned) - How rendering works
- **Cross-Platform Strategy** (planned) - Multiplatform implementation
- **Performance Optimization** (planned) - Performance best practices

### [Examples](examples/)

Real, working code examples:

- **[Basic Usage](examples/basic-usage.md)** - Simple examples
- **Advanced Patterns** (planned) - Advanced techniques
- **Platform Examples** (planned) - Platform-specific code

## 🏗️ Platform Support

### Current Platform Status

| Platform        | Math & Scene | Renderer                | Physics    | Status   |
|-----------------|--------------|-------------------------|------------|----------|
| **JVM**         | ✅ Complete   | ⚠️ In Progress (Vulkan) | ✅ Bullet   | 🔄 Beta  |
| **JavaScript**  | ✅ Complete   | ✅ WebGL2                | ✅ Rapier   | ✅ Stable |
| **Linux x64**   | ✅ Complete   | ⚠️ Planned (Vulkan)     | 📋 Planned | 🔄 Alpha |
| **Windows x64** | ✅ Complete   | ⚠️ Planned (Vulkan)     | 📋 Planned | 🔄 Alpha |

### Legend

- ✅ Complete - Fully implemented and tested
- 🔄 Beta - Implemented, may have rough edges
- ⚠️ In Progress - Partial implementation
- 📋 Planned - Not yet implemented
- 🚫 Disabled - Implemented but currently disabled

## 🎯 Quick Example

### Creating Your First Scene

```kotlin
import io.materia.core.scene.*
import io.materia.core.math.*
import io.materia.geometry.primitives.*
import io.materia.material.*
import io.materia.camera.*

// Create scene
val scene = Scene()

// Add camera
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 0.1f,
    far = 1000f
)
camera.position.z = 5f

// Create a rotating cube
val geometry = BoxGeometry(1f, 1f, 1f)
val material = SimpleMaterial(
    albedo = Color(0xff6b46c1),
    metallic = 0.3f,
    roughness = 0.4f
)
val cube = Mesh(geometry, material)
scene.add(cube)

// Animation loop
fun animate(deltaTime: Float) {
    cube.rotation.x += 0.01f
    cube.rotation.y += 0.01f
    renderer.render(scene, camera)
}
```

See [Getting Started](guides/getting-started.md) for a complete walkthrough.

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    commonMain {
        implementation("io.materia:materia-core:0.1.0-alpha01")
    }
}
```

### Maven

```xml
<dependency>
    <groupId>io.materia</groupId>
    <artifactId>materia-core</artifactId>
    <version>0.1.0-alpha01</version>
</dependency>
```

## 🎯 Performance Targets

Materia is designed with performance in mind:

- **Frame Rate**: 60 FPS target
- **Triangle Count**: 100k+ triangles on standard hardware
- **Library Size**: <5MB base library (modular architecture)
- **Initialization**: Fast renderer startup (<1s target)

Performance optimization features:

- Object pooling for math primitives
- Dirty flag optimization for matrix updates
- Efficient scene graph traversal
- Platform-optimized rendering backends

## 🔧 Development

### Building Materia

```bash
# Build all platforms
./gradlew build

# Build specific platform
./gradlew jvmMainClasses    # JVM
./gradlew jsMainClasses     # JavaScript
./gradlew compileKotlinLinuxX64  # Linux Native
```

### Running Examples

```bash
# Desktop example (JVM with LWJGL)
./gradlew :examples:basic-scene:runJvm

# Web example (opens in browser)
./gradlew :examples:basic-scene:runJs
```

### Running Tests

```bash
# All tests
./gradlew test

# Platform-specific
./gradlew jvmTest
./gradlew jsTest
```

## 🤝 Contributing

We welcome contributions! Here's how to get started:

1. Read the codebase to understand what's actually implemented
2. Check existing documentation for coverage gaps
3. Ensure examples use only implemented features
4. Follow Kotlin coding conventions
5. Add KDoc documentation for public APIs

See the main [CONTRIBUTING.md](../../CONTRIBUTING.md) for detailed guidelines.

## 📋 Documentation Coverage

### Current Status

- **Core Module**: ~80% documented (Vector3, Object3D fully documented)
- **Camera Module**: ~20% documented (needs expansion)
- **Geometry Module**: ~10% documented (needs creation)
- **Material Module**: ~10% documented (needs creation)
- **Animation Module**: ~5% documented (needs creation)

**Goal**: 90%+ documentation coverage for all public APIs

## 🔗 Resources

- **[GitHub Repository](https://github.com/your-org/materia)** - Source code
- **[API Docs (Dokka)](https://materia.io/api)** - Generated API documentation
- **[Examples Repository](examples/)** - Working code examples
- **[Issue Tracker](https://github.com/your-org/materia/issues)** - Report bugs

## 📄 License

Materia is licensed under the Apache License 2.0. See [LICENSE](../../LICENSE) for details.

## 🙏 Acknowledgments

- Inspired by [Three.js](https://threejs.org/) for the elegant 3D API design
- Built on [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Powered by WebGL2, Vulkan, and modern graphics APIs

---

**Last Updated**: 2025-10-04
**Version**: 0.1.0-alpha01
**Documentation Status**: 🚧 In Progress
