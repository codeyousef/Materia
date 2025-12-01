# Renderer API Reference

The renderer module provides GPU rendering backends for WebGPU and Vulkan.

## Overview

```kotlin
import io.materia.renderer.*
```

---

## Renderer (Base Interface)

Common interface for all renderers.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `width` | `Int` | Render width in pixels |
| `height` | `Int` | Render height in pixels |
| `pixelRatio` | `Float` | Device pixel ratio |
| `outputColorSpace` | `ColorSpace` | Output color space |
| `toneMapping` | `ToneMapping` | Tone mapping mode |
| `toneMappingExposure` | `Float` | Exposure for tone mapping |
| `shadowMap` | `ShadowMap` | Shadow configuration |
| `info` | `RendererInfo` | Render statistics |

### Methods

```kotlin
// Render a scene
fun render(scene: Scene, camera: Camera)

// Set render size
fun setSize(width: Int, height: Int)

// Set pixel ratio
fun setPixelRatio(ratio: Float)

// Set viewport
fun setViewport(x: Int, y: Int, width: Int, height: Int)

// Set scissor
fun setScissor(x: Int, y: Int, width: Int, height: Int)
fun setScissorTest(enabled: Boolean)

// Clear buffers
fun clear(color: Boolean = true, depth: Boolean = true, stencil: Boolean = true)
fun setClearColor(color: Color, alpha: Float = 1f)

// Render to texture
fun setRenderTarget(target: RenderTarget?)
fun getRenderTarget(): RenderTarget?

// Read pixels
fun readRenderTargetPixels(
    target: RenderTarget,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    buffer: ByteArray
)

// Dispose resources
fun dispose()
```

---

## WebGPURenderer

Renderer using the WebGPU API (JavaScript/WASM target).

### Constructor

```kotlin
class WebGPURenderer(
    canvas: HTMLCanvasElement,
    options: WebGPURendererOptions = WebGPURendererOptions()
)
```

### Options

```kotlin
data class WebGPURendererOptions(
    val antialias: Boolean = true,
    val alpha: Boolean = true,
    val depth: Boolean = true,
    val stencil: Boolean = false,
    val powerPreference: PowerPreference = PowerPreference.HIGH_PERFORMANCE,
    val requiredFeatures: List<String> = emptyList(),
    val requiredLimits: Map<String, Long> = emptyMap()
)

enum class PowerPreference {
    LOW_POWER,
    HIGH_PERFORMANCE
}
```

### Initialization

WebGPU requires async initialization:

```kotlin
// Async creation
WebGPURenderer.create(canvas, options) { renderer ->
    // Renderer ready
    val scene = Scene()
    val camera = PerspectiveCamera()
    
    fun animate() {
        renderer.render(scene, camera)
        window.requestAnimationFrame(::animate)
    }
    animate()
}

// Or with coroutines
suspend fun main() {
    val renderer = WebGPURenderer.createAsync(canvas, options)
    // ...
}
```

### WebGPU-Specific Properties

| Property | Type | Description |
|----------|------|-------------|
| `device` | `GPUDevice` | WebGPU device |
| `context` | `GPUCanvasContext` | Canvas context |
| `format` | `GPUTextureFormat` | Preferred format |

---

## VulkanRenderer

Renderer using the Vulkan API (JVM target via LWJGL).

### Constructor

```kotlin
class VulkanRenderer(
    window: Window,
    options: VulkanRendererOptions = VulkanRendererOptions()
)
```

### Options

```kotlin
data class VulkanRendererOptions(
    val antialias: Boolean = true,
    val vsync: Boolean = true,
    val validation: Boolean = false,  // Enable validation layers
    val deviceExtensions: List<String> = emptyList(),
    val sampleCount: Int = 4  // MSAA samples
)
```

### Initialization

```kotlin
// Create window with Vulkan surface
val window = Window(
    title = "My App",
    width = 1280,
    height = 720
)

// Create renderer
val renderer = VulkanRenderer(window, VulkanRendererOptions(
    antialias = true,
    vsync = true,
    validation = BuildConfig.DEBUG  // Enable in debug builds
))

// Render loop
window.run { deltaTime ->
    renderer.render(scene, camera)
}

// Cleanup
renderer.dispose()
window.dispose()
```

### Vulkan-Specific Properties

| Property | Type | Description |
|----------|------|-------------|
| `instance` | `VkInstance` | Vulkan instance |
| `device` | `VkDevice` | Logical device |
| `physicalDevice` | `VkPhysicalDevice` | Physical device |
| `swapchain` | `Swapchain` | Swapchain manager |

---

## Color Spaces

```kotlin
enum class ColorSpace {
    SRGB,           // Standard sRGB
    LINEAR_SRGB,    // Linear sRGB
    DISPLAY_P3      // Display P3 wide gamut
}
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

### Example

```kotlin
// HDR with ACES tone mapping
renderer.toneMapping = ToneMapping.ACES_FILMIC
renderer.toneMappingExposure = 1.0f

// Increase exposure for brighter scene
renderer.toneMappingExposure = 1.5f
```

---

## Shadow Map

### Properties

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

### Example

```kotlin
// Enable shadows
renderer.shadowMap.enabled = true
renderer.shadowMap.type = ShadowMapType.PCF_SOFT

// Manual shadow update
renderer.shadowMap.autoUpdate = false
renderer.shadowMap.needsUpdate = true  // Request update
```

---

## Render Targets

### WebGLRenderTarget / RenderTarget

Off-screen render target for effects.

```kotlin
class RenderTarget(
    width: Int,
    height: Int,
    options: RenderTargetOptions = RenderTargetOptions()
)
```

### Options

```kotlin
data class RenderTargetOptions(
    val minFilter: TextureFilter = TextureFilter.LINEAR,
    val magFilter: TextureFilter = TextureFilter.LINEAR,
    val format: TextureFormat = TextureFormat.RGBA,
    val type: TextureDataType = TextureDataType.UNSIGNED_BYTE,
    val depthBuffer: Boolean = true,
    val stencilBuffer: Boolean = false,
    val depthTexture: DepthTexture? = null,
    val samples: Int = 0,  // MSAA samples
    val colorSpace: ColorSpace = ColorSpace.SRGB
)
```

### Example

```kotlin
// Create render target
val renderTarget = RenderTarget(1024, 1024, RenderTargetOptions(
    minFilter = TextureFilter.LINEAR,
    magFilter = TextureFilter.LINEAR,
    format = TextureFormat.RGBA,
    depthBuffer = true
))

// Render to target
renderer.setRenderTarget(renderTarget)
renderer.clear()
renderer.render(scene, camera)

// Use as texture
material.map = renderTarget.texture

// Render to screen
renderer.setRenderTarget(null)
renderer.render(mainScene, mainCamera)
```

### Multiple Render Targets (MRT)

```kotlin
class MultipleRenderTargets(
    width: Int,
    height: Int,
    count: Int,
    options: RenderTargetOptions = RenderTargetOptions()
) {
    val textures: Array<Texture>  // Color attachments
}
```

---

## Render Info

Statistics about rendering.

```kotlin
class RendererInfo {
    val memory: MemoryInfo
    val render: RenderInfo
    
    fun reset()  // Reset frame counters
}

class MemoryInfo {
    var geometries: Int = 0
    var textures: Int = 0
}

class RenderInfo {
    var frame: Int = 0
    var calls: Int = 0
    var triangles: Int = 0
    var points: Int = 0
    var lines: Int = 0
}
```

### Example

```kotlin
// Display stats
fun logStats() {
    val info = renderer.info
    println("Draw calls: ${info.render.calls}")
    println("Triangles: ${info.render.triangles}")
    println("Textures: ${info.memory.textures}")
}

// Reset per-frame stats
fun animate() {
    renderer.info.reset()
    renderer.render(scene, camera)
    logStats()
}
```

---

## Capabilities

Check GPU capabilities.

```kotlin
class Capabilities {
    val maxTextureSize: Int
    val maxCubeMapTextureSize: Int
    val maxAttributes: Int
    val maxVertexUniforms: Int
    val maxVaryings: Int
    val maxFragmentUniforms: Int
    val maxSamples: Int
    
    val floatFragmentTextures: Boolean
    val floatVertexTextures: Boolean
    val logarithmicDepthBuffer: Boolean
}
```

---

## Custom Rendering

### Manual Render Control

```kotlin
// Begin frame
renderer.beginFrame()

// Set up state
renderer.setViewport(0, 0, width, height)
renderer.setClearColor(Color.BLACK)
renderer.clear()

// Render multiple scenes/cameras
renderer.render(backgroundScene, backgroundCamera)
renderer.render(mainScene, mainCamera)
renderer.render(uiScene, uiCamera)

// End frame
renderer.endFrame()
```

### Render Lists

```kotlin
// Get current render lists
val renderLists = renderer.renderLists

// Custom sorting
renderLists.opaque.sort { a, b ->
    a.material.id.compareTo(b.material.id)  // Sort by material
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

// Merge static geometry
val mergedGeometry = BufferGeometryUtils.mergeGeometries(geometries)
```

### Reduce State Changes

```kotlin
// Sort by material
renderer.sortObjects = true

// Use texture atlases
val atlas = TextureAtlas()
atlas.add("wood", woodTexture)
atlas.add("metal", metalTexture)
// Single texture, multiple UVs
```

### Optimize Shadows

```kotlin
// Use appropriate shadow map sizes
mainLight.shadow.mapSize.set(1024f, 1024f)  // Main light
fillLight.shadow.mapSize.set(256f, 256f)    // Fill light

// Limit shadow casters
distantObject.castShadow = false

// Use cascaded shadow maps for large scenes
// (Configured via light.shadow.cascade)
```

### LOD (Level of Detail)

```kotlin
val lod = LOD()
lod.addLevel(highDetailMesh, 0f)
lod.addLevel(mediumDetailMesh, 50f)
lod.addLevel(lowDetailMesh, 100f)
scene.add(lod)

// Update LOD in render loop
lod.update(camera)
```

---

## See Also

- [Camera API](camera.md) - Cameras for rendering
- [Material API](material.md) - Materials and shaders
- [Advanced: Performance](../advanced/performance.md) - Optimization guide
- [Advanced: Post-Processing](../advanced/post-processing.md) - Effects pipeline
