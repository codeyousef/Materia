# Architecture Overview

Materia is designed as a modular Kotlin Multiplatform library that provides Three.js-equivalent 3D
graphics capabilities
across JVM, Web, Android, iOS, and Native platforms.

## Design Principles

### 1. Type Safety First

- No runtime casts - all types validated at compile time
- Sealed classes for type hierarchies (Material, Light, Geometry)
- Strong type checking across platform boundaries
- Null safety enforced throughout

### 2. Three.js API Compatibility

- Familiar API patterns for easy migration from Three.js
- Method names and signatures match Three.js where possible
- Scene graph structure mirrors Three.js conventions
- Gradual enhancement with Kotlin features

### 3. Performance Optimized

- 60 FPS target with 100k triangles
- Object pooling for frequently allocated objects
- Dirty flag optimization for matrix updates
- Frustum culling and batching for rendering
- Lazy initialization of GPU resources

### 4. Modular Architecture

- <5MB base library size
- Optional modules for advanced features
- Tree-shakable dependencies
- Platform-specific optimizations

### 5. Modern Graphics

- WebGPU-first with sensible fallbacks
- PBR (Physically Based Rendering) materials
- Advanced lighting (IBL, area lights, shadows)
- Post-processing effects

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
├─────────────────────────────────────────────────────────────┤
│              Scene Graph & Object Hierarchy                  │
│  Scene → Group → Mesh/Light/Camera → Object3D               │
├─────────────────────────────────────────────────────────────┤
│                   Core Systems                               │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │  Animation  │  │   Physics    │  │     XR       │       │
│  └─────────────┘  └──────────────┘  └──────────────┘       │
├─────────────────────────────────────────────────────────────┤
│                Rendering Pipeline                            │
│  ┌──────┐  ┌──────────┐  ┌────────┐  ┌──────────────┐     │
│  │Culling│→│ Sorting  │→│Batching│→│Post-Processing│     │
│  └──────┘  └──────────┘  └────────┘  └──────────────┘     │
├─────────────────────────────────────────────────────────────┤
│           Platform Abstraction (expect/actual)               │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌──────────┐     │
│  │   JVM   │  │   Web   │  │ Android  │  │   iOS    │     │
│  │ Vulkan  │  │ WebGPU  │  │  Vulkan  │  │MoltenVK  │     │
│  └─────────┘  └─────────┘  └──────────┘  └──────────┘     │
└─────────────────────────────────────────────────────────────┘
```

## Module Structure

### Core Modules

**materia-core**

- Math primitives (Vector3, Matrix4, Quaternion)
- Scene graph system (Object3D, Scene, Camera)
- Core utilities and platform abstraction
- Size: ~500KB

**materia-renderer**

- WebGPU/Vulkan abstraction layer
- Platform-specific renderer implementations
- Buffer and state management
- Shader compilation
- Size: ~1MB

**materia-scene**

- Mesh, Light, Camera implementations
- Scene management and traversal
- Visibility and culling
- Size: ~300KB

**materia-geometry**

- Geometry primitives (Box, Sphere, Plane)
- Procedural generation
- Geometry processing and optimization
- Size: ~400KB

**materia-material**

- Material system (Basic, Standard, Physical)
- Shader management
- Texture handling
- Size: ~600KB

### Optional Modules

**materia-animation**

- Animation clips and mixers
- Skeletal animation
- Morph targets
- State machines
- IK solvers
- Size: ~400KB

**materia-physics**

- Rapier physics engine integration
- Rigid body dynamics
- Collision detection
- Character controllers
- Size: ~800KB (includes Rapier)

**materia-xr**

- WebXR integration (Web)
- ARKit integration (iOS)
- ARCore integration (Android)
- Hand tracking and input
- Size: ~300KB

**materia-loader**

- GLTF/GLB loader
- OBJ loader
## Asset Pipeline

- GLTF/GLB loader (complete)
- FBX ASCII loader (static meshes)
- COLLADA `.dae` loader (triangle meshes)
- OBJ / PLY / STL loaders (ASCII/Binary)
- Draco JSON loader for compact meshes
- KTX2 uncompressed RGBA textures
- EXR (RGB float, uncompressed)
- Texture compression (DRACO, KTX2)
- Size: ~500KB

**materia-postprocessing**

- Post-processing pipeline
- Effects (bloom, tone mapping, SSAO)
- Render passes
- Size: ~300KB

**materia-controls**

- Camera controls (Orbit, First Person, Fly)
- Input handling
- Touch and gamepad support
- Size: ~200KB

### Development Tools

**materia-validation**

- Production readiness checker
- Performance validation
- Cross-platform testing
- Constitutional compliance

**tools/editor**

- Visual scene editor
- Material editor
- Animation timeline

**tools/profiler**

- Performance profiling
- GPU metrics
- Memory tracking

## Cross-Platform Strategy

### Expect/Actual Pattern

Materia uses Kotlin's expect/actual mechanism for platform-specific code:

```kotlin
// Common code (expect declaration)
expect class Renderer {
    fun render(scene: Scene, camera: Camera)
    fun setSize(width: Int, height: Int)
}

// JVM implementation (actual)
actual class Renderer {
    private val vkRenderer = VulkanRenderer()

    actual fun render(scene: Scene, camera: Camera) {
        vkRenderer.render(scene, camera)
    }

    actual fun setSize(width: Int, height: Int) {
        vkRenderer.setSize(width, height)
    }
}

// Web implementation (actual)
actual class Renderer {
    private val webgpuRenderer = WebGPURenderer()

    actual fun render(scene: Scene, camera: Camera) {
        webgpuRenderer.render(scene, camera)
    }

    actual fun setSize(width: Int, height: Int) {
        webgpuRenderer.setSize(width, height)
    }
}
```

### Platform-Specific Optimizations

**JVM (Vulkan)**

- Direct Vulkan API via LWJGL bindings
- Native memory management with ByteBuffer
- Multi-threaded command buffer recording
- Validation layers in debug builds

**Web (WebGPU)**

- WebGPU API with WebGL2 fallback
- TypedArray for efficient data transfer
- SharedArrayBuffer for worker threads
- Canvas-based rendering

**Android (Vulkan)**

- Native Vulkan API
- Android Surface integration
- Hardware acceleration
- Power-efficient rendering

**iOS (MoltenVK)**

- Vulkan-to-Metal translation layer
- Metal performance shaders
- iOS-specific optimizations
- Touch input handling

## Rendering Pipeline

### Frame Rendering Flow

```
1. Scene Update
   ├─ Update transformations (Object3D.updateMatrixWorld)
   ├─ Update animations (AnimationMixer.update)
   ├─ Update physics (PhysicsWorld.step)
   └─ Update XR (XRSession.update)

2. Frustum Culling
   ├─ Extract camera frustum
   ├─ Test bounding volumes
   └─ Mark visible objects

3. Render List Construction
   ├─ Collect visible meshes
   ├─ Sort by material/depth
   └─ Batch draw calls

4. Shadow Pass (if enabled)
   ├─ Render from light perspective
   ├─ Generate shadow maps
   └─ Store depth textures

5. Main Render Pass
   ├─ Clear framebuffer
   ├─ Render opaque objects (front-to-back)
   ├─ Render transparent objects (back-to-front)
   └─ Render overlays

6. Post-Processing (if enabled)
   ├─ Render to framebuffer
   ├─ Apply effects (bloom, SSAO, etc.)
   └─ Tone mapping and color grading

7. Present
   ├─ Resolve multisampling
   ├─ Swap buffers
   └─ Submit to display
```

### Shader Management

Materia uses WGSL (WebGPU Shading Language) as the source shader language:

```
WGSL Source → Shader Compiler → Platform Shader
                                    ├─ SPIR-V (Vulkan)
                                    ├─ WGSL (WebGPU)
                                    └─ MSL (Metal/iOS)
```

Shader compilation happens at runtime with caching:

- First use: Compile and cache
- Subsequent uses: Load from cache
- Platform-specific optimizations applied

## Memory Management

### Object Lifecycle

```kotlin
// Creation
val geometry = BoxGeometry(1f, 1f, 1f)  // CPU data
val material = MeshStandardMaterial()   // CPU data
val mesh = Mesh(geometry, material)     // Scene graph node

// GPU Upload (lazy)
renderer.render(scene, camera)  // Uploads on first render

// Modification
mesh.geometry.attributes.position.needsUpdate = true

// Disposal
mesh.geometry.dispose()  // Free GPU memory
mesh.material.dispose()  // Free GPU textures/buffers
```

### Resource Pooling

```kotlin
// Vector pool for temporary calculations
private val vectorPool = ObjectPool { Vector3() }

fun computeNormal(a: Vector3, b: Vector3, c: Vector3): Vector3 {
    val v1 = vectorPool.acquire()
    val v2 = vectorPool.acquire()

    try {
        v1.subVectors(b, a)
        v2.subVectors(c, a)
        return v1.cross(v2).normalize()
    } finally {
        vectorPool.release(v1)
        vectorPool.release(v2)
    }
}
```

## Performance Optimization

### Dirty Flag System

```kotlin
class Object3D {
    private var matrixNeedsUpdate = true
    private var worldMatrixVersion = 0

    fun updateMatrixWorld(force: Boolean = false) {
        // Skip update if nothing changed
        if (!force && !matrixNeedsUpdate) return

        // Update only when needed
        if (matrixNeedsUpdate) {
            updateMatrix()
            matrixNeedsUpdate = false
            worldMatrixVersion++
        }

        // Propagate to children only if changed
        if (worldMatrixVersion != lastWorldMatrixVersion) {
            children.forEach { it.updateMatrixWorld() }
        }
    }
}
```

### Frustum Culling

```kotlin
fun render(scene: Scene, camera: Camera) {
    // Extract frustum from camera
    val frustum = Frustum.fromProjectionMatrix(camera.projectionMatrix)

    // Test objects against frustum
    scene.traverse { obj ->
        if (obj is Mesh) {
            val bounds = obj.geometry.boundingBox
            if (frustum.intersectsBox(bounds)) {
                renderList.add(obj)
            }
        }
    }
}
```

### Draw Call Batching

```kotlin
// Group objects by material
val batches = renderList.groupBy { it.material }

// Render in batches
batches.forEach { (material, meshes) ->
    bindMaterial(material)
    meshes.forEach { mesh ->
        setTransform(mesh.matrixWorld)
        draw(mesh.geometry)
    }
}
```

## Testing Strategy

### Unit Tests

- Math operations (Vector3, Matrix4, Quaternion)
- Scene graph operations (add, remove, traverse)
- Transformation calculations

### Integration Tests

- Renderer initialization across platforms
- Scene rendering consistency
- Material shader compilation

### Platform Tests

- JVM: Vulkan initialization and rendering
- Web: WebGPU/WebGL fallback
- Android: Vulkan surface creation
- iOS: MoltenVK compatibility

### Performance Tests

- 60 FPS validation with 100k triangles
- Memory usage under 5MB base
- Initialization time < 1s

### Visual Regression Tests

- Screenshot comparison across platforms
- Rendering consistency validation
- Material appearance verification

## Future Architecture

### Planned Improvements

1. **GPU-Driven Rendering**
    - Indirect draw calls
    - GPU culling and LOD selection
    - Compute shader-based particle systems

2. **Ray Tracing Support**
    - Hardware ray tracing (when available)
    - Hybrid rasterization/ray tracing
    - Real-time global illumination

3. **Streaming System**
    - Progressive asset loading
    - Virtual texturing
    - Geometry streaming

4. **Multi-Threading**
    - Parallel scene updates
    - Background asset loading
    - Worker thread support (Web)

## See Also

- [Rendering Pipeline](rendering-pipeline.md)
- [Cross-Platform Guide](cross-platform.md)
- [Performance Guide](performance.md)
- [API Reference](../api-reference/README.md)
