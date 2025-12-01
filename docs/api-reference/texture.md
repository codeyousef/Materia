# Texture API Reference

The texture module provides classes for managing texture data and sampling.

## Overview

```kotlin
import io.materia.texture.*
```

---

## Texture

Base class for all textures.

### Constructor

```kotlin
class Texture(
    image: Image? = null,
    mapping: TextureMapping = TextureMapping.UV,
    wrapS: TextureWrapping = TextureWrapping.CLAMP_TO_EDGE,
    wrapT: TextureWrapping = TextureWrapping.CLAMP_TO_EDGE,
    magFilter: TextureFilter = TextureFilter.LINEAR,
    minFilter: TextureFilter = TextureFilter.LINEAR_MIPMAP_LINEAR,
    format: TextureFormat = TextureFormat.RGBA,
    type: TextureDataType = TextureDataType.UNSIGNED_BYTE,
    anisotropy: Int = 1
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `id` | `Int` | auto | Unique identifier |
| `uuid` | `String` | auto | UUID string |
| `name` | `String` | `""` | Optional name |
| `image` | `Image?` | `null` | Source image |
| `mipmaps` | `List<Image>` | `[]` | Mipmap levels |
| `mapping` | `TextureMapping` | `UV` | Mapping mode |
| `wrapS` | `TextureWrapping` | `CLAMP` | Horizontal wrap |
| `wrapT` | `TextureWrapping` | `CLAMP` | Vertical wrap |
| `magFilter` | `TextureFilter` | `LINEAR` | Magnification filter |
| `minFilter` | `TextureFilter` | `LINEAR_MIPMAP_LINEAR` | Minification filter |
| `anisotropy` | `Int` | `1` | Anisotropic filtering |
| `format` | `TextureFormat` | `RGBA` | Pixel format |
| `internalFormat` | `String?` | `null` | Internal GPU format |
| `type` | `TextureDataType` | `UNSIGNED_BYTE` | Data type |
| `offset` | `Vector2` | `(0,0)` | UV offset |
| `repeat` | `Vector2` | `(1,1)` | UV repeat |
| `center` | `Vector2` | `(0,0)` | Rotation center |
| `rotation` | `Float` | `0` | Rotation (radians) |
| `generateMipmaps` | `Boolean` | `true` | Auto-generate mipmaps |
| `premultiplyAlpha` | `Boolean` | `false` | Premultiply alpha |
| `flipY` | `Boolean` | `true` | Flip Y on upload |
| `unpackAlignment` | `Int` | `4` | Byte alignment |
| `colorSpace` | `ColorSpace` | `SRGB` | Color space |
| `needsUpdate` | `Boolean` | `false` | Needs GPU upload |
| `userData` | `Map<String, Any>` | `{}` | Custom data |

### Texture Mapping

```kotlin
enum class TextureMapping {
    UV,                           // Standard UV mapping
    CUBE_REFLECTION,              // Cube map reflection
    CUBE_REFRACTION,              // Cube map refraction
    EQUIRECTANGULAR_REFLECTION,   // Equirectangular reflection
    EQUIRECTANGULAR_REFRACTION    // Equirectangular refraction
}
```

### Texture Wrapping

```kotlin
enum class TextureWrapping {
    REPEAT,           // Tile texture
    CLAMP_TO_EDGE,    // Clamp to edge
    MIRRORED_REPEAT   // Mirror and tile
}
```

### Texture Filter

```kotlin
enum class TextureFilter {
    NEAREST,                // Nearest neighbor
    NEAREST_MIPMAP_NEAREST, // Nearest with nearest mipmap
    NEAREST_MIPMAP_LINEAR,  // Nearest with linear mipmap
    LINEAR,                 // Bilinear
    LINEAR_MIPMAP_NEAREST,  // Bilinear with nearest mipmap
    LINEAR_MIPMAP_LINEAR    // Trilinear
}
```

### Texture Format

```kotlin
enum class TextureFormat {
    ALPHA,
    RED,
    RED_INTEGER,
    RG,
    RG_INTEGER,
    RGB,
    RGB_INTEGER,
    RGBA,
    RGBA_INTEGER,
    LUMINANCE,
    LUMINANCE_ALPHA,
    DEPTH,
    DEPTH_STENCIL
}
```

### Texture Data Type

```kotlin
enum class TextureDataType {
    UNSIGNED_BYTE,
    BYTE,
    SHORT,
    UNSIGNED_SHORT,
    INT,
    UNSIGNED_INT,
    FLOAT,
    HALF_FLOAT,
    UNSIGNED_INT_24_8,
    UNSIGNED_SHORT_4_4_4_4,
    UNSIGNED_SHORT_5_5_5_1,
    UNSIGNED_SHORT_5_6_5
}
```

### Methods

```kotlin
// Update transform matrix from offset, repeat, rotation, center
fun updateMatrix()

// Clone texture
fun clone(): Texture

// Copy from another texture
fun copy(source: Texture): Texture

// Convert to JSON
fun toJSON(meta: Any? = null): JsonObject

// Dispose GPU resources
fun dispose()

// Transform UV
fun transformUv(uv: Vector2): Vector2
```

### Example

```kotlin
// Load and configure texture
val texture = textureLoader.load("textures/brick.jpg")
texture.wrapS = TextureWrapping.REPEAT
texture.wrapT = TextureWrapping.REPEAT
texture.repeat.set(4f, 4f)
texture.anisotropy = renderer.capabilities.maxAnisotropy

// Offset and rotate
texture.offset.set(0.5f, 0f)
texture.center.set(0.5f, 0.5f)
texture.rotation = PI / 4

// Apply to material
material.map = texture
```

---

## DataTexture

Texture from raw pixel data.

### Constructor

```kotlin
class DataTexture(
    data: ByteArray,
    width: Int,
    height: Int,
    format: TextureFormat = TextureFormat.RGBA,
    type: TextureDataType = TextureDataType.UNSIGNED_BYTE
)
```

### Example

```kotlin
// Create checkerboard texture
val size = 64
val data = ByteArray(size * size * 4)

for (y in 0 until size) {
    for (x in 0 until size) {
        val i = (y * size + x) * 4
        val isWhite = (x / 8 + y / 8) % 2 == 0
        val value = if (isWhite) 255.toByte() else 0.toByte()
        data[i] = value      // R
        data[i + 1] = value  // G
        data[i + 2] = value  // B
        data[i + 3] = 255.toByte()  // A
    }
}

val texture = DataTexture(data, size, size)
texture.needsUpdate = true
```

---

## Data3DTexture

3D texture (volume).

### Constructor

```kotlin
class Data3DTexture(
    data: ByteArray,
    width: Int,
    height: Int,
    depth: Int
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `wrapR` | `TextureWrapping` | Depth wrap mode |

### Example

```kotlin
// Create 3D noise texture
val size = 32
val data = ByteArray(size * size * size)

for (z in 0 until size) {
    for (y in 0 until size) {
        for (x in 0 until size) {
            val i = z * size * size + y * size + x
            data[i] = (noise3D(x, y, z) * 255).toByte()
        }
    }
}

val texture = Data3DTexture(data, size, size, size)
texture.format = TextureFormat.RED
```

---

## DataArrayTexture

Array of 2D textures.

### Constructor

```kotlin
class DataArrayTexture(
    data: ByteArray,
    width: Int,
    height: Int,
    depth: Int  // Number of layers
)
```

---

## CompressedTexture

GPU-compressed texture.

### Constructor

```kotlin
class CompressedTexture(
    mipmaps: List<CompressedMipmap>,
    width: Int,
    height: Int,
    format: CompressedTextureFormat,
    type: TextureDataType = TextureDataType.UNSIGNED_BYTE
)
```

### Compressed Formats

```kotlin
enum class CompressedTextureFormat {
    // S3TC (Desktop)
    RGB_S3TC_DXT1,
    RGBA_S3TC_DXT1,
    RGBA_S3TC_DXT3,
    RGBA_S3TC_DXT5,
    
    // PVRTC (iOS)
    RGB_PVRTC_4BPPV1,
    RGB_PVRTC_2BPPV1,
    RGBA_PVRTC_4BPPV1,
    RGBA_PVRTC_2BPPV1,
    
    // ETC
    RGB_ETC1,
    RGB_ETC2,
    RGBA_ETC2_EAC,
    
    // ASTC
    RGBA_ASTC_4x4,
    RGBA_ASTC_5x4,
    RGBA_ASTC_5x5,
    RGBA_ASTC_6x5,
    RGBA_ASTC_6x6,
    RGBA_ASTC_8x5,
    RGBA_ASTC_8x6,
    RGBA_ASTC_8x8,
    RGBA_ASTC_10x5,
    RGBA_ASTC_10x6,
    RGBA_ASTC_10x8,
    RGBA_ASTC_10x10,
    RGBA_ASTC_12x10,
    RGBA_ASTC_12x12,
    
    // BPTC
    RGBA_BPTC,
    RGB_BPTC_SIGNED,
    RGB_BPTC_UNSIGNED
}
```

---

## CubeTexture

Cube map texture for skyboxes and environment maps.

### Constructor

```kotlin
class CubeTexture(
    images: Array<Image> = Array(6) { null },
    mapping: TextureMapping = TextureMapping.CUBE_REFLECTION
)
```

### Face Order

```kotlin
// images[0] = positive X (right)
// images[1] = negative X (left)
// images[2] = positive Y (top)
// images[3] = negative Y (bottom)
// images[4] = positive Z (front)
// images[5] = negative Z (back)
```

### Example

```kotlin
val cubeTexture = cubeTextureLoader.load(arrayOf(
    "px.jpg", "nx.jpg",
    "py.jpg", "ny.jpg",
    "pz.jpg", "nz.jpg"
))

// As skybox
scene.background = cubeTexture

// As environment map
scene.environment = cubeTexture

// On material
material.envMap = cubeTexture
material.envMapIntensity = 1.0f
```

---

## CanvasTexture

Texture from HTML Canvas.

### Constructor

```kotlin
class CanvasTexture(
    canvas: HTMLCanvasElement,
    mapping: TextureMapping = TextureMapping.UV
)
```

### Example

```kotlin
// Create canvas
val canvas = document.createElement("canvas") as HTMLCanvasElement
canvas.width = 256
canvas.height = 256

val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
ctx.fillStyle = "red"
ctx.fillRect(0, 0, 256, 256)
ctx.fillStyle = "white"
ctx.font = "48px sans-serif"
ctx.fillText("Hello!", 50, 128)

// Create texture
val texture = CanvasTexture(canvas)

// Update after drawing
ctx.fillStyle = "blue"
ctx.fillRect(100, 100, 50, 50)
texture.needsUpdate = true
```

---

## VideoTexture

Texture from HTML Video.

### Constructor

```kotlin
class VideoTexture(
    video: HTMLVideoElement,
    mapping: TextureMapping = TextureMapping.UV
)
```

### Example

```kotlin
// Create video element
val video = document.createElement("video") as HTMLVideoElement
video.src = "video/movie.mp4"
video.loop = true
video.muted = true
video.play()

// Create texture
val texture = VideoTexture(video)
texture.colorSpace = ColorSpace.SRGB

// Apply to material
material.map = texture

// Texture auto-updates each frame
```

---

## DepthTexture

Texture for storing depth information.

### Constructor

```kotlin
class DepthTexture(
    width: Int,
    height: Int,
    type: TextureDataType = TextureDataType.UNSIGNED_INT,
    mapping: TextureMapping = TextureMapping.UV
)
```

### Example

```kotlin
// Create render target with depth texture
val depthTexture = DepthTexture(1024, 1024)

val renderTarget = RenderTarget(1024, 1024, RenderTargetOptions(
    depthTexture = depthTexture
))

// Render to target
renderer.setRenderTarget(renderTarget)
renderer.render(scene, camera)

// Use depth texture
depthMaterial.map = renderTarget.depthTexture
```

---

## FramebufferTexture

Texture backed by framebuffer for copyTexImage.

### Constructor

```kotlin
class FramebufferTexture(
    width: Int,
    height: Int
)
```

---

## Texture Utilities

### PMREMGenerator

Generates prefiltered environment maps for IBL.

```kotlin
class PMREMGenerator(renderer: Renderer) {
    // From equirectangular texture
    fun fromEquirectangular(texture: Texture): RenderTarget
    
    // From cube texture
    fun fromCubemap(cubeTexture: CubeTexture): RenderTarget
    
    // From scene (capture environment)
    fun fromScene(scene: Scene, sigma: Float = 0f, near: Float = 0.1f, far: Float = 100f): RenderTarget
    
    // Compile shaders
    fun compileEquirectangularShader()
    fun compileCubemapShader()
    
    // Dispose
    fun dispose()
}
```

### Example

```kotlin
val pmremGenerator = PMREMGenerator(renderer)

// From HDR environment
rgbeLoader.load("environment.hdr") { texture ->
    val envMap = pmremGenerator.fromEquirectangular(texture).texture
    scene.environment = envMap
    texture.dispose()
}

pmremGenerator.dispose()
```

---

## Sampler (WebGPU)

Explicit sampler state.

```kotlin
class Sampler(
    addressModeU: AddressMode = AddressMode.CLAMP_TO_EDGE,
    addressModeV: AddressMode = AddressMode.CLAMP_TO_EDGE,
    addressModeW: AddressMode = AddressMode.CLAMP_TO_EDGE,
    magFilter: FilterMode = FilterMode.LINEAR,
    minFilter: FilterMode = FilterMode.LINEAR,
    mipmapFilter: MipmapFilterMode = MipmapFilterMode.LINEAR,
    lodMinClamp: Float = 0f,
    lodMaxClamp: Float = 32f,
    compare: CompareFunction? = null,
    maxAnisotropy: Int = 1
)

enum class AddressMode {
    CLAMP_TO_EDGE,
    REPEAT,
    MIRROR_REPEAT
}

enum class FilterMode {
    NEAREST,
    LINEAR
}

enum class MipmapFilterMode {
    NEAREST,
    LINEAR
}
```

---

## Common Patterns

### Tiling Texture

```kotlin
val texture = textureLoader.load("textures/tile.jpg")
texture.wrapS = TextureWrapping.REPEAT
texture.wrapT = TextureWrapping.REPEAT
texture.repeat.set(10f, 10f)  // Tile 10x10
```

### Animated UV

```kotlin
fun animate(time: Float) {
    texture.offset.x = time * 0.1f  // Scroll horizontally
    // No needsUpdate needed for offset/repeat
}
```

### Sprite Sheet

```kotlin
val texture = textureLoader.load("textures/spritesheet.png")
texture.magFilter = TextureFilter.NEAREST  // Pixel art

// 4x4 sprite sheet, show sprite at (col, row)
fun setSprite(col: Int, row: Int) {
    texture.offset.set(col / 4f, 1 - (row + 1) / 4f)
    texture.repeat.set(0.25f, 0.25f)
}
```

### Render to Texture

```kotlin
val renderTarget = RenderTarget(512, 512)

fun renderToTexture() {
    renderer.setRenderTarget(renderTarget)
    renderer.clear()
    renderer.render(offscreenScene, offscreenCamera)
    renderer.setRenderTarget(null)
}

// Use as texture
screenMaterial.map = renderTarget.texture
```

---

## See Also

- [Loader API](loader.md) - Loading textures
- [Material API](material.md) - Applying textures
- [Renderer API](renderer.md) - Render targets
