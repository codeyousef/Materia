# Lighting API Reference

The lighting module provides various light types for illuminating 3D scenes.

## Overview

```kotlin
import io.materia.light.*
```

---

## Light (Base Class)

Abstract base class for all lights.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Light color |
| `intensity` | `Float` | `1.0` | Light intensity |

### Methods

```kotlin
// Copy light properties
fun copy(source: Light): Light

// Clone light
fun clone(): Light

// Dispose resources
fun dispose()
```

---

## AmbientLight

Uniform light that illuminates all objects equally from all directions.

### Constructor

```kotlin
class AmbientLight(
    color: Color = Color.WHITE,
    intensity: Float = 1f
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `color` | `Color` | Light color |
| `intensity` | `Float` | Light intensity |

### Example

```kotlin
// Add ambient light for base illumination
val ambient = AmbientLight(Color(0x404040), 0.4f)
scene.add(ambient)
```

---

## DirectionalLight

Light that emits parallel rays in a specific direction (like sunlight).

### Constructor

```kotlin
class DirectionalLight(
    color: Color = Color.WHITE,
    intensity: Float = 1f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Light color |
| `intensity` | `Float` | `1.0` | Light intensity |
| `target` | `Object3D` | auto | Look-at target |
| `shadow` | `DirectionalLightShadow` | auto | Shadow settings |
| `castShadow` | `Boolean` | `false` | Enable shadows |

### Shadow Properties

```kotlin
class DirectionalLightShadow {
    var mapSize: Vector2 = Vector2(512, 512)  // Shadow map resolution
    var camera: OrthographicCamera            // Shadow camera
    var bias: Float = 0f                      // Shadow bias
    var normalBias: Float = 0f                // Normal-based bias
    var radius: Float = 1f                    // PCF radius
    var blurSamples: Int = 8                  // Blur samples
}
```

### Example

```kotlin
// Main sun light
val sun = DirectionalLight(Color(0xffffff), 1.0f)
sun.position.set(5f, 10f, 7.5f)
scene.add(sun)

// With shadows
sun.castShadow = true
sun.shadow.mapSize.set(2048f, 2048f)
sun.shadow.camera.near = 0.5f
sun.shadow.camera.far = 50f
sun.shadow.camera.left = -10f
sun.shadow.camera.right = 10f
sun.shadow.camera.top = 10f
sun.shadow.camera.bottom = -10f
sun.shadow.bias = -0.0001f

// Change direction
sun.target.position.set(0f, 0f, 0f)
scene.add(sun.target)
```

---

## PointLight

Omnidirectional light emitting from a single point.

### Constructor

```kotlin
class PointLight(
    color: Color = Color.WHITE,
    intensity: Float = 1f,
    distance: Float = 0f,
    decay: Float = 2f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Light color |
| `intensity` | `Float` | `1.0` | Light intensity |
| `distance` | `Float` | `0` | Maximum range (0 = infinite) |
| `decay` | `Float` | `2` | Decay rate (physically correct = 2) |
| `shadow` | `PointLightShadow` | auto | Shadow settings |
| `castShadow` | `Boolean` | `false` | Enable shadows |

### Shadow Properties

```kotlin
class PointLightShadow {
    var mapSize: Vector2 = Vector2(512, 512)
    var camera: PerspectiveCamera  // Shadow camera (90° FOV)
    var bias: Float = 0f
    var normalBias: Float = 0f
    var radius: Float = 1f
}
```

### Example

```kotlin
// Lamp light
val lamp = PointLight(Color(0xffaa00), 1.5f, 10f, 2f)
lamp.position.set(0f, 3f, 0f)
scene.add(lamp)

// With shadows (expensive - renders 6 shadow maps)
lamp.castShadow = true
lamp.shadow.mapSize.set(1024f, 1024f)
lamp.shadow.camera.near = 0.1f
lamp.shadow.camera.far = 15f

// Animate
fun animate(time: Float) {
    lamp.position.x = sin(time) * 3f
    lamp.position.z = cos(time) * 3f
}
```

---

## SpotLight

Cone-shaped light emitting from a point in a direction.

### Constructor

```kotlin
class SpotLight(
    color: Color = Color.WHITE,
    intensity: Float = 1f,
    distance: Float = 0f,
    angle: Float = PI / 3,
    penumbra: Float = 0f,
    decay: Float = 2f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Light color |
| `intensity` | `Float` | `1.0` | Light intensity |
| `distance` | `Float` | `0` | Maximum range |
| `angle` | `Float` | `PI/3` | Cone angle (radians, max PI/2) |
| `penumbra` | `Float` | `0` | Soft edge (0-1) |
| `decay` | `Float` | `2` | Decay rate |
| `target` | `Object3D` | auto | Look-at target |
| `shadow` | `SpotLightShadow` | auto | Shadow settings |
| `castShadow` | `Boolean` | `false` | Enable shadows |
| `map` | `Texture?` | `null` | Projector texture |

### Shadow Properties

```kotlin
class SpotLightShadow {
    var mapSize: Vector2 = Vector2(512, 512)
    var camera: PerspectiveCamera
    var bias: Float = 0f
    var normalBias: Float = 0f
    var radius: Float = 1f
    var focus: Float = 1f  // Shadow camera focus
}
```

### Example

```kotlin
// Flashlight
val flashlight = SpotLight(
    color = Color.WHITE,
    intensity = 2f,
    distance = 20f,
    angle = PI / 8,
    penumbra = 0.3f
)
flashlight.position.set(0f, 5f, 5f)
scene.add(flashlight)

// Point at target
flashlight.target.position.set(0f, 0f, 0f)
scene.add(flashlight.target)

// With shadows
flashlight.castShadow = true
flashlight.shadow.mapSize.set(1024f, 1024f)
flashlight.shadow.camera.near = 0.5f
flashlight.shadow.camera.far = 25f
flashlight.shadow.focus = 1f

// Projector texture (gobo)
flashlight.map = textureLoader.load("textures/gobo.png")
```

---

## RectAreaLight

Rectangular area light (soft box lighting).

### Constructor

```kotlin
class RectAreaLight(
    color: Color = Color.WHITE,
    intensity: Float = 1f,
    width: Float = 10f,
    height: Float = 10f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Light color |
| `intensity` | `Float` | `1.0` | Light intensity |
| `width` | `Float` | `10` | Light width |
| `height` | `Float` | `10` | Light height |

### Notes

- Only works with `MeshStandardMaterial` and `MeshPhysicalMaterial`
- Does not support shadows
- Requires special shader includes

### Example

```kotlin
// Soft studio light
val areaLight = RectAreaLight(Color(0xffffff), 5f, 4f, 2f)
areaLight.position.set(-5f, 5f, 5f)
areaLight.lookAt(Vector3.ZERO)
scene.add(areaLight)

// Helper for visualization
val helper = RectAreaLightHelper(areaLight)
scene.add(helper)
```

---

## HemisphereLight

Gradient light from sky color to ground color.

### Constructor

```kotlin
class HemisphereLight(
    skyColor: Color = Color.WHITE,
    groundColor: Color = Color.WHITE,
    intensity: Float = 1f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `color` | `Color` | `WHITE` | Sky color |
| `groundColor` | `Color` | `WHITE` | Ground color |
| `intensity` | `Float` | `1.0` | Light intensity |

### Example

```kotlin
// Outdoor ambient
val hemi = HemisphereLight(
    skyColor = Color(0x87ceeb),    // Sky blue
    groundColor = Color(0x362d1a), // Brown earth
    intensity = 0.6f
)
scene.add(hemi)
```

---

## LightProbe

Light probe for image-based lighting.

### Constructor

```kotlin
class LightProbe(
    sh: SphericalHarmonics3 = SphericalHarmonics3(),
    intensity: Float = 1f
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `sh` | `SphericalHarmonics3` | Spherical harmonics coefficients |
| `intensity` | `Float` | Probe intensity |

### Example

```kotlin
// Create probe from environment map
val probe = LightProbeGenerator.fromCubeTexture(cubeTexture)
probe.intensity = 0.5f
scene.add(probe)
```

---

## Shadow Configuration

### Renderer Shadow Settings

```kotlin
// Enable shadows on renderer
renderer.shadowMap.enabled = true
renderer.shadowMap.type = ShadowMapType.PCFSoft

// Shadow map types
enum class ShadowMapType {
    BASIC,      // No filtering
    PCF,        // Percentage-closer filtering
    PCF_SOFT,   // Soft PCF
    VSM         // Variance shadow mapping
}
```

### Object Shadow Settings

```kotlin
// Cast shadows
mesh.castShadow = true

// Receive shadows
mesh.receiveShadow = true

// Materials auto-receive if receiveShadow is true
```

### Shadow Optimization

```kotlin
// Use smaller shadow maps for less important lights
fill.castShadow = true
fill.shadow.mapSize.set(256f, 256f)

// Tight shadow camera bounds
directional.shadow.camera.left = -5f
directional.shadow.camera.right = 5f
directional.shadow.camera.top = 5f
directional.shadow.camera.bottom = -5f

// Update shadow camera
directional.shadow.camera.updateProjectionMatrix()

// Bias to prevent shadow acne
directional.shadow.bias = -0.0001f
directional.shadow.normalBias = 0.02f
```

---

## Light Helpers

### DirectionalLightHelper

```kotlin
val helper = DirectionalLightHelper(directionalLight, 5f, Color.YELLOW)
scene.add(helper)
```

### PointLightHelper

```kotlin
val helper = PointLightHelper(pointLight, 1f, Color.RED)
scene.add(helper)
```

### SpotLightHelper

```kotlin
val helper = SpotLightHelper(spotLight, Color.GREEN)
scene.add(helper)
```

### HemisphereLightHelper

```kotlin
val helper = HemisphereLightHelper(hemisphereLight, 5f)
scene.add(helper)
```

### RectAreaLightHelper

```kotlin
val helper = RectAreaLightHelper(rectAreaLight)
scene.add(helper)
```

---

## Common Lighting Setups

### Three-Point Lighting

```kotlin
// Key light (main illumination)
val key = DirectionalLight(Color(0xffffff), 1.0f)
key.position.set(5f, 5f, 5f)
key.castShadow = true
scene.add(key)

// Fill light (soften shadows)
val fill = DirectionalLight(Color(0x8888ff), 0.3f)
fill.position.set(-5f, 3f, 0f)
scene.add(fill)

// Back light (rim/separation)
val back = DirectionalLight(Color(0xffff88), 0.5f)
back.position.set(0f, 5f, -5f)
scene.add(back)

// Ambient fill
val ambient = AmbientLight(Color(0x404040), 0.2f)
scene.add(ambient)
```

### Outdoor Daylight

```kotlin
// Sun
val sun = DirectionalLight(Color(0xffffd4), 1.2f)
sun.position.set(50f, 100f, 50f)
sun.castShadow = true
scene.add(sun)

// Sky/ground hemisphere
val hemi = HemisphereLight(
    skyColor = Color(0x87ceeb),
    groundColor = Color(0xb97a20),
    intensity = 0.5f
)
scene.add(hemi)
```

### Indoor Studio

```kotlin
// Soft area lights
val softbox1 = RectAreaLight(Color.WHITE, 5f, 2f, 2f)
softbox1.position.set(-3f, 3f, 3f)
softbox1.lookAt(Vector3.ZERO)
scene.add(softbox1)

val softbox2 = RectAreaLight(Color.WHITE, 3f, 2f, 2f)
softbox2.position.set(3f, 2f, 3f)
softbox2.lookAt(Vector3.ZERO)
scene.add(softbox2)

// Background light
val back = RectAreaLight(Color(0x8888ff), 2f, 4f, 4f)
back.position.set(0f, 2f, -3f)
back.lookAt(Vector3.ZERO)
scene.add(back)
```

### Night/Horror

```kotlin
// Single point light (lamp/candle)
val point = PointLight(Color(0xff6600), 1f, 10f, 2f)
point.position.set(0f, 2f, 0f)
point.castShadow = true
scene.add(point)

// Very dim ambient
val ambient = AmbientLight(Color(0x101020), 0.1f)
scene.add(ambient)

// Animate flicker
fun animate(time: Float) {
    point.intensity = 1f + sin(time * 10f) * 0.1f + random() * 0.05f
}
```

---

## See Also

- [Material API](material.md) - Materials that respond to lights
- [Renderer API](renderer.md) - Shadow map configuration
- [Advanced: Post-Processing](../advanced/post-processing.md) - Bloom and glow effects
