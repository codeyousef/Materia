# Lighting API Reference

Lights illuminate your 3D scenes. Materia provides various light types for different effects.

## Table of Contents

- [Light (Base Class)](#light-base-class)
- [Basic Lights](#basic-lights)
    - [AmbientLight](#ambientlight)
    - [HemisphereLight](#hemispherelight)
- [Direct Lights](#direct-lights)
    - [DirectionalLight](#directionallight)
    - [PointLight](#pointlight)
    - [SpotLight](#spotlight)
- [Area Lights](#area-lights)
    - [RectAreaLight](#rectarealight)
- [Advanced Lighting](#advanced-lighting)
    - [Light Probes](#light-probes)
    - [IBL (Image-Based Lighting)](#image-based-lighting)
- [Shadows](#shadows)
- [Light Helpers](#light-helpers)

---

## Light (Base Class)

Base class for all lights.

### Properties

```kotlin
var color: Color              // Light color
var intensity: Float          // Light intensity (default: 1.0)
```

### Methods

```kotlin
fun clone(): Light
fun copy(source: Light): Light
```

---

## Basic Lights

### AmbientLight

Global ambient lighting affecting all objects equally. No directionality or shadows.

```kotlin
val light = AmbientLight(
    color = Color(0x404040),  // Gray
    intensity = 0.5f
)
scene.add(light)
```

**Properties**:

```kotlin
var color: Color
var intensity: Float
```

**Use Cases**:

- Base lighting to prevent completely black shadows
- Simulating scattered light from sky/environment
- Quick lighting without complex setup

**Examples**:

```kotlin
// Dim ambient light
val ambient = AmbientLight(Color(0x404040), intensity = 0.3f)
scene.add(ambient)

// Colored ambient (sunset)
val warmAmbient = AmbientLight(Color(0xFF8844), intensity = 0.4f)

// No ambient (high contrast)
// Don't add ambient light for dramatic shadows
```

---

### HemisphereLight

Light from sky and ground, simulating outdoor lighting.

```kotlin
val light = HemisphereLight(
    skyColor = Color(0x87CEEB),    // Sky blue
    groundColor = Color(0x8B4513),  // Brown ground
    intensity = 1f
)
light.position.set(0f, 50f, 0f)
scene.add(light)
```

**Properties**:

```kotlin
var skyColor: Color           // Sky/top color
var groundColor: Color        // Ground/bottom color
var intensity: Float
```

**Examples**:

```kotlin
// Outdoor daytime
val daylight = HemisphereLight(
    skyColor = Color(0xFFFFFF),
    groundColor = Color(0x444444),
    intensity = 1f
)

// Sunset
val sunset = HemisphereLight(
    skyColor = Color(0xFF6B2E),
    groundColor = Color(0x2E1A0A),
    intensity = 0.8f
)

// Indoor (subtle)
val indoor = HemisphereLight(
    skyColor = Color(0xEEEEFF),
    groundColor = Color(0x887755),
    intensity = 0.6f
)
```

---

## Direct Lights

### DirectionalLight

Parallel rays like sunlight. Illuminates from a specific direction.

```kotlin
val light = DirectionalLight(
    color = Color(0xFFFFFF),
    intensity = 1f
)
light.position.set(5f, 10f, 7.5f)
light.castShadow = true
scene.add(light)
```

**Properties**:

```kotlin
var color: Color
var intensity: Float
var castShadow: Boolean
val shadow: DirectionalLightShadow  // Shadow configuration
val target: Object3D                // Light target (lookAt)
```

**Shadow Configuration**:

```kotlin
light.shadow.apply {
    mapSize.width = 2048
    mapSize.height = 2048
    camera.near = 0.5f
    camera.far = 500f
    camera.left = -50f
    camera.right = 50f
    camera.top = 50f
    camera.bottom = -50f
    bias = -0.0001f
}
```

**Examples**:

```kotlin
// Sunlight
val sun = DirectionalLight(Color(0xFFFFDD), intensity = 1f).apply {
    position.set(10f, 20f, 5f)
    castShadow = true

    shadow.mapSize.width = 4096
    shadow.mapSize.height = 4096
    shadow.camera.near = 1f
    shadow.camera.far = 100f
}

// Moonlight
val moon = DirectionalLight(Color(0x8888FF), intensity = 0.3f).apply {
    position.set(-10f, 15f, -5f)
}

// With specific target
val targetedLight = DirectionalLight(Color(0xFFFFFF)).apply {
    position.set(0f, 10f, 0f)
    target.position.set(5f, 0f, 5f)
}
scene.add(targetedLight)
scene.add(targetedLight.target)  // Must add target to scene
```

---

### PointLight

Omnidirectional light from a single point (like a lightbulb).

```kotlin
val light = PointLight(
    color = Color(0xFFFFFF),
    intensity = 1f,
    distance = 100f,  // Maximum range (0 = infinite)
    decay = 2f        // Light falloff
)
light.position.set(0f, 5f, 0f)
light.castShadow = true
scene.add(light)
```

**Properties**:

```kotlin
var color: Color
var intensity: Float
var distance: Float           // Range (0 = infinite)
var decay: Float              // Physically correct = 2
var castShadow: Boolean
val shadow: PointLightShadow
```

**Examples**:

```kotlin
// Ceiling lamp
val lamp = PointLight(Color(0xFFFFAA), intensity = 1f, distance = 20f).apply {
    position.set(0f, 5f, 0f)
    castShadow = true
}

// Campfire (animated)
val fire = PointLight(Color(0xFF6600), intensity = 2f, distance = 10f).apply {
    position.set(0f, 1f, 0f)
}

// Animation
fun animateFire(time: Float) {
    fire.intensity = 1.8f + sin(time * 5f) * 0.3f
    fire.position.y = 1f + sin(time * 3f) * 0.1f
}

// Torch
val torch = PointLight(Color(0xFFAA44), 1.5f, 15f, 2f).apply {
    position.set(0f, 3f, 0f)
    castShadow = true
    shadow.bias = 0.001f
}
```

---

### SpotLight

Conical light beam (like a flashlight or stage light).

```kotlin
val light = SpotLight(
    color = Color(0xFFFFFF),
    intensity = 1f,
    distance = 100f,
    angle = PI.toFloat() / 6f,  // Cone angle
    penumbra = 0.1f,             // Edge softness
    decay = 2f
)
light.position.set(0f, 10f, 0f)
light.castShadow = true
scene.add(light)

// Light points at target
light.target.position.set(0f, 0f, 0f)
scene.add(light.target)
```

**Properties**:

```kotlin
var color: Color
var intensity: Float
var distance: Float           // Range
var angle: Float              // Cone angle (radians)
var penumbra: Float           // Edge softness (0-1)
var decay: Float
var castShadow: Boolean
val shadow: SpotLightShadow
val target: Object3D          // Light direction
```

**Examples**:

```kotlin
// Stage spotlight
val spotlight = SpotLight(
    color = Color(0xFFFFFF),
    intensity = 2f,
    distance = 50f,
    angle = PI.toFloat() / 4f,
    penumbra = 0.2f
).apply {
    position.set(0f, 20f, 0f)
    target.position.set(0f, 0f, 0f)
    castShadow = true
}
scene.add(spotlight)
scene.add(spotlight.target)

// Flashlight
val flashlight = SpotLight(
    color = Color(0xFFFFFF),
    intensity = 1.5f,
    distance = 30f,
    angle = PI.toFloat() / 8f,
    penumbra = 0.1f,
    decay = 2f
).apply {
    castShadow = true
}

// Follow character
fun updateFlashlight(character: Object3D) {
    flashlight.position.copy(character.position)
    flashlight.target.position.copy(
        character.position.clone().add(character.getWorldDirection())
    )
}

// Car headlight
val headlight = SpotLight(Color(0xFFFFAA), 3f, 50f).apply {
    angle = PI.toFloat() / 6f
    penumbra = 0.3f
    castShadow = true
}
```

---

## Area Lights

### RectAreaLight

Rectangular area light (realistic soft lighting).

```kotlin
val light = RectAreaLight(
    color = Color(0xFFFFFF),
    intensity = 5f,
    width = 10f,
    height = 5f
)
light.position.set(0f, 5f, 0f)
light.lookAt(Vector3(0f, 0f, 0f))
scene.add(light)
```

**Properties**:

```kotlin
var color: Color
var intensity: Float
var width: Float              // Light width
var height: Float             // Light height
```

**Notes**:

- Does not cast shadows
- Only works with MeshStandardMaterial and MeshPhysicalMaterial
- Requires RectAreaLightUniformsLib

**Examples**:

```kotlin
// Softbox light (photography)
val softbox = RectAreaLight(Color(0xFFFFFF), intensity = 10f).apply {
    width = 5f
    height = 5f
    position.set(0f, 5f, 5f)
    lookAt(Vector3.ZERO)
}

// Window light
val window = RectAreaLight(Color(0x8888FF), intensity = 3f).apply {
    width = 3f
    height = 2f
    position.set(-5f, 2f, 0f)
    lookAt(Vector3(0f, 1f, 0f))
}

// TV screen
val tvLight = RectAreaLight(Color(0x4444FF), intensity = 2f).apply {
    width = 2f
    height = 1.2f
    position.set(0f, 1.5f, -3f)
    lookAt(Vector3(0f, 1f, 0f))
}
```

---

## Advanced Lighting

### Light Probes

Capture lighting information for realistic indirect lighting.

```kotlin
val probe = LightProbe()
probe.position.set(0f, 2f, 0f)
scene.add(probe)

// Generate from cubemap
val generator = LightProbeGenerator()
generator.fromCubeTexture(envMap, probe)
```

**Types**:

```kotlin
// Spherical harmonics probe
val shProbe = LightProbe()

// Ambient light probe
val ambientProbe = AmbientLightProbe(color = Color(0xFFFFFF), intensity = 1f)
```

**Examples**:

```kotlin
// Interior lighting with probes
val probes = listOf(
    LightProbe().apply { position.set(-5f, 2f, 0f) },
    LightProbe().apply { position.set(5f, 2f, 0f) },
    LightProbe().apply { position.set(0f, 2f, 5f) }
)

probes.forEach { probe ->
    generator.fromCubeTexture(envMap, probe)
    scene.add(probe)
}
```

---

### Image-Based Lighting

Use environment maps for realistic lighting.

```kotlin
// Load environment map
val envMap = CubeTextureLoader().load(arrayOf(
    "px.jpg", "nx.jpg",
    "py.jpg", "ny.jpg",
    "pz.jpg", "nz.jpg"
))

// Push prefiltered cube + BRDF LUT into the scene
val lighting = DefaultLightingSystem()
lighting.applyEnvironmentToScene(scene, envMap)

// Materials pick up IBL automatically
val material = MeshStandardMaterial().apply {
    envMapIntensity = 1f
}
```

**HDR Environment Maps**:

```kotlin
// Load HDR for better quality
val hdrLoader = HDRCubeTextureLoader()
val hdrEnvMap = hdrLoader.load("environment.hdr")

scene.background = Background.Texture(hdrEnvMap)

suspend fun configureEnvironment(scene: Scene) {
    val processor = IBLProcessorImpl()
    val config = IBLConfig(prefilterSize = 256, brdfLutSize = 512)
    processor.processEnvironmentForScene(hdrEnvMap, config, scene)
}
```

---

## Shadows

Configure shadow rendering for realistic lighting.

### Enabling Shadows

```kotlin
// Enable in renderer
renderer.shadowMap.enabled = true
renderer.shadowMap.type = ShadowMapType.PCFSoftShadowMap

// Enable for lights
directionalLight.castShadow = true
pointLight.castShadow = true
spotLight.castShadow = true

// Configure which objects cast/receive
mesh.castShadow = true
ground.receiveShadow = true
```

### Shadow Types

```kotlin
renderer.shadowMap.type = ShadowMapType.BasicShadowMap      // Fastest, hard edges
renderer.shadowMap.type = ShadowMapType.PCFShadowMap        // Filtered
renderer.shadowMap.type = ShadowMapType.PCFSoftShadowMap    // Soft edges (default)
renderer.shadowMap.type = ShadowMapType.VSMShadowMap        // Variance shadow map
```

### Shadow Configuration

```kotlin
directionalLight.shadow.apply {
    // Shadow map resolution
    mapSize.width = 2048
    mapSize.height = 2048

    // Shadow camera frustum
    camera.near = 0.5f
    camera.far = 500f
    camera.left = -10f
    camera.right = 10f
    camera.top = 10f
    camera.bottom = -10f

    // Shadow bias (prevent shadow acne)
    bias = -0.0001f
    normalBias = 0.01f

    // Shadow fade
    radius = 1f  // Blur radius
}
```

**Point Light Shadows** (6-sided cubemap):

```kotlin
pointLight.shadow.apply {
    mapSize.width = 1024
    mapSize.height = 1024
    camera.near = 0.5f
    camera.far = 100f
    bias = 0.001f
}
```

**Spot Light Shadows**:

```kotlin
spotLight.shadow.apply {
    mapSize.width = 1024
    mapSize.height = 1024
    camera.near = 0.5f
    camera.far = 50f
    camera.fov = spotLight.angle * 2f * 180f / PI.toFloat()
    bias = -0.0001f
}
```

---

## Light Helpers

Visualize light positions and directions (debug only).

```kotlin
// Directional light helper
val dirHelper = DirectionalLightHelper(directionalLight, size = 5f)
scene.add(dirHelper)

// Point light helper
val pointHelper = PointLightHelper(pointLight, sphereSize = 1f)
scene.add(pointHelper)

// Spot light helper
val spotHelper = SpotLightHelper(spotLight)
scene.add(spotHelper)

// Hemisphere light helper
val hemiHelper = HemisphereLightHelper(hemisphereLight, size = 5f)
scene.add(hemiHelper)

// RectArea light helper
val rectHelper = RectAreaLightHelper(rectAreaLight)
scene.add(rectHelper)

// Update helpers in animation loop
fun animate() {
    dirHelper.update()
    spotHelper.update()
    // ...
}
```

---

## Lighting Setups

### Three-Point Lighting

Classic photography setup.

```kotlin
// Key light (main)
val keyLight = DirectionalLight(Color(0xFFFFFF), intensity = 1f).apply {
    position.set(5f, 10f, 7.5f)
    castShadow = true
}

// Fill light (soften shadows)
val fillLight = DirectionalLight(Color(0x8888FF), intensity = 0.4f).apply {
    position.set(-5f, 5f, 2.5f)
}

// Back light (rim/separation)
val backLight = DirectionalLight(Color(0xFFFFFF), intensity = 0.6f).apply {
    position.set(0f, 5f, -10f)
}

scene.add(keyLight, fillLight, backLight)
```

### Outdoor Scene

```kotlin
// Sun
val sun = DirectionalLight(Color(0xFFFFDD), intensity = 1f).apply {
    position.set(10f, 20f, 5f)
    castShadow = true
    shadow.camera.far = 100f
}

// Sky light
val sky = HemisphereLight(
    skyColor = Color(0x87CEEB),
    groundColor = Color(0x8B7355),
    intensity = 0.6f
)

// Ambient bounce
val ambient = AmbientLight(Color(0x404060), intensity = 0.3f)

scene.add(sun, sky, ambient)
```

### Indoor Scene

```kotlin
// Ceiling lights
val ceiling1 = PointLight(Color(0xFFFFAA), 1f, 20f).apply {
    position.set(-5f, 5f, 0f)
    castShadow = true
}

val ceiling2 = PointLight(Color(0xFFFFAA), 1f, 20f).apply {
    position.set(5f, 5f, 0f)
    castShadow = true
}

// Window light
val window = RectAreaLight(Color(0x8888FF), 3f, 4f, 3f).apply {
    position.set(-10f, 3f, 0f)
    lookAt(Vector3(0f, 1f, 0f))
}

// Ambient
val ambient = AmbientLight(Color(0x404040), 0.2f)

scene.add(ceiling1, ceiling2, window, ambient)
```

### Night Scene

```kotlin
// Moonlight
val moon = DirectionalLight(Color(0x4444FF), intensity = 0.3f).apply {
    position.set(-10f, 15f, -5f)
    castShadow = true
}

// Street lights
fun createStreetLight(x: Float, z: Float): PointLight {
    return PointLight(Color(0xFFAA44), 2f, 15f).apply {
        position.set(x, 5f, z)
        castShadow = true
    }
}

val streetLights = listOf(
    createStreetLight(-10f, -10f),
    createStreetLight(-10f, 10f),
    createStreetLight(10f, -10f),
    createStreetLight(10f, 10f)
)

// Dark ambient
val ambient = AmbientLight(Color(0x101020), 0.1f)

scene.add(moon, ambient, *streetLights.toTypedArray())
```

---

## Performance Tips

1. **Minimize shadow-casting lights**: Shadows are expensive
2. **Optimize shadow map size**: Balance quality vs performance
3. **Use baked lighting**: Pre-compute static lighting
4. **Limit light count**: Fewer lights = better performance
5. **Use light helpers**: Debug light placement

---

## Common Patterns

### Animated Lights

```kotlin
var time = 0f

fun animate(deltaTime: Float) {
    time += deltaTime

    // Flickering fire
    fireLight.intensity = 1.5f + sin(time * 10f) * 0.3f

    // Pulsing
    pulseLight.intensity = 1f + sin(time * 2f) * 0.5f

    // Moving light
    movingLight.position.x = sin(time) * 5f
    movingLight.position.z = cos(time) * 5f
}
```

### Day/Night Cycle

```kotlin
var timeOfDay = 0f  // 0-1 (0 = midnight, 0.5 = noon)

fun updateDayNight(delta: Float) {
    timeOfDay = (timeOfDay + delta * 0.01f) % 1f

    // Sun angle
    val angle = timeOfDay * PI.toFloat() * 2f
    sun.position.set(
        sin(angle) * 50f,
        cos(angle) * 50f,
        0f
    )

    // Sun intensity
    val dayness = cos(angle).coerceIn(0f, 1f)
    sun.intensity = dayness
    sun.color = Color.lerp(
        Color(0xFF6600),  // Sunrise/sunset
        Color(0xFFFFFF),  // Noon
        dayness
    )

    // Ambient
    ambient.intensity = 0.2f + dayness * 0.3f
}
```

---

## See Also

- [Lighting Source](../../../src/commonMain/kotlin/io/materia/lighting/)
- [Materials](../material/materials.md)
- [Shadows Guide](../../guides/shadows-guide.md)
- [PBR Guide](../../guides/pbr-guide.md)
