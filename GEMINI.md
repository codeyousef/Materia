# GEMINI.md

## Project Overview

Materia is a production-ready, Kotlin Multiplatform 3D graphics library designed to bring the power
and familiarity of Three.js to native platforms. It enables developers to write 3D applications once
and deploy them across a wide range of platforms, including web, desktop, and mobile.

**Key Technologies:**

*   **Kotlin Multiplatform:** The core of the project, allowing for a single codebase to target multiple platforms.
*   **Graphics APIs:**
    *   **WebGPU:** The primary rendering backend for web, with WebGL2 as a fallback.
    *   **Vulkan:** Used for rendering on JVM, Linux, and Windows platforms.
    *   **MoltenVK:** Enables Vulkan support on macOS and iOS.
*   **Physics Engines:**
    *   **Rapier:** Integrated for physics on the web platform.
    *   **Bullet:** Used for physics on native platforms.
*   **Build Tool:** Gradle is used for building the project and managing dependencies.

**Architecture:**

Materia features a modular architecture that promotes a clean separation of concerns. The main
modules include:

* `materia-core`: Contains core mathematical primitives and utilities.
* `materia-renderer`: An abstraction layer for the underlying graphics APIs (WebGPU/Vulkan).
* `materia-scene`: Implements the scene graph system.
* `materia-geometry`: Provides various geometry classes and primitives.
* `materia-material`: The material system and shaders.
* `materia-animation`: Handles animation clips and mixers.
* `materia-loader`: Manages asset loading (GLTF, OBJ, FBX).
* `materia-controls`: Provides camera controls and user interaction.
* `materia-physics`: Integrates with the physics engines.
* `materia-xr`: For VR/AR support.
* `materia-postprocess`: Handles post-processing effects.

The project leverages Kotlin's `expect`/`actual` pattern to provide platform-specific implementations while maintaining a common API.

## Building and Running

The following Gradle commands are essential for working with the Materia project:

**Building the Project:**

*   Build all targets:
    ```bash
    ./gradlew build
    ```
*   Build a specific target (e.g., JVM):
    ```bash
    ./gradlew compileKotlinJvm
    ```

**Running Tests:**

*   Run all tests:
    ```bash
    ./gradlew test
    ```
*   Run platform-specific tests (e.g., JVM):
    ```bash
    ./gradlew jvmTest
    ```

**Running Examples:**

*   Run the basic scene example on the desktop (JVM):
    ```bash
    ./gradlew :examples:basic-scene:runJvm
    ```
*   Run the basic scene example in a web browser:
    ```bash
    ./gradlew :examples:basic-scene:runJs
    ```

## Development Conventions

While a formal `CONTRIBUTING.md` file was not found, the `README.md` provides a clear set of development standards and a contribution workflow.

**Development Workflow:**

1.  Fork the repository.
2.  Create a feature branch (`git checkout -b feature/amazing-feature`).
3.  Write tests for your changes.
4.  Commit your changes (`git commit -m 'Add amazing feature'`).
5.  Push to the branch (`git push origin feature/amazing-feature`).
6.  Open a Pull Request.

**Development Standards:**

*   **Type Safety:** Avoid using the `!!` operator or unsafe casts in production code.
*   **Performance:** Employ object pooling, dirty flagging, and GPU optimization techniques.
*   **Testing:** Write comprehensive unit, integration, and visual regression tests.
*   **Documentation:** Maintain complete KDoc coverage and provide interactive examples.
*   **Security:** Regularly audit dependencies and scan for vulnerabilities.
