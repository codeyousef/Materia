Materia — Development Guidelines (Advanced)

Scope and audience

- This document records project-specific details that help advanced contributors build, test, and
  extend Materia
  consistently across platforms.
- It is intentionally focused on Materia’s actual Gradle/KMP wiring, targets, known constraints on
  Windows, and how to
  work with the repository’s tests and tools.

1) Build and configuration

Tooling and versions

- Gradle: Wrapper-managed (8.11.1 as of this snapshot). Use the provided wrapper; do not install a global Gradle.
    - Windows usage: .\gradlew.bat <task>
- Kotlin: Multiplatform plugin with K2; versions are defined in gradle/libs.versions.toml (managed via version
  catalogs).
- Java: JVM toolchain targeting Java 11 for the JVM target.

Targets configured in build.gradle.kts

- Enabled on Windows in this repo:
    - jvm: primary desktop target (LWJGL/Vulkan/GLFW). Uses org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11.
    - js(IR): configured for browser and Node, but testTask for JS is disabled by default (see Testing section).
    - mingwX64: Windows native target; compiles on Windows.
    - linuxX64: Native target; compiles (cross) but not runnable on Windows.
- Disabled on Windows (commented out): android, ios, macos. These are intentionally disabled to keep CI and local
  Windows developer flow lean.

Key dependency choices

- LWJGL natives (JVM): The build dynamically selects the correct LWJGL natives classifier at runtime based on os.name (
  natives-windows, natives-linux, natives-macos). Vulkan itself is header-only in LWJGL; you still need system Vulkan
  available for running real renderers on JVM.
- Common libraries: kotlinx.coroutines, kotlinx.serialization, kotlinx.datetime, kotlinx.collections.immutable, okio,
  kotlinx-io-core, kotlin-reflect.

Doc and coverage tooling

- Dokka V2: org.jetbrains.dokka is applied to the root project and subprojects. Main task: dokkaHtml.
- Kover: Code coverage integrated (org.jetbrains.kotlinx.kover). Main tasks: koverHtmlReport, koverXmlReport,
  koverVerify.

Publishing

- The root applies maven-publish. For local testing you can publish artifacts to your local Maven repository:
    - .\gradlew.bat publishToMavenLocal

Useful commands (Windows)

- List tasks: .\gradlew.bat tasks
- Build (all main targets): .\gradlew.bat assemble
- Build JVM only: .\gradlew.bat jvmJar
- Dokka docs: .\gradlew.bat dokkaHtml
- Coverage (see caveat below about tests): .\gradlew.bat koverHtmlReport

Notes about Node/JS

- JS is configured with IR backend and adds npm("@webgpu/types", "0.1.40"). If you work on the JS/WebGPU renderer,
  ensure your Node/Yarn/npm is set up; however, JS test tasks are explicitly disabled in Gradle for both browser and
  node.

2) Testing

Current repository state (as observed on Windows)

- Running JVM tests in the root module currently fails at compile time due to incomplete contract tests and example
  modules’ tests. Examples of compile-time errors include unresolved types (e.g., RenderTarget, StorageInfo) and
  constructor parameter mismatches in contract tests.
- JS test tasks are disabled by default in build.gradle.kts (browser.testTask.enabled = false, nodejs.testTask.enabled =
  false).
- Because the test sources in src/commonTest (and in :examples subprojects) do not currently compile for JVM, a straight
  .\gradlew.bat jvmTest or .\gradlew.bat test will fail until those test sources are corrected.

Implications for contributors

- If you are adding new tests while the existing test suite is in flux, prefer to scope your work to avoid compiling the
  currently failing test sources. Two practical approaches:
    1) Work in a branch that temporarily comments or fixes the failing test classes (recommended for medium/long-lived
       streams).
    2) Create a dedicated, temporary test module or task to validate small units locally without building the whole
       suite (see “Establishing a quick local test loop” below).

How to run available tasks safely today

- Build main sources (no tests):
    - .\gradlew.bat assemble
- Dokka documentation (no tests):
    - .\gradlew.bat dokkaHtml
- Coverage tasks depend on tests; expect failure until tests compile. You can still run:
    - .\gradlew.bat koverHtmlReport
      But it will be empty or fail if tests cannot execute.

Establishing a quick local test loop (recommended workflow until suite stabilizes)

- The multiplatform Gradle plugin compiles all sources in the jvmTest source set, which currently includes failing
  tests. A minimally invasive pattern while working locally:
    - Create a short-lived branch.
    - Temporarily annotate broken test classes with @Ignore (kotlin.test.Ignore) or move them out of src/commonTest
      while you iterate. Keep a commit that restores them before PR.
    - Add your focused tests to src/jvmTest (or src/commonTest if they are pure-common and you ensure they compile) and
      run:
        - .\gradlew.bat :jvmTest --tests "<your.package>.<YourTestClass>"
    - Once done, restore the original failing sources and keep only your added tests in the PR.
- If you prefer a dedicated quick test harness, add a temporary JVM-only test source set and task (jvmQuickTest) in a
  local branch. Example sketch:
    - In build.gradle.kts, register a custom Test task bound to an ad-hoc source set (e.g., src/jvmQuickTest/kotlin)
      that depends only on kotlin("test"). This avoids compiling the main jvmTest sources. Remove before committing to
      main unless the team adopts it.

Adding new tests

- Common tests: Place in src/commonTest/kotlin using kotlin.test APIs. These compile to platform-specific tests; ensure
  no platform-specific APIs leak into common tests.
- JVM-specific tests: Place in src/jvmTest/kotlin for code that relies on LWJGL/Vulkan or JVM-only behavior.
- JS-specific tests: Place in src/jsTest/kotlin, but remember JS test tasks are disabled by default; if you need to run
  them, re-enable testTask in build.gradle.kts under js { browser { testTask { enabled = true } } nodejs { testTask {
  enabled = true } } }.
- Native (mingw/linux) tests: Supported at the source-set level, but running them on Windows is non-trivial. Prefer
  common or JVM tests for Windows CI/dev.

Running tests (when the suite compiles)

- Run all JVM tests for the root module only:
    - .\gradlew.bat :jvmTest
- Run a specific test class on JVM:
    - .\gradlew.bat :jvmTest --tests "io.materia.core.math.Box3Test"
- Run tests in a specific subproject (if fixed):
    - .\gradlew.bat :examples:voxelcraft:jvmTest
- Generate coverage after successful tests:
    - .\gradlew.bat koverHtmlReport

Demonstration: creating a simple test locally

- Because the current repository JVM test sources do not compile end-to-end, the following is a local-only demonstration
  pattern you can use while developing (do this in a temporary branch and do not commit the changes unless you also fix
  the broken tests):
    1) Create src/jvmTest/kotlin/io/materia/demo/DemoGuidelineTest.kt with:
        - package io.materia.demo
          import kotlin.test.Test
          import kotlin.test.assertEquals
          class DemoGuidelineTest { @Test fun adds() { assertEquals(4, 2 + 2) } }
    2) Temporarily disable or move out the failing test sources under src/commonTest and examples/*/src/commonTest to
       allow jvmTest to compile.
    3) Run: .\gradlew.bat :jvmTest --tests "io.materia.demo.DemoGuidelineTest"
    4) Revert your temporary moves/changes before opening a PR.
- If you prefer not to touch existing sources, create a temporary custom task (jvmQuickTest) and directory (
  src/jvmQuickTest/kotlin) as noted above; wire it to kotlin("test") only, run it locally, then drop it before commit.

3) Additional development information

Source set organization and patterns

- commonMain contains cross-platform APIs (scene graph, math, camera abstractions). Avoid using platform globals (e.g.,
  window, console) directly in commonMain; prefer expect/actual facades if you need logging or platform access.
- Platform source sets (jvmMain, jsMain, nativeMain, mingwX64Main, linuxX64Main) provide actuals and integrations (e.g.,
  LWJGL on JVM, WebGPU types on JS). Keep platform-specific dependencies out of commonMain.

Performance and profiling

- The project aims for 60 FPS with 100k+ triangles; pay attention to allocations in hot paths. MathObjectPools are used
  in transformations to minimize allocations—re-use them rather than allocating new vectors/matrices in loops.
- Profiling hooks and examples live under tools/profiling and docs/profiling. Use these when optimizing render or scene
  graph updates.

Rendering backends

- WebGPU is the preferred web backend (with WebGL2 fallback planned). On JVM, Vulkan via LWJGL is the focus. Make sure
  you test feature availability and fail gracefully.

Examples and tools

- Examples live under :examples (basic-scene, voxelcraft). On Windows, some examples may require additional native
  dependencies (Vulkan SDK, drivers). Use run-examples.ps1/.bat scripts at the root to launch examples where available.
- Tools live under tools/* (docs, editor, profiler, tests, validation, etc.). The root project applies dokka to tools
  subprojects too (group io.materia.tools; version = root version).

Code style and conventions

- Kotlin code style: Follow JetBrains defaults with explicit types in public APIs.
- Naming: Three.js-inspired API; mirror familiar names (Object3D, Camera) while adopting idiomatic Kotlin.
- Tests: Prefer kotlin.test APIs (assertEquals, assertTrue, etc.). Keep tests deterministic; no sleeps for timing unless
  guarded and justified.
- Logging in common code: Use an expect/actual abstraction if you need logging from commonMain. Do not reference JS
  globals directly in common source sets.

Known issues (as of 2025-10-09)

- JVM unit tests fail to compile due to incomplete contract tests in src/commonTest and example subprojects. Before
  enabling CI gating on tests, stabilize or temporarily ignore those classes.
- JS test tasks are disabled; re-enable if/when web tests are maintained.

Checklist for contributors

- Windows dev box: Ensure Java 11, Vulkan runtime (for JVM renderer-related work), and Node (for JS renderer work if you
  enable JS tests).
- Use the Gradle wrapper; do not bump Gradle without testing all targets.
- Update docs via dokkaHtml and keep API reference in docs/ in sync if publishing.
- If you introduce platform interactions in commonMain, add an expect/actual facade rather than referencing platform
  globals.

Contact/triage

- If you encounter build or test issues, capture the exact Gradle task, OS, and the error excerpt. Prioritize fixing
  test compilation errors in commonTest to re-enable the standard jvmTest task.