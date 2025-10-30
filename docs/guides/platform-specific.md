# Platform-Specific Guide

This guide covers platform-specific considerations when using Materia across different targets.

## Platform Overview

| Platform                     | Rendering Backend           | Status | Min Version     |
|------------------------------|-----------------------------|--------|-----------------|
| JVM                          | Vulkan via LWJGL            | Stable | Java 17+        |
| JavaScript                   | WebGPU with WebGL2 fallback | Stable | Modern browsers |
| Android                      | Native Vulkan API           | Stable | API 24+         |
| iOS                          | MoltenVK (Vulkan-to-Metal)  | Beta   | iOS 14+         |
| Native (Linux/Windows/macOS) | Direct Vulkan               | Beta   | OS-dependent    |

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
                implementation("io.materia:materia-jvm:1.0.0")
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
                implementation("io.materia:materia-js:1.0.0")
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
                implementation("io.materia:materia-android:1.0.0")
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

### Setup

```kotlin
// build.gradle.kts
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.materia:materia-ios:1.0.0")
            }
        }

        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}
```

### Swift Integration

```swift
import UIKit
import Materia

class GameViewController: UIViewController {
    var renderer: VulkanRenderer!
    var displayLink: CADisplayLink!

    override func viewDidLoad() {
        super.viewDidLoad()

        // Create Metal view
        let metalView = MTKView(frame: view.bounds)
        metalView.device = MTLCreateSystemDefaultDevice()
        view.addSubview(metalView)

        // Initialize Materia renderer (uses MoltenVK)
        renderer = VulkanRenderer(metalView: metalView)

        // Start render loop
        displayLink = CADisplayLink(target: self, selector: #selector(renderLoop))
        displayLink.add(to: .main, forMode: .default)
    }

    @objc func renderLoop() {
        renderer.render(scene: scene, camera: camera)
    }

    deinit {
        displayLink.invalidate()
        renderer.dispose()
    }
}
```

### Performance Considerations

- **MoltenVK**: Vulkan-to-Metal translation layer
- **Memory**: iOS has strict memory limits
- **Power Efficiency**: A-series GPUs are power-efficient
- **App Store**: Ensure compliance with guidelines

## Native (Linux/Windows/macOS)

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
                implementation("io.materia:materia-native:1.0.0")
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
