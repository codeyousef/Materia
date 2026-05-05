# Platform-Specific Guide

This guide covers platform-specific considerations when using Materia across different targets.

## Platform Overview

| Platform              | Rendering Backend                           | Status | Min Version     |
|-----------------------|---------------------------------------------|--------|-----------------|
| JVM                   | Vulkan via LWJGL                            | Stable | Java 17+        |
| JavaScript            | WebGPU with WebGL2 fallback                 | Stable | Modern browsers |
| Android               | Native Vulkan API                           | Stable | API 24+         |
| iOS                   | MoltenVK via host-owned `MTKView`/`CAMetalLayer` | Beta   | Xcode 15+       |
| macOS Apple Native    | MoltenVK via shared engine/gpu path         | Beta   | Xcode 15+       |
| Native (Linux/Windows)| Direct Vulkan                               | Beta   | OS-dependent    |

## JVM (Desktop)

### Setup

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "1.9.21"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.materia:materia-jvm:0.4.0.1")
                implementation("org.lwjgl:lwjgl:3.3.3")
                implementation("org.lwjgl:lwjgl-vulkan:3.3.3")

                // Platform-specific natives
                val lwjglNatives = when (osName) {
                    "Linux" -> "natives-linux"
                    "Mac OS X" -> "natives-macos"
                    "Windows" -> "natives-windows"
                    else -> throw GradleException("Unsupported OS: $osName")
                }

                runtimeOnly("org.lwjgl:lwjgl:3.3.3:$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-vulkan:3.3.3:$lwjglNatives")
            }
        }
    }
}
```

### Renderer Initialization

```kotlin
import io.materia.renderer.VulkanRenderer
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack

fun main() {
    // Initialize GLFW
    if (!GLFW.glfwInit()) {
        throw RuntimeException("Failed to initialize GLFW")
    }

    // Create window (no OpenGL context)
    GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API)
    val window = GLFW.glfwCreateWindow(800, 600, "Materia App", 0, 0)

    // Create Vulkan renderer
    val renderer = VulkanRenderer(window)

    // Main loop
    while (!GLFW.glfwWindowShouldClose(window)) {
        GLFW.glfwPollEvents()
        renderer.render(scene, camera)
    }

    // Cleanup
    renderer.dispose()
    GLFW.glfwDestroyWindow(window)
    GLFW.glfwTerminate()
}
```

### Performance Considerations

- **Validation Layers**: Enable in debug builds for Vulkan debugging
- **Memory Management**: Use direct ByteBuffers for GPU data
- **Threading**: Command buffers can be recorded in parallel

```kotlin
// Enable validation in debug
val renderer = VulkanRenderer(window).apply {
    if (DEBUG) {
        enableValidationLayers = true
    }
}
```

## JavaScript (Web)

### Setup

```kotlin
// build.gradle.kts
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("io.materia:materia-js:0.4.0.1")
            }
        }
    }
}
```

### HTML Setup

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Materia WebGPU App</title>
    <style>
        body {
            margin: 0;
            overflow: hidden;
        }
        canvas {
            display: block;
            width: 100vw;
            height: 100vh;
        }
    </style>
</head>
<body>
    <canvas id="renderCanvas"></canvas>
    <script src="materia-app.js"></script>
</body>
</html>
```

### Renderer Initialization

```kotlin
import io.materia.renderer.WebGPURenderer
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

fun main() {
    val canvas = document.getElementById("renderCanvas") as HTMLCanvasElement

    // Create WebGPU renderer (falls back to WebGL2 if not available)
    val renderer = WebGPURenderer(canvas)

    // Handle window resize
    window.addEventListener("resize") {
        val width = window.innerWidth
        val height = window.innerHeight
        renderer.setSize(width, height)
        camera.aspect = width.toFloat() / height.toFloat()
        camera.updateProjectionMatrix()
    }

    // Animation loop
    fun animate() {
        renderer.render(scene, camera)
        window.requestAnimationFrame { animate() }
    }
    animate()
}
```

### WebGPU Feature Detection

```kotlin
suspend fun initRenderer(canvas: HTMLCanvasElement): Renderer {
    return when {
        WebGPURenderer.isSupported() -> {
            console.log("Using WebGPU")
            WebGPURenderer(canvas)
        }
        WebGL2Renderer.isSupported() -> {
            console.log("WebGPU not available, falling back to WebGL2")
            WebGL2Renderer(canvas)
        }
        else -> {
            throw UnsupportedOperationException("No compatible renderer found")
        }
    }
}
```

### Asset Loading

```kotlin
// CORS considerations for texture loading
val textureLoader = TextureLoader().apply {
    crossOrigin = "anonymous"
}

textureLoader.load(
    url = "https://example.com/texture.jpg",
    onLoad = { texture ->
        material.map = texture
        material.needsUpdate = true
    },
    onError = { error ->
        console.error("Failed to load texture: $error")
    }
)
```

## Android

### Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("multiplatform")
}

android {
    namespace = "com.example.materia"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.materia"
        minSdk = 24
        targetSdk = 34
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation("io.materia:materia-android:0.4.0.1")
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.appcompat:appcompat:1.6.1")
            }
        }
    }
}
```

### AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Vulkan support -->
    <uses-feature
        android:name="android.hardware.vulkan.level"
        android:required="true"
        android:version="1" />

    <!-- OpenGL ES fallback -->
    <uses-feature
        android:glEsVersion="0x00030000"
        android:required="false" />

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### Activity Setup

```kotlin
import android.os.Bundle
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import io.materia.renderer.VulkanRenderer

class MainActivity : AppCompatActivity() {
    private lateinit var renderer: VulkanRenderer
    private lateinit var surfaceView: MateriaSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create surface view
        surfaceView = MateriaSurfaceView(this).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Initialize renderer with Android Surface
                    renderer = VulkanRenderer(holder.surface)
                    startRenderLoop()
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    renderer.setSize(width, height)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    stopRenderLoop()
                    renderer.dispose()
                }
            })
        }

        setContentView(surfaceView)
    }

    private fun startRenderLoop() {
        // Use Choreographer for vsync-aligned rendering
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                renderer.render(scene, camera)
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
    }
}
```

### Performance Considerations

- **Vulkan vs OpenGL ES**: Materia prefers Vulkan but falls back to OpenGL ES 3.0
- **Thermal Throttling**: Monitor device temperature
- **Battery Usage**: Implement adaptive quality settings
- **Screen Orientation**: Handle configuration changes

```kotlin
// Adaptive quality based on device capabilities
val renderer = VulkanRenderer(surface).apply {
    when (getDevicePerformanceTier()) {
        PerformanceTier.HIGH -> {
            shadowMap.enabled = true
            shadowMap.type = ShadowMapType.PCFSoftShadowMap
            antialiasing = AntialiasingType.MSAA_4X
        }
        PerformanceTier.MEDIUM -> {
            shadowMap.enabled = true
            shadowMap.type = ShadowMapType.BasicShadowMap
            antialiasing = AntialiasingType.FXAA
        }
        PerformanceTier.LOW -> {
            shadowMap.enabled = false
            antialiasing = AntialiasingType.NONE
        }
    }
}
```

## iOS

### Runtime Model

- Materia uses MoltenVK through the shared Apple `wgpu4k` path.
- iOS window creation is host-owned. Your app provides an `MTKView` or `CAMetalLayer`, and Materia builds the render surface around that host view.
- The repository includes a minimal sample host in `examples/triangle/src/iosMain/kotlin/io/materia/examples/triangle/TriangleIosHost.kt`.
- The `Data3DTexture` / `volume-texture` example still uses the older root `RendererFactory` API. On Apple, the current working path is `examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj`, which wraps the generated JS/WebGL bundle in a native iOS / Mac Catalyst shell.

### Setup

```kotlin
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}
```

### Swift Integration

The sample host is exported through the `MateriaTriangle` Apple framework. The integration shape is:

```swift
import MetalKit
import UIKit
import MateriaTriangle

final class TriangleViewController: UIViewController {
    private let metalView = MTKView(frame: .zero)
    private var controller: TriangleIosController?
    private var hasStarted = false

    override func viewDidLoad() {
        super.viewDidLoad()

        metalView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(metalView)

        NSLayoutConstraint.activate([
            metalView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            metalView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            metalView.topAnchor.constraint(equalTo: view.topAnchor),
            metalView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        controller = TriangleIosHostKt.createDefaultTriangleIosController(metalView: metalView)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)

        guard !hasStarted else { return }
        hasStarted = true

        controller?.start(
            onReady: { bootLog in
                print(bootLog)
            },
            onError: { message in
                print("MateriaTriangle iOS host error: \(message)")
            }
        )
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        controller?.resizeToDrawableSize()
    }

    deinit {
        controller?.stop()
    }
}
```

### Supported Launch Paths

- `./gradlew :examples:triangle:linkDebugFrameworkIosSimulatorArm64`
- `./gradlew :examples:triangle:linkReleaseFrameworkIosArm64`
- `./gradlew :examples:triangle:compileKotlinIosArm64`
- `./gradlew :examples:triangle:compileKotlinIosX64`
- `./gradlew :examples:triangle:compileKotlinIosSimulatorArm64`
- `open examples/triangle-ios-app/MateriaTriangleDemo.xcodeproj`
- `open examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj`
- There is no `runIos` Gradle task in this repository yet; the iOS runtime path is through a host app that embeds the generated Kotlin framework.

### Performance Considerations

- **MoltenVK**: Vulkan-to-Metal translation layer
- **Memory**: iOS has strict memory limits
- **Power Efficiency**: A-series GPUs are power-efficient
- **App Store**: Ensure compliance with guidelines

## Native (Linux/Windows/macOS)

### Current Launch Paths

- Desktop runnable examples are split by API family. `:examples:triangle:runMacos` uses a dedicated Kotlin/Native macOS executable backed by the shared Apple engine/gpu path.
- `examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj` can also run on `My Mac (Mac Catalyst)` for the current Apple `Data3DTexture` example path.
- JVM entry points such as `:examples:triangle:runJvm` and `:examples:volume-texture:runJvm` remain the primary desktop path for Vulkan-backed examples.
- The direct Vulkan setup below applies to Linux/Windows and non-Apple native experimentation. Apple runtime setup uses the shared MoltenVK path instead of raw `platform.vulkan` initialization.

### Setup

```kotlin
kotlin {
    linuxX64()
    mingwX64()  // Windows
    macosX64()
    macosArm64()

    sourceSets {
        val nativeMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.materia:materia-native:0.4.0.1")
            }
        }

        val linuxX64Main by getting { dependsOn(nativeMain) }
        val mingwX64Main by getting { dependsOn(nativeMain) }
        val macosX64Main by getting { dependsOn(nativeMain) }
        val macosArm64Main by getting { dependsOn(nativeMain) }
    }
}
```

### Platform-Specific Initialization

```kotlin
import io.materia.renderer.VulkanRenderer
import platform.vulkan.*

fun main() {
    // Initialize Vulkan directly
    val renderer = VulkanRenderer().apply {
        createInstance()
        createSurface()
        selectPhysicalDevice()
        createLogicalDevice()
    }

    // Main loop
    while (running) {
        processEvents()
        renderer.render(scene, camera)
    }

    renderer.dispose()
}
```

## Cross-Platform Considerations

### Asset Paths

```kotlin
expect object AssetLoader {
    fun loadTexture(path: String): ByteArray
}

// JVM implementation
actual object AssetLoader {
    actual fun loadTexture(path: String): ByteArray {
        return File(path).readBytes()
    }
}

// JS implementation
actual object AssetLoader {
    actual suspend fun loadTexture(path: String): ByteArray {
        val response = window.fetch(path).await()
        return response.arrayBuffer().await().toByteArray()
    }
}

// Android implementation
actual object AssetLoader {
    actual fun loadTexture(path: String): ByteArray {
        return context.assets.open(path).readBytes()
    }
}
```

### Input Handling

```kotlin
expect class InputManager {
    fun isKeyPressed(key: KeyCode): Boolean
    fun getMousePosition(): Vector2
}

// Platform-specific implementations handle input differently
```

### File System

```kotlin
expect object FileSystem {
    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
}
```

## Troubleshooting

### Vulkan Not Available

```kotlin
try {
    val renderer = VulkanRenderer()
} catch (e: VulkanNotSupportedException) {
    println("Vulkan not available: ${e.message}")
    // Fall back to OpenGL/WebGL
}
```

### WebGPU Not Available

```kotlin
if (!WebGPURenderer.isSupported()) {
    if (WebGL2Renderer.isSupported()) {
        // Use WebGL2 fallback
    } else {
        // Show error message
    }
}
```

### Performance Issues

```kotlin
// Enable performance monitoring
val profiler = PerformanceProfiler()
profiler.enable()

fun animate() {
    profiler.startFrame()

    // Render
    renderer.render(scene, camera)

    profiler.endFrame()

    if (profiler.averageFPS < 30) {
        // Reduce quality
        reduceQuality()
    }
}
```

## See Also

- [Getting Started Guide](getting-started.md)
- [Performance Optimization](../architecture/performance.md)
- [API Reference](../api-reference/README.md)
