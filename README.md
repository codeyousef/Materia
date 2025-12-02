<div align="center">
  <img src="materia-logo.png" alt="Materia Logo" width="200" />
  <h1>Materia</h1>
  <p><strong>Kotlin Multiplatform 3D Engine</strong></p>
  <p>Modern rendering toolkit targeting WebGPU, Vulkan, and emerging XR surfaces with Three.js-style ergonomics.</p>
  
  <br/>
  
  > ⚠️ **Alpha Software** – Materia is under active development. APIs may change between releases. Not recommended for production use yet.
</div>

<p align="center">
  <a href="https://kotlinlang.org/docs/multiplatform.html">
    <img src="https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg" alt="Kotlin 2.2.20" />
  </a>
  <a href="https://kotlinlang.org/docs/multiplatform.html">
    <img src="https://img.shields.io/badge/Targets-JVM%20|%20JS%20|%20Android%20|%20Native-34C759.svg" alt="KMP Targets" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg" alt="License: Apache 2.0" />
  </a>
  <img src="https://img.shields.io/badge/Status-Alpha-red.svg" alt="Alpha Status" />
</p>

---

## ✨ Highlights

- **Unified Rendering API** – Write once, render everywhere with expect/actual abstractions over WebGPU, Vulkan (LWJGL), and Metal (MoltenVK)
- **Three.js-Style API** – Familiar scene graph, materials, cameras, controls, and lighting patterns for easy adoption
- **Performance-First** – Arena allocators, uniform ring buffers, GPU resource pooling, and automatic instancing
- **Comprehensive Loaders** – GLTF 2.0, OBJ, FBX, Collada, PLY, STL, DRACO, KTX2, HDR/EXR textures, fonts
- **Full Controls Suite** – OrbitControls, FlyControls, PointerLockControls, TrackballControls, DragControls, TransformControls
- **Debug Helpers** – AxesHelper, GridHelper, BoxHelper, ArrowHelper, CameraHelper, light helpers
- **Cross-Platform Audio** – Positional audio and analyser abstractions aligned with the camera system
- **Production Ready** – Built-in validation, Kover coverage, and dependency scanning pipelines

---

## 🎯 Platform Support

| Platform | Backend | Status |
|----------|---------|--------|
| **Browser (JS/WASM)** | WebGPU (WebGL2 fallback) | ✅ Ready |
| **JVM (Linux/macOS/Windows)** | Vulkan via LWJGL 3.3.6 | ✅ Ready |
| **Android** | Vulkan (API 24+) | ✅ Ready |
| **Native (macOS/iOS)** | MoltenVK | 🟡 In Progress |

---

## 🚀 Quick Start

### Prerequisites

- JDK 17+
- Node.js ≥ 18 (for JS target)
- Android SDK API 34 (for Android target)
- Vulkan drivers or WebGPU-enabled browser

### Build

```bash
git clone https://github.com/codeyousef/KreeKt.git
cd KreeKt

# Build all targets
./gradlew build

# Run tests
./gradlew test

# Generate coverage report
./gradlew koverHtmlReport
```

---

## 📦 Examples

Run any example with Gradle:

```bash
# Triangle demo
./gradlew :examples:triangle:runJvm          # Desktop
./gradlew :examples:triangle:jsBrowserRun    # Browser

# VoxelCraft (Minecraft-style demo)
./gradlew :examples:voxelcraft:runJvm
./gradlew :examples:voxelcraft:jsBrowserRun

# Embedding Galaxy (particle visualization)
./gradlew :examples:embedding-galaxy:runJvm

# Force Graph (network visualization)  
./gradlew :examples:force-graph:runJvm
```

**Browser Requirements:** Chrome 113+, Edge 113+, or Firefox Nightly with WebGPU enabled.

**Android:** Connect a device/emulator with Vulkan support, then run `:examples:triangle-android:installDebug`.

---

## 🏗️ Project Structure

```
materia-engine/         # Core: scene graph, materials, animation
materia-gpu/            # GPU abstraction: WebGPU/Vulkan backends
materia-postprocessing/ # Post-processing effects pipeline
materia-validation/     # Production readiness validation CLI
examples/               # Multiplatform example applications
docs/                   # API reference and guides
```

---

## 📖 Documentation

- [Getting Started Guide](docs/guides/getting-started.md)
- [API Reference](docs/api-reference/README.md)
- [Architecture Overview](docs/architecture/overview.md)
- [Platform-Specific Notes](docs/guides/platform-specific.md)

---

## 🔧 Development

### Quality Gates

```bash
./gradlew build                       # Compile + tests
./gradlew koverVerify                 # Coverage check (50% minimum)
./gradlew lintDebug                   # Android lint
./gradlew dependencyCheckAnalyze      # Security audit
./gradlew validateProductionReadiness # Full validation suite
```

### Shader Compilation

The project uses dual shader sources:
- **WebGPU (JS):** WGSL strings in Kotlin code
- **Vulkan (JVM):** Pre-compiled SPIR-V in `src/jvmMain/resources/shaders/`

Run `./gradlew compileShaders` to regenerate SPIR-V from WGSL sources.

---

## 🤝 Contributing

Contributions welcome! Please:

1. Fork and create a feature branch
2. Add tests for new functionality
3. Run quality gates before submitting
4. Open a PR with clear description

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

[Apache License 2.0](LICENSE) – Use freely in commercial and open source projects.

---

<p align="center">
  <sub>Built with ❤️ using Kotlin Multiplatform</sub>
</p>
