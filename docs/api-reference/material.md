# Material API Reference

The material module provides classes for defining surface appearance, from simple solid colors to physically-based rendering (PBR).

## Overview

```kotlin
import io.materia.material.*
```

---

## Material (Base Class)

Abstract base class for all materials.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `id` | `Int` | auto | Unique identifier |
| `uuid` | `String` | auto | UUID string |
| `name` | `String` | `""` | Optional name |
| `visible` | `Boolean` | `true` | Render this material |
| `transparent` | `Boolean` | `false` | Enable transparency |
| `opacity` | `Float` | `1.0` | Opacity (0-1) |
| `side` | `Side` | `Side.FRONT` | Which side(s) to render |
| `blending` | `Blending` | `NormalBlending` | Blend mode |
| `depthTest` | `Boolean` | `true` | Enable depth testing |
| `depthWrite` | `Boolean` | `true` | Write to depth buffer |
| `colorWrite` | `Boolean` | `true` | Write to color buffer |
| `alphaTest` | `Float` | `0.0` | Alpha discard threshold |
| `alphaToCoverage` | `Boolean` | `false` | Enable alpha to coverage |
| `polygonOffset` | `Boolean` | `false` | Enable polygon offset |
| `polygonOffsetFactor` | `Float` | `0.0` | Polygon offset factor |
| `polygonOffsetUnits` | `Float` | `0.0` | Polygon offset units |
| `wireframe` | `Boolean` | `false` | Render as wireframe |
| `wireframeLinewidth` | `Float` | `1.0` | Wireframe line width |
| `needsUpdate` | `Boolean` | `false` | Flag to recompile |

### Enums

```kotlin
enum class Side {
    FRONT,    // Render front faces only
    BACK,     // Render back faces only
    DOUBLE    // Render both sides
}

enum class Blending {
    NO,           // No blending
    NORMAL,       // Standard alpha blending
    ADDITIVE,     // Add colors together
    SUBTRACTIVE,  // Subtract colors
    MULTIPLY,     // Multiply colors
    CUSTOM        // Custom blend function
}
```

### Methods

```kotlin
// Clone material
fun clone(): Material

// Copy properties from another material
fun copy(source: Material): Material

// Dispose GPU resources
fun dispose()

// Mark for update
fun needsUpdate()
```

---

## MeshBasicMaterial

Simple unlit material, renders solid color or texture without lighting.

### Constructor

```kotlin
class MeshBasicMaterial(
    color: Color = Color.WHITE,
    map: Texture? = null
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Base color |
| `map` | `Texture?` | `null` | Color/diffuse texture |
| `alphaMap` | `Texture?` | `null` | Alpha texture |
| `envMap` | `Texture?` | `null` | Environment map |
| `envMapIntensity` | `Float` | `1.0` | Environment intensity |
| `reflectivity` | `Float` | `1.0` | Reflection amount |
| `combine` | `Combine` | `MULTIPLY` | How to combine with env map |

### Example

```kotlin
// Simple colored material
val redMaterial = MeshBasicMaterial().apply {
    color = Color(0xff0000)
}

// Textured material
val texturedMaterial = MeshBasicMaterial().apply {
    map = textureLoader.load("textures/crate.png")
}

// Transparent material
val glassMaterial = MeshBasicMaterial().apply {
    color = Color(0x88ccff)
    transparent = true
    opacity = 0.5f
}
```

---

## MeshLambertMaterial

Material using Lambertian (diffuse-only) shading. Good for non-shiny surfaces.

### Constructor

```kotlin
class MeshLambertMaterial(
    color: Color = Color.WHITE
)
```

### Properties

Inherits all from `MeshBasicMaterial` plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `emissive` | `Color` | `BLACK` | Emissive (glow) color |
| `emissiveIntensity` | `Float` | `1.0` | Emissive brightness |
| `emissiveMap` | `Texture?` | `null` | Emissive texture |

### Example

```kotlin
val matteMaterial = MeshLambertMaterial().apply {
    color = Color(0x44aa88)
    emissive = Color(0x111111)
}
```

---

## MeshPhongMaterial

Material using Blinn-Phong shading with specular highlights.

### Constructor

```kotlin
class MeshPhongMaterial(
    color: Color = Color.WHITE
)
```

### Properties

Inherits all from `MeshLambertMaterial` plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `specular` | `Color` | `0x111111` | Specular highlight color |
| `shininess` | `Float` | `30.0` | Specular exponent (0-1000) |
| `specularMap` | `Texture?` | `null` | Specular texture |
| `bumpMap` | `Texture?` | `null` | Bump map texture |
| `bumpScale` | `Float` | `1.0` | Bump map intensity |
| `normalMap` | `Texture?` | `null` | Normal map texture |
| `normalScale` | `Vector2` | `(1,1)` | Normal map intensity |
| `displacementMap` | `Texture?` | `null` | Displacement texture |
| `displacementScale` | `Float` | `1.0` | Displacement amount |
| `displacementBias` | `Float` | `0.0` | Displacement offset |

### Example

```kotlin
val shinyMaterial = MeshPhongMaterial().apply {
    color = Color(0x156289)
    specular = Color(0x222222)
    shininess = 100f
    normalMap = textureLoader.load("textures/brick_normal.jpg")
}
```

---

## MeshStandardMaterial

Physically-based rendering (PBR) material with metalness/roughness workflow.

### Constructor

```kotlin
class MeshStandardMaterial(
    color: Color = Color.WHITE
)
```

### Properties

Inherits common properties plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Albedo color |
| `map` | `Texture?` | `null` | Albedo texture |
| `roughness` | `Float` | `1.0` | Surface roughness (0-1) |
| `roughnessMap` | `Texture?` | `null` | Roughness texture |
| `metalness` | `Float` | `0.0` | Metallic factor (0-1) |
| `metalnessMap` | `Texture?` | `null` | Metalness texture |
| `normalMap` | `Texture?` | `null` | Normal map |
| `normalScale` | `Vector2` | `(1,1)` | Normal intensity |
| `aoMap` | `Texture?` | `null` | Ambient occlusion map |
| `aoMapIntensity` | `Float` | `1.0` | AO intensity |
| `emissive` | `Color` | `BLACK` | Emissive color |
| `emissiveIntensity` | `Float` | `1.0` | Emissive brightness |
| `emissiveMap` | `Texture?` | `null` | Emissive texture |
| `bumpMap` | `Texture?` | `null` | Bump map |
| `bumpScale` | `Float` | `1.0` | Bump intensity |
| `displacementMap` | `Texture?` | `null` | Displacement map |
| `displacementScale` | `Float` | `1.0` | Displacement amount |
| `displacementBias` | `Float` | `0.0` | Displacement offset |
| `envMap` | `Texture?` | `null` | Environment map |
| `envMapIntensity` | `Float` | `1.0` | Environment intensity |
| `flatShading` | `Boolean` | `false` | Use flat shading |

### Example

```kotlin
// Shiny metal
val metalMaterial = MeshStandardMaterial().apply {
    color = Color(0xffffff)
    metalness = 1.0f
    roughness = 0.2f
}

// Rough plastic
val plasticMaterial = MeshStandardMaterial().apply {
    color = Color(0xff0000)
    metalness = 0.0f
    roughness = 0.7f
}

// Full PBR with textures
val pbrMaterial = MeshStandardMaterial().apply {
    map = textureLoader.load("textures/metal_albedo.jpg")
    normalMap = textureLoader.load("textures/metal_normal.jpg")
    roughnessMap = textureLoader.load("textures/metal_roughness.jpg")
    metalnessMap = textureLoader.load("textures/metal_metalness.jpg")
    aoMap = textureLoader.load("textures/metal_ao.jpg")
}
```

---

## MeshPhysicalMaterial

Extended PBR material with advanced properties like clearcoat, transmission, and sheen.

### Constructor

```kotlin
class MeshPhysicalMaterial(
    color: Color = Color.WHITE
)
```

### Properties

Inherits all from `MeshStandardMaterial` plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `clearcoat` | `Float` | `0.0` | Clearcoat layer intensity |
| `clearcoatRoughness` | `Float` | `0.0` | Clearcoat roughness |
| `clearcoatMap` | `Texture?` | `null` | Clearcoat intensity map |
| `clearcoatRoughnessMap` | `Texture?` | `null` | Clearcoat roughness map |
| `clearcoatNormalMap` | `Texture?` | `null` | Clearcoat normal map |
| `clearcoatNormalScale` | `Vector2` | `(1,1)` | Clearcoat normal scale |
| `transmission` | `Float` | `0.0` | Light transmission (glass) |
| `transmissionMap` | `Texture?` | `null` | Transmission map |
| `thickness` | `Float` | `0.0` | Thickness for refraction |
| `thicknessMap` | `Texture?` | `null` | Thickness map |
| `attenuationColor` | `Color` | `WHITE` | Absorption color |
| `attenuationDistance` | `Float` | `Infinity` | Absorption distance |
| `ior` | `Float` | `1.5` | Index of refraction |
| `sheen` | `Float` | `0.0` | Sheen layer intensity |
| `sheenRoughness` | `Float` | `1.0` | Sheen roughness |
| `sheenColor` | `Color` | `BLACK` | Sheen tint |
| `sheenColorMap` | `Texture?` | `null` | Sheen color map |
| `sheenRoughnessMap` | `Texture?` | `null` | Sheen roughness map |
| `iridescence` | `Float` | `0.0` | Iridescence intensity |
| `iridescenceIOR` | `Float` | `1.3` | Iridescence IOR |
| `iridescenceThicknessRange` | `Pair<Float,Float>` | `(100,400)` | Thin-film range |
| `specularIntensity` | `Float` | `1.0` | Specular intensity |
| `specularColor` | `Color` | `WHITE` | Specular tint |

### Example

```kotlin
// Car paint with clearcoat
val carPaint = MeshPhysicalMaterial().apply {
    color = Color(0x880000)
    metalness = 0.9f
    roughness = 0.5f
    clearcoat = 1.0f
    clearcoatRoughness = 0.1f
}

// Glass
val glass = MeshPhysicalMaterial().apply {
    transmission = 1.0f
    roughness = 0.0f
    ior = 1.5f
    thickness = 0.5f
}

// Velvet fabric with sheen
val velvet = MeshPhysicalMaterial().apply {
    color = Color(0x880088)
    sheen = 1.0f
    sheenRoughness = 0.4f
    sheenColor = Color(0xff00ff)
}

// Soap bubble (iridescence)
val bubble = MeshPhysicalMaterial().apply {
    transmission = 1.0f
    iridescence = 1.0f
    iridescenceIOR = 1.3f
}
```

---

## ShaderMaterial

Material with custom vertex and fragment shaders.

### Constructor

```kotlin
class ShaderMaterial(
    vertexShader: String? = null,
    fragmentShader: String? = null,
    uniforms: MutableMap<String, Uniform> = mutableMapOf()
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `vertexShader` | `String?` | Custom vertex shader (WGSL/GLSL) |
| `fragmentShader` | `String?` | Custom fragment shader |
| `uniforms` | `Map<String, Uniform>` | Custom uniform values |
| `defines` | `Map<String, String>` | Preprocessor defines |
| `extensions` | `ShaderExtensions` | Required extensions |
| `lights` | `Boolean` | Include light uniforms |
| `fog` | `Boolean` | Include fog uniforms |

### Uniform Types

```kotlin
sealed class Uniform {
    data class Float(val value: kotlin.Float) : Uniform()
    data class Int(val value: kotlin.Int) : Uniform()
    data class Vector2(val value: io.materia.core.Vector2) : Uniform()
    data class Vector3(val value: io.materia.core.Vector3) : Uniform()
    data class Vector4(val value: io.materia.core.Vector4) : Uniform()
    data class Color(val value: io.materia.core.Color) : Uniform()
    data class Matrix3(val value: io.materia.core.Matrix3) : Uniform()
    data class Matrix4(val value: io.materia.core.Matrix4) : Uniform()
    data class Texture(val value: io.materia.texture.Texture?) : Uniform()
    data class FloatArray(val value: kotlin.FloatArray) : Uniform()
}
```

### Example

```kotlin
// Custom shader material
val customMaterial = ShaderMaterial(
    vertexShader = """
        struct Uniforms {
            modelViewProjection: mat4x4<f32>,
            time: f32,
        }
        @binding(0) @group(0) var<uniform> uniforms: Uniforms;
        
        struct VertexInput {
            @location(0) position: vec3<f32>,
            @location(1) uv: vec2<f32>,
        }
        
        struct VertexOutput {
            @builtin(position) position: vec4<f32>,
            @location(0) vUv: vec2<f32>,
        }
        
        @vertex
        fn main(input: VertexInput) -> VertexOutput {
            var output: VertexOutput;
            var pos = input.position;
            pos.y += sin(pos.x * 10.0 + uniforms.time) * 0.1;
            output.position = uniforms.modelViewProjection * vec4<f32>(pos, 1.0);
            output.vUv = input.uv;
            return output;
        }
    """,
    fragmentShader = """
        struct Uniforms {
            color: vec3<f32>,
        }
        @binding(1) @group(0) var<uniform> uniforms: Uniforms;
        
        @fragment
        fn main(@location(0) vUv: vec2<f32>) -> @location(0) vec4<f32> {
            return vec4<f32>(uniforms.color, 1.0);
        }
    """,
    uniforms = mutableMapOf(
        "time" to Uniform.Float(0f),
        "color" to Uniform.Color(Color(0x00ff00))
    )
)

// Update uniform in render loop
fun animate(time: Float) {
    customMaterial.uniforms["time"] = Uniform.Float(time)
}
```

---

## RawShaderMaterial

Like ShaderMaterial but without automatic uniforms injection.

```kotlin
class RawShaderMaterial(
    vertexShader: String,
    fragmentShader: String,
    uniforms: MutableMap<String, Uniform> = mutableMapOf()
)
```

---

## PointsMaterial

Material for rendering point clouds.

### Constructor

```kotlin
class PointsMaterial(
    color: Color = Color.WHITE,
    size: Float = 1f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Point color |
| `size` | `Float` | `1.0` | Point size in pixels |
| `sizeAttenuation` | `Boolean` | `true` | Scale with distance |
| `map` | `Texture?` | `null` | Sprite texture |
| `alphaMap` | `Texture?` | `null` | Alpha texture |

### Example

```kotlin
val pointsMaterial = PointsMaterial().apply {
    color = Color(0x88ccff)
    size = 0.05f
    sizeAttenuation = true
    transparent = true
    opacity = 0.8f
}

val points = Points(geometry, pointsMaterial)
```

---

## LineBasicMaterial

Material for rendering lines.

### Constructor

```kotlin
class LineBasicMaterial(
    color: Color = Color.WHITE
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Line color |
| `linewidth` | `Float` | `1.0` | Line width (limited support) |
| `linecap` | `String` | `"round"` | Line cap style |
| `linejoin` | `String` | `"round"` | Line join style |

---

## LineDashedMaterial

Material for dashed lines.

### Constructor

```kotlin
class LineDashedMaterial(
    color: Color = Color.WHITE
)
```

### Properties

Inherits from `LineBasicMaterial` plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dashSize` | `Float` | `3.0` | Dash length |
| `gapSize` | `Float` | `1.0` | Gap length |
| `scale` | `Float` | `1.0` | Pattern scale |

---

## SpriteMaterial

Material for sprites (always camera-facing billboards).

### Constructor

```kotlin
class SpriteMaterial(
    map: Texture? = null
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Tint color |
| `map` | `Texture?` | `null` | Sprite texture |
| `rotation` | `Float` | `0.0` | Rotation in radians |
| `sizeAttenuation` | `Boolean` | `true` | Scale with distance |

---

## ShadowMaterial

Transparent material that only receives shadows.

```kotlin
class ShadowMaterial(
    color: Color = Color.BLACK
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `BLACK` | Shadow color |

### Example

```kotlin
// Invisible ground that shows shadows
val shadowPlane = Mesh(
    PlaneGeometry(100f, 100f),
    ShadowMaterial().apply { opacity = 0.5f }
)
shadowPlane.receiveShadow = true
```

---

## See Also

- [Texture API](texture.md) - Loading and configuring textures
- [Geometry API](geometry.md) - Geometry for materials
- [Lighting API](lighting.md) - Lights that affect materials
- [Advanced: Custom Shaders](../advanced/custom-shaders.md) - Writing shaders
