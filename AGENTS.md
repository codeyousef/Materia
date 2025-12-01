# Repository Guidelines

## Project Structure & Module Organization

Core multiplatform code lives under `src/commonMain`, with platform-specific `actual`
implementations in `src/jvmMain`, `src/jsMain`, and `src/nativeMain`. Feature modules such as
`materia-loader`, `materia-helpers`, and `materia-validation` encapsulate loaders, utilities, and
the validation CLI. Examples for demos reside in `examples/*`, reference docs in `docs/`, and audit
specifications under `specs/`. System tests live in `ests/` split into contract, integration,
performance, and visual suites; mirror that layout when adding coverage.

## Build, Test, and Development Commands

Run `./gradlew build` before pushes to compile all targets and execute default checks. Use
`./gradlew test` for the multiplatform unit suites or scope with module selectors, e.g.,
`./gradlew :materia-validation:test`. Generate coverage with `./gradlew koverVerify`; inspect HTML
reports via `./gradlew koverHtmlReport` and open `build/reports/kover/html/index.html`. List
runnable demos using `./gradlew listExamples`, launch the baseline scene with
`./gradlew :examples:basic-scene:runJvm`, and produce API docs via `./gradlew dokkaHtml`.

## Coding Style & Naming Conventions
Follow official Kotlin style with four-space indentation, trailing commas where they clarify diffs, and explicit visibility. Public types use UpperCamelCase, members lowerCamelCase, Gradle tasks lowerCamelCase, and module directories hyphenated. Avoid `!!` and unchecked casts. Run the IDE formatter or ktlint; CI expects reports under `build/reports/ktlint/`.

## Testing Guidelines
Author unit tests in `src/*Test` using `kotlin.test`; JVM-specific cases can leverage JUnit 5 and MockK. Place integration and performance scenarios in `ests/integration` with descriptive `*Test.kt` filenames. Maintain at least 50% coverage via Kover while working toward the 95% target.

## Commit & Pull Request Guidelines
Write commits with short imperative subjects (e.g., "Add validation wiring"). PRs should summarize scope, enumerate validation steps (`./gradlew build`, key demos), and link relevant roadmap items or issues. Attach coverage artifacts for core logic and screenshots for visual work, and request CI validation for cross-platform changes.

## Security & Compliance Checks

Run `./gradlew dependencyCheckAnalyze` during security reviews. Validate release readiness through
`./gradlew validateProductionReadiness`, reviewing reports in
`materia-validation/build/validation-reports/`.

## Shader Management & Rendering Architecture

### Dual Shader Source
The project uses a split strategy for shaders:
*   **WebGPU (JS):** Uses WGSL source strings defined directly in Kotlin code (e.g., `UnlitPipelineFactory.kt`). Edits to these strings take effect immediately on the JS target.
*   **Vulkan (JVM):** Uses pre-compiled SPIR-V binaries located in `src/jvmMain/resources/shaders/`. The JVM backend **ignores** the WGSL strings in the Kotlin code.
*   **Implication:** When modifying shaders, you must update the WGSL string for JS **AND** recompile the shader to SPIR-V for JVM. If the JVM output looks "stale" or "purple" (debug color), it means the SPIR-V binary is out of sync with the code.

### Rendering Pipeline
*   **EngineRenderer:** The high-level entry point for the engine-focused render loop.
*   **SceneRenderer:** Handles the actual draw command generation.
*   **Pipeline Factories:** (e.g., `UnlitPipelineFactory`) Configure the GPU state and load shaders.
*   **Topology:** WebGPU has strict topology requirements. `POINT_LIST` is often not supported or renders invisible points. Prefer using a **Quad Fallback** (rendering points as 6-vertex billboards) for cross-platform consistency.

