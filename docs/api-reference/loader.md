# Loader API Reference

The loader module provides classes for loading 3D models, textures, and other assets.

## Overview

```kotlin
import io.materia.loader.*
```

---

## Loader (Base Class)

Abstract base class for all loaders.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `manager` | `LoadingManager` | Loading manager |
| `crossOrigin` | `String` | CORS setting |
| `withCredentials` | `Boolean` | Include credentials |
| `path` | `String` | Base path for assets |
| `resourcePath` | `String` | Resource path |
| `requestHeader` | `Map<String, String>` | HTTP headers |

### Methods

```kotlin
// Set base path
fun setPath(path: String): Loader

// Set resource path
fun setResourcePath(path: String): Loader

// Set cross-origin
fun setCrossOrigin(crossOrigin: String): Loader

// Set request headers
fun setRequestHeader(headers: Map<String, String>): Loader
```

---

## GLTFLoader

Loads glTF 2.0 and GLB files (industry standard format).

### Constructor

```kotlin
class GLTFLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
// Load glTF/GLB file
fun load(
    url: String,
    onLoad: (GLTF) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

// Load with coroutines
suspend fun loadAsync(url: String): GLTF

// Parse from ArrayBuffer
fun parse(
    data: ArrayBuffer,
    path: String,
    onLoad: (GLTF) -> Unit,
    onError: ((Exception) -> Unit)? = null
)

// Register extension
fun register(callback: (GLTFParser) -> GLTFLoaderPlugin): GLTFLoader

// Unregister extension
fun unregister(callback: (GLTFParser) -> GLTFLoaderPlugin): GLTFLoader

// Set Draco decoder path
fun setDRACOLoader(dracoLoader: DRACOLoader): GLTFLoader

// Set KTX2 loader
fun setKTX2Loader(ktx2Loader: KTX2Loader): GLTFLoader

// Set mesh optimizer decoder
fun setMeshoptDecoder(decoder: MeshoptDecoder): GLTFLoader
```

### GLTF Result

```kotlin
data class GLTF(
    val scene: Group,                    // Root scene
    val scenes: List<Group>,             // All scenes
    val animations: List<AnimationClip>, // Animations
    val cameras: List<Camera>,           // Cameras
    val asset: GLTFAsset,                // Asset metadata
    val parser: GLTFParser,              // Parser instance
    val userData: Map<String, Any>       // Custom data
)

data class GLTFAsset(
    val version: String,
    val generator: String?,
    val copyright: String?,
    val minVersion: String?
)
```

### Example

```kotlin
val loader = GLTFLoader()

// Basic loading
loader.load("models/robot.glb") { gltf ->
    scene.add(gltf.scene)
    
    // Play animations
    if (gltf.animations.isNotEmpty()) {
        val mixer = AnimationMixer(gltf.scene)
        val action = mixer.clipAction(gltf.animations[0])
        action.play()
    }
}

// With Draco compression
val dracoLoader = DRACOLoader()
dracoLoader.setDecoderPath("libs/draco/")
loader.setDRACOLoader(dracoLoader)

loader.load("models/compressed.glb") { gltf ->
    scene.add(gltf.scene)
}

// With progress
loader.load(
    url = "models/large.glb",
    onLoad = { gltf -> scene.add(gltf.scene) },
    onProgress = { event ->
        val percent = (event.loaded / event.total) * 100
        println("Loading: $percent%")
    },
    onError = { error ->
        println("Error: ${error.message}")
    }
)
```

---

## OBJLoader

Loads Wavefront OBJ files.

### Constructor

```kotlin
class OBJLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
// Load OBJ file
fun load(
    url: String,
    onLoad: (Group) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

// Set materials
fun setMaterials(materials: MTLLoader.MaterialCreator): OBJLoader

// Parse OBJ string
fun parse(text: String): Group
```

### Example

```kotlin
val mtlLoader = MTLLoader()
val objLoader = OBJLoader()

// Load with materials
mtlLoader.load("models/model.mtl") { materials ->
    materials.preload()
    objLoader.setMaterials(materials)
    
    objLoader.load("models/model.obj") { obj ->
        scene.add(obj)
    }
}
```

---

## FBXLoader

Loads FBX files (ASCII format, static geometry only).

### Constructor

```kotlin
class FBXLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (Group) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun parse(buffer: ArrayBuffer, path: String): Group
```

### Example

```kotlin
val loader = FBXLoader()

loader.load("models/character.fbx") { fbx ->
    fbx.scale.setScalar(0.01f)  // FBX often uses cm
    scene.add(fbx)
    
    // Animations
    if (fbx.animations.isNotEmpty()) {
        val mixer = AnimationMixer(fbx)
        mixer.clipAction(fbx.animations[0]).play()
    }
}
```

---

## ColladaLoader

Loads COLLADA (.dae) files.

### Constructor

```kotlin
class ColladaLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (Collada) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

data class Collada(
    val scene: Group,
    val animations: List<AnimationClip>,
    val kinematics: Any?,
    val library: ColladaLibrary
)
```

---

## PLYLoader

Loads PLY (Polygon File Format) files.

### Constructor

```kotlin
class PLYLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (BufferGeometry) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun parse(data: ArrayBuffer): BufferGeometry
```

### Example

```kotlin
val loader = PLYLoader()

loader.load("models/scan.ply") { geometry ->
    geometry.computeVertexNormals()
    
    val material = MeshStandardMaterial().apply {
        vertexColors = true
    }
    val mesh = Mesh(geometry, material)
    scene.add(mesh)
}
```

---

## STLLoader

Loads STL (Stereolithography) files.

### Constructor

```kotlin
class STLLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (BufferGeometry) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun parse(data: ArrayBuffer): BufferGeometry
```

### Example

```kotlin
val loader = STLLoader()

loader.load("models/part.stl") { geometry ->
    val material = MeshPhongMaterial().apply {
        color = Color(0x888888)
        specular = Color(0x111111)
        shininess = 200f
    }
    val mesh = Mesh(geometry, material)
    mesh.rotation.x = -PI / 2  // STL often Z-up
    scene.add(mesh)
}
```

---

## DRACOLoader

Loads Draco-compressed geometry.

### Constructor

```kotlin
class DRACOLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
// Set decoder path
fun setDecoderPath(path: String): DRACOLoader

// Set decoder config
fun setDecoderConfig(config: DracoDecoderConfig): DRACOLoader

// Enable/disable worker
fun setWorkerLimit(limit: Int): DRACOLoader

// Preload decoder
fun preload(): DRACOLoader

// Load compressed file
fun load(
    url: String,
    onLoad: (BufferGeometry) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

// Dispose workers
fun dispose()
```

### Example

```kotlin
val dracoLoader = DRACOLoader()
dracoLoader.setDecoderPath("libs/draco/")
dracoLoader.setWorkerLimit(4)
dracoLoader.preload()

// Use with GLTFLoader
val gltfLoader = GLTFLoader()
gltfLoader.setDRACOLoader(dracoLoader)
```

---

## TextureLoader

Loads image textures.

### Constructor

```kotlin
class TextureLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: ((Texture) -> Unit)? = null,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
): Texture

suspend fun loadAsync(url: String): Texture
```

### Example

```kotlin
val loader = TextureLoader()

// Sync (texture updates when loaded)
val texture = loader.load("textures/wood.jpg")
material.map = texture

// With callback
loader.load("textures/wood.jpg") { texture ->
    texture.wrapS = TextureWrapping.REPEAT
    texture.wrapT = TextureWrapping.REPEAT
    texture.repeat.set(4f, 4f)
    material.map = texture
    material.needsUpdate = true
}

// Load multiple
val textures = listOf(
    "albedo.jpg",
    "normal.jpg",
    "roughness.jpg"
).map { loader.load("textures/pbr/$it") }
```

---

## CubeTextureLoader

Loads cube map textures (skyboxes, environment maps).

### Constructor

```kotlin
class CubeTextureLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    urls: Array<String>,  // [px, nx, py, ny, pz, nz]
    onLoad: ((CubeTexture) -> Unit)? = null,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
): CubeTexture
```

### Example

```kotlin
val loader = CubeTextureLoader()

// Load skybox
val cubeTexture = loader.load(arrayOf(
    "textures/sky/px.jpg",
    "textures/sky/nx.jpg",
    "textures/sky/py.jpg",
    "textures/sky/ny.jpg",
    "textures/sky/pz.jpg",
    "textures/sky/nz.jpg"
)) { texture ->
    scene.background = texture
    scene.environment = texture  // For reflections
}
```

---

## KTX2Loader

Loads KTX2 compressed textures.

### Constructor

```kotlin
class KTX2Loader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun setTranscoderPath(path: String): KTX2Loader
fun setWorkerLimit(limit: Int): KTX2Loader
fun detectSupport(renderer: Renderer): KTX2Loader

fun load(
    url: String,
    onLoad: ((CompressedTexture) -> Unit),
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun dispose()
```

### Example

```kotlin
val ktx2Loader = KTX2Loader()
ktx2Loader.setTranscoderPath("libs/basis/")
ktx2Loader.detectSupport(renderer)

ktx2Loader.load("textures/compressed.ktx2") { texture ->
    material.map = texture
}
```

---

## EXRLoader

Loads OpenEXR HDR images.

### Constructor

```kotlin
class EXRLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun setDataType(type: TextureDataType): EXRLoader

fun load(
    url: String,
    onLoad: ((DataTexture) -> Unit),
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)
```

### Example

```kotlin
val loader = EXRLoader()
loader.setDataType(TextureDataType.HALF_FLOAT)

loader.load("textures/environment.exr") { texture ->
    texture.mapping = TextureMapping.EQUIRECTANGULAR_REFLECTION
    scene.environment = texture
    scene.background = texture
}
```

---

## RGBELoader

Loads Radiance HDR (.hdr) images.

### Constructor

```kotlin
class RGBELoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun setDataType(type: TextureDataType): RGBELoader

fun load(
    url: String,
    onLoad: ((DataTexture) -> Unit),
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)
```

### Example

```kotlin
val loader = RGBELoader()

loader.load("textures/environment.hdr") { texture ->
    texture.mapping = TextureMapping.EQUIRECTANGULAR_REFLECTION
    scene.environment = texture
    
    // Convert to cube map for better performance
    val pmremGenerator = PMREMGenerator(renderer)
    val envMap = pmremGenerator.fromEquirectangular(texture).texture
    scene.environment = envMap
    texture.dispose()
    pmremGenerator.dispose()
}
```

---

## FontLoader

Loads JSON font files for TextGeometry.

### Constructor

```kotlin
class FontLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (Font) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun parse(json: JsonObject): Font
```

### Example

```kotlin
val loader = FontLoader()

loader.load("fonts/helvetiker_regular.typeface.json") { font ->
    val geometry = TextGeometry("Hello", TextGeometryOptions(
        font = font,
        size = 1f,
        height = 0.2f
    ))
    
    val mesh = Mesh(geometry, material)
    scene.add(mesh)
}
```

---

## LoadingManager

Manages loading progress across multiple loaders.

### Constructor

```kotlin
class LoadingManager(
    onLoad: (() -> Unit)? = null,
    onProgress: ((url: String, loaded: Int, total: Int) -> Unit)? = null,
    onError: ((url: String) -> Unit)? = null
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `onStart` | `((url: String, loaded: Int, total: Int) -> Unit)?` | Start callback |
| `onLoad` | `(() -> Unit)?` | All loaded callback |
| `onProgress` | `((url: String, loaded: Int, total: Int) -> Unit)?` | Progress callback |
| `onError` | `((url: String) -> Unit)?` | Error callback |

### Methods

```kotlin
// Get/set URL modifier
fun setURLModifier(callback: ((url: String) -> String)?): LoadingManager

// Add/remove handlers
fun addHandler(regex: Regex, loader: Loader): LoadingManager
fun removeHandler(regex: Regex): LoadingManager

// Get handler for URL
fun getHandler(file: String): Loader?

// Resolve URL
fun resolveURL(url: String): String

// Item tracking
fun itemStart(url: String)
fun itemEnd(url: String)
fun itemError(url: String)
```

### Example

```kotlin
// Create manager with callbacks
val manager = LoadingManager(
    onLoad = {
        println("All assets loaded!")
        hideLoadingScreen()
    },
    onProgress = { url, loaded, total ->
        val progress = loaded.toFloat() / total * 100
        updateLoadingBar(progress)
    },
    onError = { url ->
        println("Error loading: $url")
    }
)

// Use with loaders
val gltfLoader = GLTFLoader(manager)
val textureLoader = TextureLoader(manager)

// Load assets
gltfLoader.load("models/scene.glb") { /* ... */ }
textureLoader.load("textures/diffuse.jpg") { /* ... */ }
textureLoader.load("textures/normal.jpg") { /* ... */ }
// onLoad fires when all three complete
```

---

## FileLoader

Low-level file loading.

### Constructor

```kotlin
class FileLoader(manager: LoadingManager = DefaultLoadingManager)
```

### Methods

```kotlin
fun load(
    url: String,
    onLoad: (String) -> Unit,
    onProgress: ((ProgressEvent) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
)

fun setResponseType(type: String): FileLoader  // "text", "arraybuffer", "blob", "json"
fun setMimeType(mimeType: String): FileLoader
```

---

## See Also

- [Texture API](texture.md) - Texture configuration
- [Geometry API](geometry.md) - Geometry processing
- [Animation API](animation.md) - Playing loaded animations
