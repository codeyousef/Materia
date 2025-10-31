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

| Example          | JVM                                           | Browser / JS                                 | Android                                           |
|------------------|-----------------------------------------------|----------------------------------------------|---------------------------------------------------|
| Triangle         | `./gradlew :examples:triangle:runJvm`         | `./gradlew :examples:triangle:runJs`         | `./gradlew :examples:triangle:runAndroid`         |
| Embedding Galaxy | `./gradlew :examples:embedding-galaxy:runJvm` | `./gradlew :examples:embedding-galaxy:runJs` | `./gradlew :examples:embedding-galaxy:runAndroid` |
| Force Graph      | `./gradlew :examples:force-graph:runJvm`      | `./gradlew :examples:force-graph:runJs`      | `./gradlew :examples:force-graph:runAndroid`      |
| VoxelCraft       | `./gradlew :examples:voxelcraft:runJvm`       | `./gradlew :examples:voxelcraft:runJs`       | `./gradlew :examples:voxelcraft:runAndroid`       |

- All run targets live under the Gradle `run` task group for quick discovery (
  `./gradlew :examples:triangle:tasks --group run`).
- JVM runs default to `stackSize=8192` to keep Vulkan happy on software adapters; VoxelCraft caps
  itself to a short smoke loop (`VOXELCRAFT_FRAME_BUDGET`, default 60 frames) so the CLI run
  completes without manual interaction.
- Android tasks install and launch via `adb`; connect a device or emulator first.
- Browser runs depend on a WebGPU-capable browser (Chrome Canary, Edge Dev, or Firefox Nightly with
  flags enabled).

---

## Modules

| Directory                    | Purpose                                                      |
|------------------------------|--------------------------------------------------------------|
| `materia-engine`             | Core scene graph, materials, animation, telemetry            |
| `materia-gpu`                | WebGPU/Vulkan abstraction, swapchains, shader compilation    |
| `materia-gpu-android-native` | Android Vulkan bootstrap (surface + validation glue)         |
| `materia-examples-common`    | Shared assets, camera rigs, and debugging overlays           |
| `materia-validation`         | CLI for readiness checks, lint aggregation, dependency scans |
| `examples/*`                 | Multiplatform example scenes (JVM, JS, Android wrappers)     |

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
