# Material API Reference

Materials define how objects appear when rendered. Materia provides a comprehensive material system
compatible with
Three.js patterns.

## Table of Contents

- [Material (Base Class)](#material-base-class)
- [Basic Materials](#basic-materials)
    - [MeshBasicMaterial](#meshbasicmaterial)
    - [MeshNormalMaterial](#meshnormalmaterial)
    - [MeshDepthMaterial](#meshdepthmaterial)
- [Shaded Materials](#shaded-materials)
    - [MeshLambertMaterial](#meshlambertmaterial)
    - [MeshPhongMaterial](#meshphongmaterial)
    - [MeshToonMaterial](#meshtoonmaterial)
- [PBR Materials](#pbr-materials)
    - [MeshStandardMaterial](#meshstandardmaterial)
    - [MeshPhysicalMaterial](#meshphysicalmaterial)
- [Special Materials](#special-materials)
    - [ShaderMaterial](#shadermaterial)
    - [RawShaderMaterial](#rawshadermaterial)
    - [LineMaterial](#linematerial)
    - [PointsMaterial](#pointsmaterial)
- [Material Properties](#material-properties)
- [Textures](#textures)

---

## Material (Base Class)

Abstract base class for all materials.

### Common Properties

```kotlin
val id: Int                   // Unique ID
val uuid: String              // UUID
var name: String              // Material name
var needsUpdate: Boolean      // Dirty flag
var visible: Boolean          // Render material?

// Rendering
var opacity: Float            // 0.0 - 1.0 (default: 1.0)
var transparent: Boolean      // Enable transparency
var side: Side                // Which faces to render
var vertexColors: Boolean     // Use vertex colors
var depthWrite: Boolean       // Write to depth buffer
var depthTest: Boolean        // Enable depth testing
var depthFunc: DepthMode      // Depth comparison function

// Blending
var blending: Blending
var blendSrc: BlendingFactor
var blendDst: BlendingFactor
var blendEquation: BlendingEquation
var premultipliedAlpha: Boolean

// Stencil
var stencilWrite: Boolean
var stencilFunc: StencilFunc
var stencilRef: Int
var stencilWriteMask: Int
var stencilFuncMask: Int
var stencilFail: StencilOp
var stencilZFail: StencilOp
var stencilZPass: StencilOp

// Other
var alphaTest: Float          // Alpha cutoff threshold
var alphaToCoverage: Boolean  // Use alpha to coverage
var polygonOffset: Boolean    // Enable polygon offset
var polygonOffsetFactor: Float
var polygonOffsetUnits: Float
var dithering: Boolean        // Enable dithering
var toneMapped: Boolean       // Apply tone mapping
var precision: Precision?     // Shader precision

// Clipping planes
var clippingPlanes: List<Plane>?
var clipIntersection: Boolean
var clipShadows: Boolean

// User data
val userData: MutableMap<String, Any>
```

### Side Enum

```kotlin
enum class Side {
    FrontSide,   // Render front faces only (default)
    BackSide,    // Render back faces only
    DoubleSide   // Render both sides
}
```

### Methods

```kotlin
// Clone and copy
abstract fun clone(): Material
open fun copy(source: Material): Material

// Lifecycle
open fun dispose()

// Properties
open fun setValues(values: Map<String, Any>)
```

---

## Basic Materials

### MeshBasicMaterial

Unlit material with solid color or texture. Fastest material, no lighting calculations.

```kotlin
val material = MeshBasicMaterial().apply {
    color = Color(0xFF0000)  // Red
}
```

**Properties**:

```kotlin
var color: Color              // Base color (default: white)
var map: Texture?             // Diffuse texture
var alphaMap: Texture?        // Alpha texture
var aoMap: Texture?           // Ambient occlusion map
var aoMapIntensity: Float     // AO intensity
var lightMap: Texture?        // Baked lighting
var lightMapIntensity: Float
var specularMap: Texture?     // Specular highlights
var envMap: CubeTexture?      // Environment map
var combine: Combine          // How to combine env map
var reflectivity: Float       // Environment reflectivity
var refractionRatio: Float    // Refraction index
var wireframe: Boolean        // Wireframe rendering
var wireframeLinewidth: Float
```

**Examples**:

```kotlin
// Solid color
val solid = MeshBasicMaterial().apply {
    color = Color(0x00FF00)
}

// Textured
val textured = MeshBasicMaterial().apply {
    map = textureLoader.load("texture.png")
}

// Transparent
val transparent = MeshBasicMaterial().apply {
    color = Color(0xFF0000)
    transparent = true
    opacity = 0.5f
}

// Wireframe
val wireframe = MeshBasicMaterial().apply {
    color = Color(0xFFFFFF)
    wireframe = true
    wireframeLinewidth = 2f
}
```

---

### MeshNormalMaterial

Material that maps normals to RGB colors. Useful for debugging.

```kotlin
val material = MeshNormalMaterial()
```

**Properties**:

```kotlin
var flatShading: Boolean      // Use flat shading
var wireframe: Boolean
var wireframeLinewidth: Float
```

**Example**:

```kotlin
val normalMat = MeshNormalMaterial().apply {
    flatShading = false  // Smooth normals
}
```

---

### MeshDepthMaterial

Material that renders depth information.

```kotlin
val material = MeshDepthMaterial()
```

**Properties**:

```kotlin
var depthPacking: DepthPacking  // How to pack depth
var displacementMap: Texture?
var displacementScale: Float
var displacementBias: Float
var wireframe: Boolean
```

---

## Shaded Materials

### MeshLambertMaterial

Diffuse (non-shiny) material with lighting.

```kotlin
val material = MeshLambertMaterial().apply {
    color = Color(0x00FF00)
}
```

**Properties**:

```kotlin
var color: Color
var emissive: Color           // Emissive color
var emissiveIntensity: Float
var emissiveMap: Texture?
var map: Texture?
var lightMap: Texture?
var lightMapIntensity: Float
var aoMap: Texture?
var aoMapIntensity: Float
var specularMap: Texture?
var alphaMap: Texture?
var envMap: CubeTexture?
var combine: Combine
var reflectivity: Float
var refractionRatio: Float
var wireframe: Boolean
var flatShading: Boolean
```

**Examples**:

```kotlin
// Simple diffuse
val diffuse = MeshLambertMaterial().apply {
    color = Color(0x44AA88)
}

// With emissive glow
val glowing = MeshLambertMaterial().apply {
    color = Color(0x333333)
    emissive = Color(0xFF0000)
    emissiveIntensity = 0.5f
}
```

---

### MeshPhongMaterial

Shiny material with specular highlights.

```kotlin
val material = MeshPhongMaterial().apply {
    color = Color(0xFF0000)
    specular = Color(0xFFFFFF)
    shininess = 100f
}
```

**Properties**:

```kotlin
var color: Color
var specular: Color           // Specular highlight color
var shininess: Float          // Shininess (0-1000+)
var emissive: Color
var emissiveIntensity: Float
var emissiveMap: Texture?
var bumpMap: Texture?
var bumpScale: Float
var normalMap: Texture?
var normalMapType: NormalMapType
var normalScale: Vector2
var displacementMap: Texture?
var displacementScale: Float
var displacementBias: Float
var map: Texture?
var lightMap: Texture?
var aoMap: Texture?
var specularMap: Texture?
var alphaMap: Texture?
var envMap: CubeTexture?
var combine: Combine
var reflectivity: Float
var refractionRatio: Float
var wireframe: Boolean
var flatShading: Boolean
```

**Examples**:

```kotlin
// Shiny plastic
val plastic = MeshPhongMaterial().apply {
    color = Color(0xFF0000)
    specular = Color(0xFFFFFF)
    shininess = 100f
}

// Metal-like
val metal = MeshPhongMaterial().apply {
    color = Color(0x888888)
    specular = Color(0xFFFFFF)
    shininess = 1000f
}

// With normal map
val bumpy = MeshPhongMaterial().apply {
    color = Color(0x8080FF)
    normalMap = normalTexture
    normalScale = Vector2(1f, 1f)
}
```

---

### MeshToonMaterial

Cartoon-style shading with discrete color steps.

```kotlin
val material = MeshToonMaterial().apply {
    color = Color(0x00FF00)
}
```

**Properties**:

```kotlin
var color: Color
var gradientMap: Texture?     // Toon gradient ramp
var map: Texture?
var lightMap: Texture?
var aoMap: Texture?
var emissive: Color
var emissiveIntensity: Float
var emissiveMap: Texture?
var bumpMap: Texture?
var normalMap: Texture?
var displacementMap: Texture?
var alphaMap: Texture?
var wireframe: Boolean
```

**Example**:

```kotlin
// Cel-shaded look
val toon = MeshToonMaterial().apply {
    color = Color(0xFF6B00)
    gradientMap = toonGradient  // 3-5 color gradient
}
```

---

## PBR Materials

### MeshStandardMaterial

Physically-based rendering (PBR) material with metalness/roughness workflow.

```kotlin
val material = MeshStandardMaterial().apply {
    color = Color(0xFF0000)
    metalness = 0.5f
    roughness = 0.5f
}
```

**Properties**:

```kotlin
var color: Color
var roughness: Float          // 0.0 (smooth) to 1.0 (rough)
var metalness: Float          // 0.0 (non-metal) to 1.0 (metal)
var map: Texture?
var lightMap: Texture?
var lightMapIntensity: Float
var aoMap: Texture?
var aoMapIntensity: Float
var emissive: Color
var emissiveIntensity: Float
var emissiveMap: Texture?
var bumpMap: Texture?
var bumpScale: Float
var normalMap: Texture?
var normalMapType: NormalMapType
var normalScale: Vector2
var displacementMap: Texture?
var displacementScale: Float
var displacementBias: Float
var roughnessMap: Texture?
var metalnessMap: Texture?
var alphaMap: Texture?
var envMap: CubeTexture?
var envMapIntensity: Float
var wireframe: Boolean
var flatShading: Boolean
```

**Examples**:

```kotlin
// Gold
val gold = MeshStandardMaterial().apply {
    color = Color(0xFFD700)
    metalness = 1f
    roughness = 0.3f
}

// Wood
val wood = MeshStandardMaterial().apply {
    map = woodTexture
    normalMap = woodNormalMap
    roughnessMap = woodRoughnessMap
    metalness = 0f
    roughness = 0.8f
}

// Wet plastic
val wetPlastic = MeshStandardMaterial().apply {
    color = Color(0xFF0000)
    metalness = 0f
    roughness = 0.1f
}
```

---

### MeshPhysicalMaterial

Advanced PBR with clearcoat, transmission, and other physical effects.

```kotlin
val material = MeshPhysicalMaterial().apply {
    color = Color(0xFFFFFF)
    metalness = 0f
    roughness = 0.1f
    clearcoat = 1f
    clearcoatRoughness = 0.1f
}
```

**Additional Properties** (extends MeshStandardMaterial):

```kotlin
var clearcoat: Float          // Clearcoat layer (0-1)
var clearcoatRoughness: Float
var clearcoatMap: Texture?
var clearcoatRoughnessMap: Texture?
var clearcoatNormalMap: Texture?
var clearcoatNormalScale: Vector2

var transmission: Float       // Light transmission (0-1)
var transmissionMap: Texture?
var thickness: Float          // Object thickness
var thicknessMap: Texture?
var attenuationDistance: Float
var attenuationColor: Color

var sheen: Float              // Fabric sheen (0-1)
var sheenRoughness: Float
var sheenColor: Color
var sheenColorMap: Texture?
var sheenRoughnessMap: Texture?

var specularIntensity: Float  // Non-metallic specular
var specularColor: Color
var specularIntensityMap: Texture?
var specularColorMap: Texture?

var ior: Float                // Index of refraction (1.0-2.333)
var reflectivity: Float
```

**Examples**:

```kotlin
// Car paint
val carPaint = MeshPhysicalMaterial().apply {
    color = Color(0xFF0000)
    metalness = 0.9f
    roughness = 0.2f
    clearcoat = 1f
    clearcoatRoughness = 0.03f
}

// Glass
val glass = MeshPhysicalMaterial().apply {
    color = Color(0xFFFFFF)
    metalness = 0f
    roughness = 0f
    transmission = 1f
    thickness = 0.5f
    ior = 1.5f
}

// Velvet fabric
val velvet = MeshPhysicalMaterial().apply {
    color = Color(0x4A0E4E)
    roughness = 1f
    sheen = 1f
    sheenColor = Color(0xFF88FF)
    sheenRoughness = 0.5f
}

// Frosted glass
val frosted = MeshPhysicalMaterial().apply {
    color = Color(0xFFFFFF)
    metalness = 0f
    roughness = 0.3f
    transmission = 0.9f
    thickness = 0.2f
}
```

---

## Special Materials

### ShaderMaterial

Custom shader material with full control over rendering.

```kotlin
val material = ShaderMaterial(
    vertexShader = """
        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        attribute vec3 position;

        void main() {
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
    """,
    fragmentShader = """
        uniform vec3 color;

        void main() {
            gl_FragColor = vec4(color, 1.0);
        }
    """,
    uniforms = mapOf(
        "color" to Uniform(Color(1f, 0f, 0f))
    )
)
```

**Properties**:

```kotlin
var vertexShader: String
var fragmentShader: String
val uniforms: MutableMap<String, Uniform>
val defines: MutableMap<String, Any>
var wireframe: Boolean
var wireframeLinewidth: Float
var lights: Boolean           // Use scene lights
var fog: Boolean              // Use scene fog
```

**Example - Animated Shader**:

```kotlin
val material = ShaderMaterial(
    vertexShader = """
        uniform float time;
        attribute vec3 position;
        varying vec3 vPosition;

        void main() {
            vPosition = position;
            vec3 pos = position;
            pos.y += sin(pos.x * 10.0 + time) * 0.1;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
        }
    """,
    fragmentShader = """
        uniform float time;
        varying vec3 vPosition;

        void main() {
            vec3 color = vec3(
                0.5 + 0.5 * sin(vPosition.x + time),
                0.5 + 0.5 * sin(vPosition.y + time * 1.3),
                0.5 + 0.5 * sin(vPosition.z + time * 0.7)
            );
            gl_FragColor = vec4(color, 1.0);
        }
    """,
    uniforms = mapOf(
        "time" to Uniform(0f)
    )
)

// Update in animation loop
fun animate(deltaTime: Float) {
    val timeUniform = material.uniforms["time"]!!
    timeUniform.value = (timeUniform.value as Float) + deltaTime
}
```

---

### RawShaderMaterial

Like ShaderMaterial but without built-in uniforms/attributes.

```kotlin
val material = RawShaderMaterial(
    vertexShader = fullVertexShader,
    fragmentShader = fullFragmentShader,
    uniforms = customUniforms
)
```

Use when you need complete control over all shader code.

---

### LineMaterial

Material for Line objects.

```kotlin
val material = LineMaterial().apply {
    color = Color(0xFF0000)
    linewidth = 2f
}
```

**Properties**:

```kotlin
var color: Color
var linewidth: Float
var vertexColors: Boolean
var dashed: Boolean
var dashSize: Float
var gapSize: Float
var dashScale: Float
```

---

### PointsMaterial

Material for point clouds.

```kotlin
val material = PointsMaterial().apply {
    color = Color(0xFF0000)
    size = 2f
}
```

**Properties**:

```kotlin
var color: Color
var map: Texture?
var alphaMap: Texture?
var size: Float               // Point size in pixels
var sizeAttenuation: Boolean  // Scale with distance
var vertexColors: Boolean
```

---

## Material Properties

### Texture Maps

```kotlin
// Diffuse/Albedo
material.map = textureLoader.load("diffuse.png")

// Normal map (surface detail)
material.normalMap = textureLoader.load("normal.png")
material.normalScale = Vector2(1f, 1f)

// Roughness (PBR)
material.roughnessMap = textureLoader.load("roughness.png")

// Metalness (PBR)
material.metalnessMap = textureLoader.load("metalness.png")

// Ambient Occlusion (shadows in crevices)
material.aoMap = textureLoader.load("ao.png")
material.aoMapIntensity = 1f

// Emissive (glow)
material.emissiveMap = textureLoader.load("emissive.png")
material.emissiveIntensity = 1f

// Alpha (transparency)
material.alphaMap = textureLoader.load("alpha.png")

// Displacement (actual geometry displacement)
material.displacementMap = textureLoader.load("height.png")
material.displacementScale = 0.1f

// Environment (reflections)
material.envMap = cubeTexture
material.envMapIntensity = 1f
```

### Blending Modes

```kotlin
// Normal blending (default)
material.blending = Blending.NormalBlending

// Additive (glow effects)
material.blending = Blending.AdditiveBlending

// Subtractive
material.blending = Blending.SubtractiveBlending

// Multiply
material.blending = Blending.MultiplyBlending

// Custom
material.blending = Blending.CustomBlending
material.blendEquation = BlendingEquation.AddEquation
material.blendSrc = BlendingFactor.SrcAlphaFactor
material.blendDst = BlendingFactor.OneMinusSrcAlphaFactor
```

### Transparency

```kotlin
// Enable transparency
material.transparent = true
material.opacity = 0.5f

// Alpha test (cutout)
material.alphaTest = 0.5f  // Discard pixels below 0.5 alpha

// Premultiplied alpha
material.premultipliedAlpha = true
```

### Depth Testing

```kotlin
// Disable depth write (for transparent objects)
material.depthWrite = false

// Disable depth test
material.depthTest = false

// Change depth function
material.depthFunc = DepthMode.LessEqualDepth
```

---

## Performance Tips

1. **Reuse materials**: Share material instances
2. **Simple materials first**: Use MeshBasicMaterial for testing
3. **Optimize textures**: Use appropriate sizes and formats
4. **Disable unused features**: Set properties to null/false
5. **Batch by material**: Group objects with same material

---

## Common Patterns

### Material Swapping

```kotlin
val materials = listOf(
    MeshBasicMaterial().apply { color = Color(0xFF0000) },
    MeshPhongMaterial().apply { color = Color(0x00FF00) },
    MeshStandardMaterial().apply { color = Color(0x0000FF) }
)

var currentMaterial = 0
mesh.material = materials[currentMaterial]

// Swap on click
fun nextMaterial() {
    currentMaterial = (currentMaterial + 1) % materials.size
    mesh.material = materials[currentMaterial]
}
```

### Animated Uniforms

```kotlin
val time = Uniform(0f)
val material = ShaderMaterial(
    vertexShader = vertexCode,
    fragmentShader = fragmentCode,
    uniforms = mapOf("time" to time)
)

fun animate(deltaTime: Float) {
    time.value = (time.value as Float) + deltaTime
}
```

---

## See Also

- [Material Source](../../../src/commonMain/kotlin/io/materia/material/Material.kt)
- [Textures](../texture/textures.md)
- [Shaders](shader-programming.md)
- [Lighting](../lights/lighting.md)
