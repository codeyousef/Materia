# Material System

The material system in Materia provides a unified, Three.js-style approach to defining surface appearance across all platforms. Materials encapsulate shader programs, textures, and render state configuration.

## Overview

Materia's material system is built on a composable architecture:

- **`EngineMaterial`** - Core interface that all materials implement
- **`BasicMaterial`** - Simple unlit material for solid colors and textures
- **`StandardMaterial`** - Full PBR material with metallic-roughness workflow

## EngineMaterial Interface

All materials implement the `EngineMaterial` interface:

```kotlin
import io.materia.engine.material.EngineMaterial

interface EngineMaterial : Disposable {
    val shaderId: String
    val transparent: Boolean
    val side: Side
    val depthTest: Boolean
    val depthWrite: Boolean
    val blending: Blending
    
    fun getUniforms(): Map<String, Any>
    fun bind(encoder: GPURenderPassEncoder)
}
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `shaderId` | `String` | - | Unique identifier for shader lookup |
| `transparent` | `Boolean` | `false` | Enable transparency rendering |
| `side` | `Side` | `Side.FRONT` | Face culling mode |
| `depthTest` | `Boolean` | `true` | Enable depth testing |
| `depthWrite` | `Boolean` | `true` | Write to depth buffer |
| `blending` | `Blending` | `Blending.NORMAL` | Blending mode for transparency |

## BasicMaterial

A simple, unlit material that renders objects with a solid color or texture. Ideal for UI elements, debugging, and stylized visuals.

```kotlin
import io.materia.engine.material.BasicMaterial
import io.materia.core.math.Color

// Create a basic red material
val redMaterial = BasicMaterial(
    color = Color(1f, 0f, 0f)
)

// With transparency
val transparentMaterial = BasicMaterial(
    color = Color(1f, 1f, 1f, 0.5f),
    transparent = true
)

// With texture (when supported)
val texturedMaterial = BasicMaterial(
    color = Color.WHITE,
    map = myTexture
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `Color.WHITE` | Base color of the material |
| `map` | `Texture?` | `null` | Color/diffuse texture |
| `opacity` | `Float` | `1.0f` | Overall opacity (0-1) |
| `alphaTest` | `Float` | `0.0f` | Alpha cutoff threshold |
| `wireframe` | `Boolean` | `false` | Render as wireframe |

### Example Usage

```kotlin
import io.materia.engine.material.BasicMaterial
import io.materia.engine.scene.EngineMesh
import io.materia.geometry.BufferGeometry
import io.materia.core.math.Color

// Create geometry
val geometry = BufferGeometry().apply {
    setAttribute("position", floatArrayOf(
        -1f, -1f, 0f,
         1f, -1f, 0f,
         0f,  1f, 0f
    ), 3)
}

// Create material
val material = BasicMaterial(color = Color(0.2f, 0.5f, 1.0f))

// Create mesh
val mesh = EngineMesh(geometry, material)
scene.add(mesh)
```

## StandardMaterial

A physically-based material using the metallic-roughness workflow. Provides realistic lighting with support for environment maps, normal mapping, and emission.

```kotlin
import io.materia.engine.material.StandardMaterial
import io.materia.core.math.Color

// Create a shiny metal material
val metalMaterial = StandardMaterial(
    color = Color(0.8f, 0.8f, 0.9f),
    metalness = 1.0f,
    roughness = 0.2f
)

// Create a rough plastic material
val plasticMaterial = StandardMaterial(
    color = Color(1.0f, 0.2f, 0.2f),
    metalness = 0.0f,
    roughness = 0.8f
)

// Create a glowing material
val glowingMaterial = StandardMaterial(
    color = Color(0.1f, 0.1f, 0.1f),
    emissive = Color(0f, 1f, 0.5f),
    emissiveIntensity = 2.0f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `Color.WHITE` | Base albedo color |
| `metalness` | `Float` | `0.0f` | Metallic factor (0-1) |
| `roughness` | `Float` | `1.0f` | Surface roughness (0-1) |
| `emissive` | `Color` | `Color.BLACK` | Emissive color |
| `emissiveIntensity` | `Float` | `1.0f` | Emissive strength multiplier |
| `normalScale` | `Float` | `1.0f` | Normal map intensity |
| `aoIntensity` | `Float` | `1.0f` | Ambient occlusion intensity |

### Texture Maps

| Property | Type | Description |
|----------|------|-------------|
| `map` | `Texture?` | Albedo/diffuse texture |
| `metalnessMap` | `Texture?` | Metalness texture (uses blue channel) |
| `roughnessMap` | `Texture?` | Roughness texture (uses green channel) |
| `normalMap` | `Texture?` | Tangent-space normal map |
| `aoMap` | `Texture?` | Ambient occlusion map |
| `emissiveMap` | `Texture?` | Emissive texture |
| `envMap` | `Texture?` | Environment/reflection map |

### PBR Example

```kotlin
import io.materia.engine.material.StandardMaterial
import io.materia.engine.scene.EngineMesh
import io.materia.geometry.primitives.SphereGeometry
import io.materia.core.math.Color

// Create a grid of materials with varying properties
val sphereGeometry = SphereGeometry(radius = 0.4f, segments = 32)

for (row in 0 until 5) {
    for (col in 0 until 5) {
        val material = StandardMaterial(
            color = Color(0.9f, 0.9f, 0.9f),
            metalness = col / 4f,  // 0 to 1 across columns
            roughness = row / 4f   // 0 to 1 across rows
        )
        
        val mesh = EngineMesh(sphereGeometry, material).apply {
            position.set(col - 2f, row - 2f, 0f)
        }
        scene.add(mesh)
    }
}
```

## Enumerations

### Side

Controls which faces of geometry are rendered:

```kotlin
enum class Side {
    FRONT,      // Render front faces only (default)
    BACK,       // Render back faces only
    DOUBLE      // Render both faces (no culling)
}
```

### Blending

Defines how materials blend with the background:

```kotlin
enum class Blending {
    NONE,           // No blending (opaque)
    NORMAL,         // Standard alpha blending
    ADDITIVE,       // Additive blending (good for glow effects)
    SUBTRACTIVE,    // Subtractive blending
    MULTIPLY        // Multiply blending
}
```

## Transparency

To enable transparency, set `transparent = true` and configure blending:

```kotlin
// Alpha transparency
val glassMaterial = BasicMaterial(
    color = Color(0.8f, 0.9f, 1.0f, 0.3f),
    transparent = true,
    blending = Blending.NORMAL,
    depthWrite = false  // Often needed for proper transparency
)

// Additive glow effect
val glowMaterial = BasicMaterial(
    color = Color(1f, 0.5f, 0f),
    transparent = true,
    blending = Blending.ADDITIVE
)
```

### Transparency Best Practices

1. **Sorting**: Transparent objects should be rendered back-to-front
2. **Depth Write**: Usually disable `depthWrite` for transparent materials
3. **Double-sided**: Consider using `side = Side.DOUBLE` for glass-like surfaces
4. **Premultiplied Alpha**: Some blending modes expect premultiplied alpha

## Custom Materials

Create custom materials by implementing `EngineMaterial`:

```kotlin
import io.materia.engine.material.EngineMaterial
import io.materia.engine.shader.ShaderLibrary

class GradientMaterial(
    val topColor: Color,
    val bottomColor: Color
) : EngineMaterial {
    
    override val shaderId = "gradient"
    override val transparent = false
    override val side = Side.FRONT
    override val depthTest = true
    override val depthWrite = true
    override val blending = Blending.NONE
    
    override fun getUniforms(): Map<String, Any> = mapOf(
        "topColor" to topColor.toVec4(),
        "bottomColor" to bottomColor.toVec4()
    )
    
    override fun bind(encoder: GPURenderPassEncoder) {
        // Bind uniform buffer
    }
    
    override fun dispose() {
        // Clean up GPU resources
    }
    
    companion object {
        init {
            // Register shader with ShaderLibrary
            ShaderLibrary.register("gradient", gradientShaderSource)
        }
    }
}
```

## ShaderLibrary Integration

Materials work with the `ShaderLibrary` to manage shader compilation:

```kotlin
import io.materia.engine.shader.ShaderLibrary

// Register a custom shader
ShaderLibrary.register("myShader", """
    @vertex
    fn vs_main(@location(0) position: vec3<f32>) -> @builtin(position) vec4<f32> {
        return vec4<f32>(position, 1.0);
    }
    
    @fragment
    fn fs_main() -> @location(0) vec4<f32> {
        return vec4<f32>(1.0, 0.0, 0.0, 1.0);
    }
""")

// Retrieve compiled shader
val shader = ShaderLibrary.get("myShader")
```

## Material Disposal

Materials hold GPU resources and must be disposed when no longer needed:

```kotlin
// Manual disposal
material.dispose()

// Using DisposableContainer
val container = DisposableContainer()
val material = BasicMaterial(color = Color.RED)
container.track(material)

// Later: disposes material and all tracked resources
container.dispose()
```

## Platform Considerations

### WebGPU (JS)

Materials compile WGSL shaders at runtime. First-time compilation may cause a brief stall.

```kotlin
// JS: Shaders are WGSL strings compiled on first use
val material = StandardMaterial(color = Color.BLUE)
```

### Vulkan (JVM)

Materials reference pre-compiled SPIR-V binaries from the resources directory.

```kotlin
// JVM: Shaders loaded from src/jvmMain/resources/shaders/
val material = StandardMaterial(color = Color.BLUE)
```

### Cross-Platform Usage

The API is identical across platforms:

```kotlin
// Works on both JS and JVM
val material = StandardMaterial(
    color = Color(0.8f, 0.2f, 0.3f),
    metalness = 0.5f,
    roughness = 0.4f
)

val mesh = EngineMesh(geometry, material)
scene.add(mesh)

// Cleanup
mesh.dispose()
material.dispose()
```

## Performance Tips

1. **Batch Materials**: Use the same material instance for multiple meshes when possible
2. **Limit Transparency**: Transparent objects are more expensive to render
3. **Texture Atlases**: Combine textures to reduce material switches
4. **LOD Materials**: Use simpler materials for distant objects
5. **Shader Caching**: ShaderLibrary caches compiled shaders automatically

## Migration from Legacy Materials

If migrating from older Materia material APIs:

| Legacy | Unified API |
|--------|-------------|
| `MeshBasicMaterial` | `BasicMaterial` |
| `MeshStandardMaterial` | `StandardMaterial` |
| `material.color = 0xFF0000` | `material.color = Color(1f, 0f, 0f)` |
| `material.needsUpdate = true` | Automatic uniform updates |

## See Also

- [Renderer](renderer.md) - WebGPU renderer configuration
- [Core](core.md) - Disposable and resource management
- [Geometry](geometry.md) - Geometry and BufferGeometry
