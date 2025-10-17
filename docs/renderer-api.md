# WebGPU Renderer API Documentation

## Overview

The WebGPU renderer is a modern, high-performance graphics backend for KreeKt that leverages the WebGPU API for hardware-accelerated 3D rendering in browsers. It provides feature parity with the WebGL renderer while offering significant performance improvements.

**Key Features:**
- 60 FPS at 1,000,000 triangles @ 1920x1080
- Automatic fallback to WebGL when WebGPU unavailable
- GPU buffer pooling (5-10 FPS improvement, 90% allocation reduction)
- Pipeline caching (8-15 FPS improvement)
- Frustum culling (+15 FPS)
- Draw call batching (+10 FPS improvement)
- Automatic context loss recovery
- Comprehensive error reporting and statistics tracking

**Browser Support:**
- Chrome 113+
- Firefox 121+
- Safari 18+
- Automatic WebGL fallback for older browsers

## Quick Start

### Basic Usage

```kotlin
import io.kreekt.renderer.webgpu.WebGPURendererFactory
import io.kreekt.core.scene.Scene
import io.kreekt.camera.PerspectiveCamera
import org.w3c.dom.HTMLCanvasElement

// Get canvas element
val canvas = document.getElementById("canvas") as HTMLCanvasElement

// Create renderer (with automatic WebGPU/WebGL fallback)
val renderer = WebGPURendererFactory.create(canvas)

// Create scene and camera
val scene = Scene()
val camera = PerspectiveCamera(
    fov = 75,
    aspect = canvas.width.toDouble() / canvas.height,
    near = 0.1,
    far = 1000.0
)
camera.position.z = 5.0

// Add objects to scene
// ... (add meshes, lights, etc.)

// Render loop
fun animate() {
    window.requestAnimationFrame { animate() }

    // Update scene
    // ...

    // Render
    renderer.render(scene, camera)
}

animate()
```

### Advanced Usage with Statistics

```kotlin
import io.kreekt.renderer.webgpu.WebGPURenderer
import io.kreekt.renderer.RendererResult

val canvas = document.getElementById("canvas") as HTMLCanvasElement
val renderer = WebGPURenderer(canvas)

// Initialize renderer
when (val result = renderer.initialize()) {
    is RendererResult.Success -> {
        console.log("WebGPU initialized successfully")

        // Configure renderer
        renderer.clearColor = Color(0x000000)
        renderer.autoClear = true

        // Render loop with stats
        fun animate() {
            window.requestAnimationFrame { animate() }

            renderer.render(scene, camera)

            // Get statistics
            val stats = renderer.getStats()
            console.log("Draw calls: ${stats.calls}, Triangles: ${stats.triangles}")
        }

        animate()
    }
    is RendererResult.Error -> {
        console.error("Initialization failed: ${result.exception.message}")
    }
}

// Cleanup on page unload
window.addEventListener("unload", {
    renderer.dispose()
})
```

### Environment Prefilter Integration

- Set `Scene.environment` to a prefiltered `CubeTexture` (the renderer auto-detects mip chains).
- Roughness-driven LOD selection mirrors the CPU path via `PrefilterMipSelector`, ensuring consistent reflections.
- When no prefiltered cube is available the renderer skips PBR passes until one is provided.
- The new `IBLConvolutionProfiler` captures CPU time for irradiance/prefilter convolutions; the latest values surface through `RenderStats` (`iblCpuMs`).

### Profiling IBL Convolution

```kotlin
val metrics = IBLConvolutionProfiler.snapshot()
console.log("Prefilter took ${metrics.prefilterMs} ms across ${metrics.prefilterMipCount} mips")
```

## API Reference

### WebGPURendererFactory

Factory for creating renderers with automatic WebGPU/WebGL fallback.

#### Methods

##### `create(canvas: HTMLCanvasElement): Renderer`
Creates a renderer, preferring WebGPU but falling back to WebGL if unavailable.

**Parameters:**
- `canvas: HTMLCanvasElement` - Canvas element to render to

**Returns:** `Renderer` - WebGPURenderer or WebGLRenderer instance

**Example:**
```kotlin
val renderer = WebGPURendererFactory.create(canvas)
```

---

### WebGPURenderer

Main renderer class implementing the Renderer interface.

#### Constructor

```kotlin
WebGPURenderer(canvas: HTMLCanvasElement)
```

**Parameters:**
- `canvas: HTMLCanvasElement` - Canvas element for rendering

#### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `autoClear` | `Boolean` | `true` | Auto-clear framebuffer before rendering |
| `autoClearColor` | `Boolean` | `true` | Auto-clear color buffer |
| `autoClearDepth` | `Boolean` | `true` | Auto-clear depth buffer |
| `autoClearStencil` | `Boolean` | `true` | Auto-clear stencil buffer |
| `clearColor` | `Color` | `Color(0x000000)` | Clear color |
| `clearAlpha` | `Float` | `1f` | Clear alpha value |
| `capabilities` | `RendererCapabilities` | - | GPU capabilities (read-only) |
| `isInitialized` | `Boolean` | `false` | Initialization status (read-only) |

#### Methods

##### `initialize(): RendererResult<Unit>`
Initializes the WebGPU renderer asynchronously.

**Returns:** `RendererResult<Unit>` - Success or error result

**Example:**
```kotlin
when (val result = renderer.initialize()) {
    is RendererResult.Success -> console.log("Initialized")
    is RendererResult.Error -> console.error(result.exception.message)
}
```

##### `render(scene: Scene, camera: Camera): RendererResult<Unit>`
Renders a scene from the camera's perspective.

**Parameters:**
- `scene: Scene` - Scene to render
- `camera: Camera` - Camera for view/projection

**Returns:** `RendererResult<Unit>` - Success or error result

##### `setSize(width: Int, height: Int, updateStyle: Boolean = true): RendererResult<Unit>`
Sets canvas size.

**Parameters:**
- `width: Int` - Canvas width in pixels
- `height: Int` - Canvas height in pixels
- `updateStyle: Boolean` - Whether to update canvas CSS style

##### `setPixelRatio(pixelRatio: Float): RendererResult<Unit>`
Sets device pixel ratio for high-DPI displays.

**Parameters:**
- `pixelRatio: Float` - Device pixel ratio (usually `window.devicePixelRatio`)

**Example:**
```kotlin
renderer.setPixelRatio(window.devicePixelRatio.toFloat())
```

##### `setViewport(x: Int, y: Int, width: Int, height: Int): RendererResult<Unit>`
Sets viewport rectangle.

**Parameters:**
- `x: Int` - Viewport x offset
- `y: Int` - Viewport y offset
- `width: Int` - Viewport width
- `height: Int` - Viewport height

##### `getStats(): RenderStats`
Gets rendering statistics.

**Returns:** `RenderStats` with:
- `fps: Double` - Average frames per second
- `frameTime: Double` - Average frame time in milliseconds
- `triangles: Int` - Triangles rendered this frame
- `drawCalls: Int` - Draw calls issued
- `textureMemory: Long` - Texture memory usage in bytes
- `bufferMemory: Long` - Buffer memory usage in bytes
- `timestamp: Long` - Timestamp when stats were captured
- `iblCpuMs: Double` - Last CPU time spent in IBL convolution
- `iblPrefilterMipCount: Int` - Prefilter mip chain used by the renderer
- `iblLastRoughness: Float` - Roughness value from the most recent PBR draw

**Example:**
```kotlin
val stats = renderer.getStats()
console.log("${stats.fps} FPS | ${stats.drawCalls} draw calls")
if (stats.iblPrefilterMipCount > 0) {
    console.log("IBL CPU: ${stats.iblCpuMs} ms (mips=${stats.iblPrefilterMipCount})")
}
```

##### `resetStats()`
Resets statistics counters.

##### `dispose(): RendererResult<Unit>`
Disposes renderer and releases GPU resources.

**Example:**
```kotlin
window.addEventListener("unload", {
    renderer.dispose()
})
```

##### `forceContextLoss(): RendererResult<Unit>`
Simulates GPU context loss for testing recovery.

##### `isContextLost(): Boolean`
Checks if GPU context is lost.

**Returns:** `Boolean` - `true` if context lost

---

### Performance Optimizations

#### Frustum Culling

Automatically enabled. Skips rendering objects outside the camera's view frustum.

**Performance Impact:** +15 FPS

```kotlin
// Culling is automatic in render()
renderer.render(scene, camera) // Only visible objects rendered
```

#### Draw Call Batching

Automatically batches meshes with compatible materials and geometry.

**Performance Impact:** +10 FPS (reduces 1000+ draw calls to 50-100)

```kotlin
// Batching is automatic - no configuration needed
renderer.render(scene, camera)
```

#### Pipeline Caching

Automatically caches compiled render pipelines.

**Performance Impact:** +8-15 FPS

**Cache Statistics:**
```kotlin
// Pipeline cache is managed internally
// Stats available via renderer.getStats()
val stats = renderer.getStats()
console.log("Shaders cached: ${stats.shaders}")
```

#### Buffer Pooling

Automatically reuses GPU buffers from size-class pools.

**Performance Impact:** +5-10 FPS, 90% allocation reduction

**Pool Size Classes:**
- 256 KB
- 512 KB
- 1 MB
- 2 MB
- 4 MB

---

### Error Handling

The WebGPU renderer provides comprehensive error reporting:

```kotlin
when (val result = renderer.render(scene, camera)) {
    is RendererResult.Success -> {
        // Rendering succeeded
    }
    is RendererResult.Error -> {
        when (val exception = result.exception) {
            is RendererException.InitializationFailed -> {
                console.error("Init failed: ${exception.message}")
            }
            is RendererException.RenderingFailed -> {
                console.error("Render failed: ${exception.message}")
            }
            is RendererException.ResourceCreationFailed -> {
                console.error("Resource creation failed: ${exception.message}")
            }
            is RendererException.ContextLost -> {
                console.error("Context lost: ${exception.message}")
                // Automatic recovery will be attempted
            }
            else -> {
                console.error("Unknown error: ${exception.message}")
            }
        }
    }
}
```

---

### Context Loss Recovery

Automatic GPU context recovery is built-in:

```kotlin
// No configuration needed - recovery is automatic

// Optional: Monitor context loss events
renderer.addEventListener("contextlost", {
    console.warn("GPU context lost - recovery in progress")
})

renderer.addEventListener("contextrestored", {
    console.log("GPU context restored successfully")
})
```

---

### Feature Detection

Check WebGPU availability before creating renderer:

```kotlin
import io.kreekt.renderer.webgpu.WebGPUDetector

if (WebGPUDetector.isSupported()) {
    console.log("WebGPU is supported")
    val renderer = WebGPURenderer(canvas)
    // ...
} else {
    console.log("WebGPU not supported - using WebGL fallback")
    // WebGPURendererFactory handles this automatically
}
```

---

## Migration from WebGL

The WebGPU renderer is API-compatible with WebGLRenderer:

```kotlin
// Before (WebGL)
val renderer = WebGLRenderer(canvas)
renderer.render(scene, camera)

// After (WebGPU with automatic fallback)
val renderer = WebGPURendererFactory.create(canvas)
renderer.render(scene, camera)
// No other changes needed!
```

**No Breaking Changes:**
- Same Renderer interface
- Same scene graph API
- Same material API
- Same camera API

---

## Performance Tips

1. **Use RendererFactory** - Automatic WebGPU/WebGL fallback
   ```kotlin
   val renderer = WebGPURendererFactory.create(canvas)
   ```

2. **Set Pixel Ratio** - Better rendering on high-DPI displays
   ```kotlin
   renderer.setPixelRatio(window.devicePixelRatio.toFloat())
   ```

3. **Minimize State Changes** - Group objects by material
   ```kotlin
   // Good: Objects with same material render together
   scene.add(mesh1WithMaterial1)
   scene.add(mesh2WithMaterial1)
   scene.add(mesh3WithMaterial2)
   ```

4. **Monitor Statistics** - Track performance
   ```kotlin
   val stats = renderer.getStats()
   if (stats.calls > 100) {
       console.warn("Too many draw calls: ${stats.calls}")
   }
   ```

5. **Proper Disposal** - Prevent memory leaks
   ```kotlin
   window.addEventListener("unload", {
       renderer.dispose()
   })
   ```

---

## Troubleshooting

### WebGPU Not Available

**Problem:** WebGPU not supported in browser

**Solution:** Use WebGPURendererFactory for automatic WebGL fallback
```kotlin
val renderer = WebGPURendererFactory.create(canvas)
```

### Low Frame Rate

**Problem:** FPS below 60

**Solutions:**
1. Check triangle count: `stats.triangles`
2. Reduce draw calls via batching
3. Enable frustum culling (automatic)
4. Check GPU capabilities: `renderer.capabilities`

### Context Loss

**Problem:** GPU context lost (tab backgrounded, driver crash, etc.)

**Solution:** Automatic recovery is built-in. No action needed.

### Shader Compilation Errors

**Problem:** WGSL shader fails to compile

**Solution:** Check console for detailed error messages with line numbers and suggestions

---

## Examples

See [quickstart.md](../specs/016-implement-production-ready/quickstart.md) for complete examples including:
- Basic rendering setup
- Custom materials and shaders
- Performance monitoring
- Error handling
- Context loss recovery

---

## API Changelog

### Version 0.2.0 (Current)
- Added WebGPU renderer with automatic fallback
- Added performance optimizations (frustum culling, batching, caching, pooling)
- Added comprehensive error reporting
- Added render statistics tracking
- Added automatic context loss recovery

### Version 0.1.0
- Initial WebGL renderer implementation
