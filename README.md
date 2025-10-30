<div align="center">
  <img src="docs/private/logo.png" alt="Materia logo" width="180" />
  <h1>Materia</h1>
  <p>Kotlin Multiplatform rendering toolkit targeting WebGPU, Vulkan, and emerging XR surfaces with Three.js-style ergonomics.</p>
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
</p>

---

## Contents

- [Highlights](#highlights)
- [Targets](#targets)
- [Quickstart](#quickstart)
- [Examples](#examples)
- [Modules](#modules)
- [Quality Gates](#quality-gates)
- [Documentation & Roadmap](#documentation--roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Highlights

- **Unified rendering API** – Expect/actual layers wrap WebGPU, Vulkan (via LWJGL), and MoltenVK
  while keeping a single scene graph.
- **Performance-first design** – Arena allocators, uniform ring buffers, GPU resource pooling, and
  instancing utilities tuned to maintain 60 FPS.
- **Loader suite** – GLTF 2.0, OBJ, FBX, and custom asset resolvers with platform-safe data URI +
  Base64 handling.
- **Cross-platform audio** – Listener, positional audio, and analyser abstractions aligned with the
  camera system.
- **Telemetry & validation** – Built-in readiness validator, Kover coverage, lint, and dependency
  check pipelines.

---

## Targets

| Target                        | Backend                     | Notes                                                                        | Status         |
|-------------------------------|-----------------------------|------------------------------------------------------------------------------|----------------|
| **Browser (wasm/JS)**         | WebGPU with WebGL2 fallback | Uses `@webgpu/types` bindings, webpack dev server                            | ✅ Ready        |
| **JVM (Linux/macOS/Windows)** | Vulkan via LWJGL 3.3.6      | GLFW windowing, shader compilation through Tint/Naga                         | ✅ Ready        |
| **Android**                   | Vulkan (API 24+)            | SurfaceView swap chain, Robolectric-friendly fallbacks                       | 🟢 Beta        |
| **Native (macOS/iOS)**        | MoltenVK                    | Expect/actual stubs in place, feature parity tracked in `docs/MVP_STATUS.md` | 🟡 In Progress |

---

## Quickstart

### Prerequisites

- JDK 17+
- Kotlin 2.2.20 toolchain (handled via Gradle wrapper)
- Node.js ≥ 18 for JS bundling
- Android SDK (API 34) if targeting Android
- Vulkan drivers or WebGPU-enabled browser depending on your platform

### Build & Test

```bash
# Clone
git clone https://github.com/materia-engine/materia.git
cd materia

# Full build (all targets + default checks)
./gradlew build

# Unit tests across targets
./gradlew test

# Code coverage verification
./gradlew koverVerify

# Android-specific lint + unit tests
./gradlew lintDebug testDebugUnitTest
```

### Common Dev Loops

```bash
# Generate WGSL → SPIR-V shaders (runs automatically when needed)
./gradlew compileShaders

# Run dependency audit
./gradlew dependencyCheckAnalyze

# Validate production readiness (telemetry + constitutional checks)
./gradlew validateProductionReadiness
```

---

## Examples

| Scenario                    | Command                                     | Description                                                                  |
|-----------------------------|---------------------------------------------|------------------------------------------------------------------------------|
| Triangle sanity check (JVM) | `./gradlew :examples:triangle:run`          | Minimal pipeline exercising shader compilation and swap chain                |
| Triangle in browser         | `./gradlew :examples:triangle:jsBrowserRun` | Launches webpack dev server with WebGPU bindings                             |
| Basic scene showcase        | `./gradlew :examples:basic-scene:runJvm`    | Orbit controls, lighting, shadows                                            |
| Embedding galaxy            | `./gradlew :examples:embedding-galaxy:run`  | 20k instanced points with clustering                                         |
| Force graph rerank          | `./gradlew :examples:force-graph:run`       | TF‑IDF vs semantic reranking visualisation                                   |
| Voxelcraft sandbox          | `./gradlew :examples:voxelcraft:runJvm`     | LWJGL-based voxel environment with Web build: `…:jsBrowserProductionWebpack` |

Each example directory contains additional notes and configuration details.

---

## Modules

| Directory                    | Purpose                                                     |
|------------------------------|-------------------------------------------------------------|
| `materia-engine`             | Core scene graph, materials, geometry, animation, telemetry |
| `materia-gpu`                | GPU abstractions, WebGPU/Vulkan backends, shader management |
| `materia-gpu-android-native` | Android-native Vulkan glue, validation layers               |
| `materia-examples-common`    | Shared assets and utilities for example projects            |
| `materia-loader`             | Asset loader implementations (GLTF, OBJ, FBX, Collada)      |
| `materia-postprocessing`     | Post-processing passes and compositor                       |
| `materia-validation`         | Production readiness tooling, CLI, and report generators    |
| `tools/*`                    | Auxiliary utilities (editor, profiler, testing harnesses)   |

Additional specs live under `specs/`, while in-depth documentation is located in `docs/`.

---

## Quality Gates

| Check                | Command                                 | Purpose                                                                |
|----------------------|-----------------------------------------|------------------------------------------------------------------------|
| Build + unit tests   | `./gradlew build`                       | Compiles all targets and runs default test suites                      |
| Coverage             | `./gradlew koverHtmlReport`             | Generates HTML coverage report (`build/reports/kover/html/index.html`) |
| Lint                 | `./gradlew lintDebug`                   | Android lint with API-level compliance fixes                           |
| Dependency audit     | `./gradlew dependencyCheckAnalyze`      | OWASP dependency analysis                                              |
| Production readiness | `./gradlew validateProductionReadiness` | Aggregated performance + compliance validation                         |

CI pipelines should run the full sequence above before merges or releases.

---

## Documentation & Roadmap

- [`docs/`](docs/) – reference guides, performance notes, profiling walkthroughs.
- [`docs/MVP_STATUS.md`](docs/MVP_STATUS.md) – feature-by-feature status tracker.
- [`mvp-plan.md`](mvp-plan.md) & [`implementation-plan.md`](implementation-plan.md) – roadmap
  checkpoints.
- [`docs/private/`](docs/private/) – internal reports, release summaries, and the library logo.

---

## Contributing

Contributions are welcome! To get started:

1. Fork the repository and create a feature branch.
2. Implement your change with tests and KDoc updates as needed.
3. Run the quality gates (`build`, `test`, `lintDebug`, `koverVerify`,
   `validateProductionReadiness`).
4. Open a pull request describing scope, validation steps, and relevant issues.

Please read `CONTRIBUTING.md` and `CODE_OF_CONDUCT.md` before submitting changes.

---

## License

Materia is licensed under the [Apache License 2.0](LICENSE). Feel free to use, modify, and
distribute according to the license terms.

---

<p align="center"><sub>Built with ❤️ by the Materia team. Reach out via issues or discussions if you need help getting started.</sub></p>
