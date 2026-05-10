# Renderer API Reference

The renderer module provides unified rendering APIs across WebGPU (JavaScript), Vulkan (JVM), and the current Apple beta runtime paths.

## Overview

```kotlin
import io.materia.engine.renderer.*
```

The Materia rendering system aims for a **"Write Once, Run Everywhere"** architecture similar to Three.js. Today, the core cross-platform path is solid on JS and JVM, while Apple support is split between the shared engine path used by `examples:triangle` and wrapper-app paths for some legacy `RendererFactory` examples such as `volume-texture`.

---

## WebGPURenderer (Unified Renderer)

The primary renderer class that works on both JS (WebGPU) and JVM (Vulkan) platforms.

### Constructor

```kotlin
class WebGPURenderer(
    config: WebGPURendererConfig = WebGPURendererConfig()
)
```

### Configuration

```kotlin
data class WebGPURendererConfig(
    val depthTest: Boolean = true,
    val clearColor: Color = Color(0f, 0f, 0f),
    val clearAlpha: Float = 1f,
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val autoResize: Boolean = true,
    val preferredFormat: GpuTextureFormat? = null,
    val antialias: Int = 1,  // MSAA samples (1 = disabled)
    val debug: Boolean = false
)
```

### Initialization

The renderer requires async initialization:

```kotlin
// Create renderer
val renderer = WebGPURenderer(
    WebGPURendererConfig(
        depthTest = true,
        clearColor = Color(0.1f, 0.1f, 0.15f),
        antialias = 4
    )
)

// Initialize with render surface
renderer.initialize(renderSurface)

// Resize when window changes
renderer.setSize(width, height)
```

### Rendering

```kotlin
// Main render loop
renderLoop.start { deltaTime ->
    // Update scene
    scene.update(deltaTime)
    
    // Render
    renderer.render(scene, camera)
}

// Cleanup
renderer.dispose()
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `stats` | `WebGPURenderStats` | Rendering statistics |
| `isDisposed` | `Boolean` | Whether renderer has been disposed |

### Render Statistics

```kotlin
data class WebGPURenderStats(
    var frameCount: Long = 0,
    var drawCalls: Int = 0,
    var triangles: Int = 0,
    var textureBinds: Int = 0,
    var pipelineSwitches: Int = 0,
    var frameTime: Float = 0f
)

// Usage
println("Draw calls: ${renderer.stats.drawCalls}")
println("Triangles: ${renderer.stats.triangles}")
```

### Resource Management

The renderer implements the `Disposable` interface for proper resource cleanup:

```kotlin
// Always dispose when done
renderer.dispose()

// Or use the `use` extension
renderer.use { r ->
    // rendering code
}
```

---

## Legacy Renderers

For backward compatibility, the following legacy renderers are still available:

### EngineRenderer

The lower-level engine renderer used by examples:

```kotlin
val renderer = RendererFactory.createEngineRenderer(
    surface = renderSurface,
    config = RendererConfig(...),
    options = EngineRendererOptions(...)
).getOrThrow()
```

---

## Platform-Specific Setup

### JavaScript (WebGPU)

```kotlin
// Get canvas element
val canvas = document.getElementById("canvas") as HTMLCanvasElement

// Create render surface from canvas
val surface = CanvasRenderSurface(canvas)

// Initialize renderer
val renderer = WebGPURenderer()
renderer.initialize(surface)
```

### JavaScript WebGL Fallback

When WebGPU is unavailable, the JS renderer can fall back to `WebGLRenderer`. The fallback render
path is designed for steady-frame rendering without synchronous GPU readback. Calls such as
`readPixels`, `toDataURL`, and 2D-canvas `getImageData` should remain explicit screenshot,
validation, picking, or image-decode operations rather than work performed by every render frame.

The browser test suite includes a guard for this behavior. Run it in an environment with Chrome
available:

```bash
CHROME_BIN=/path/to/chrome ./gradlew :jsBrowserTest -PenableJsBrowserTests=true
```

### JVM (Vulkan via LWJGL)

```kotlin
// Create GLFW window
val window = KmpWindow(WindowConfig(
    title = "My App",
    width = 1280,
    height = 720
))

// Create renderer with window surface
val renderer = WebGPURenderer()
renderer.initialize(window.getRenderSurface())
```

---

### Apple Runtime Model

Apple support is currently split by API family:

- `examples:triangle` uses the shared Apple engine/gpu path. On iOS, the app owns an `MTKView` or `CAMetalLayer` and bridges it into Materia. On macOS, `./gradlew :examples:triangle:runMacos` launches the native executable.
- `examples:volume-texture` still uses the older root `RendererFactory` API. The current native Apple implementation for that API does not yet draw scene content, so the working Apple path is `examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj`, which embeds the generated JS/WebGL bundle inside a native iOS or Mac Catalyst shell.

#### iOS Host Shape

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
        controller = TriangleIosHostKt.createDefaultTriangleIosController(metalView: metalView)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !hasStarted else { return }
        hasStarted = true
        controller?.start(onReady: { print($0) }, onError: { print($0) })
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        controller?.resizeToDrawableSize()
    }
}
```

Use `open examples/triangle-ios-app/MateriaTriangleDemo.xcodeproj` for the native iOS host app and `open examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj` for the current Apple `Data3DTexture` example path.

---

## RenderLoop

Platform-agnostic animation loop:

```kotlin
// Configure loop
val config = RenderLoopConfig(
    targetFps = 60,
    fixedTimeStep = false
)

// Create loop
val renderLoop = RenderLoop(config)

// Start rendering
renderLoop.start { deltaTime ->
    scene.update(deltaTime)
    renderer.render(scene, camera)
}

// Stop when done
renderLoop.stop()
```

### JS Implementation
Uses `requestAnimationFrame` for smooth browser rendering.

### JVM Implementation
Uses coroutine-based blocking loop with precise timing.

---

## Disposable Interface

All GPU resources implement the `Disposable` interface:

```kotlin
interface Disposable {
    val isDisposed: Boolean
    fun dispose()
}

// Extension for try-with-resources pattern
inline fun <T : Disposable, R> T.use(block: (T) -> R): R

// Check resource state
fun Disposable.checkNotDisposed(resourceName: String)
```

### DisposableContainer

Manages multiple disposable resources:

```kotlin
val container = DisposableContainer()

// Add resources (disposed in reverse order)
container += texture
container += buffer
container += pipeline

// Dispose all at once
container.dispose()
```

---

## Tone Mapping

```kotlin
enum class ToneMapping {
    NO,             // No tone mapping
    LINEAR,         // Linear mapping
    REINHARD,       // Reinhard operator
    CINEON,         // Cineon film curve
    ACES_FILMIC,    // ACES filmic (default)
    AGXFILMIC       // AgX filmic
}
```

---

## Shadow Map

```kotlin
class ShadowMap {
    var enabled: Boolean = false
    var type: ShadowMapType = ShadowMapType.PCF_SOFT
    var autoUpdate: Boolean = true
    var needsUpdate: Boolean = false
}

enum class ShadowMapType {
    BASIC,      // No filtering
    PCF,        // Percentage-closer filtering
    PCF_SOFT,   // Soft PCF (default)
    VSM         // Variance shadow mapping
}
```

---

## Performance Tips

### Reduce Draw Calls

```kotlin
// Use instancing for many similar objects
val instancedMesh = InstancedMesh(geometry, material, 1000)
for (i in 0 until 1000) {
    val matrix = Matrix4()
    matrix.setPosition(randomPosition())
    instancedMesh.setMatrixAt(i, matrix)
}
scene.add(instancedMesh)
```

### Reduce State Changes

```kotlin
// Sort by material (automatic with unified renderer)
// Use texture atlases for fewer binds
```

### Optimize Shadows

```kotlin
// Use appropriate shadow map sizes
mainLight.shadow.mapSize.set(1024f, 1024f)

// Limit shadow casters
distantObject.castShadow = false
```

---

## See Also

- [Camera API](camera.md) - Cameras for rendering
- [Material API](material.md) - Materials and shaders
- [Core API](core.md) - Math and utilities
- [Scene API](scene.md) - Scene graph and objects
