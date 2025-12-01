# Camera API Reference

The camera module provides different camera types for viewing 3D scenes.

## Overview

```kotlin
import io.materia.camera.*
```

---

## Camera (Base Class)

Abstract base class for all cameras.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `matrixWorldInverse` | `Matrix4` | Inverse of world matrix (view matrix) |
| `projectionMatrix` | `Matrix4` | Projection matrix |
| `projectionMatrixInverse` | `Matrix4` | Inverse projection matrix |

### Methods

```kotlin
// Get view-projection matrix
fun getWorldDirection(target: Vector3 = Vector3()): Vector3

// Copy camera properties
fun copy(source: Camera, recursive: Boolean = true): Camera

// Clone camera
fun clone(): Camera
```

---

## PerspectiveCamera

Standard perspective projection camera with field of view.

### Constructor

```kotlin
class PerspectiveCamera(
    fov: Float = 50f,
    aspect: Float = 1f,
    near: Float = 0.1f,
    far: Float = 2000f
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `fov` | `Float` | `50` | Vertical field of view (degrees) |
| `aspect` | `Float` | `1` | Aspect ratio (width / height) |
| `near` | `Float` | `0.1` | Near clipping plane |
| `far` | `Float` | `2000` | Far clipping plane |
| `focus` | `Float` | `10` | Focus distance for DOF |
| `filmGauge` | `Float` | `35` | Film size (mm) |
| `filmOffset` | `Float` | `0` | Horizontal offset |
| `zoom` | `Float` | `1` | Zoom factor |
| `view` | `View?` | `null` | Sub-view for multi-window |

### Methods

```kotlin
// Update projection matrix (call after changing properties)
fun updateProjectionMatrix()

// Set focal length (affects FOV)
fun setFocalLength(focalLength: Float)

// Get focal length
fun getFocalLength(): Float

// Get effective FOV (accounting for zoom)
fun getEffectiveFOV(): Float

// Get film dimensions
fun getFilmWidth(): Float
fun getFilmHeight(): Float

// Set view offset (for tiled rendering)
fun setViewOffset(
    fullWidth: Float,
    fullHeight: Float,
    x: Float,
    y: Float,
    width: Float,
    height: Float
)

// Clear view offset
fun clearViewOffset()
```

### Example

```kotlin
// Standard camera
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = window.width.toFloat() / window.height,
    near = 0.1f,
    far = 1000f
)

// Position camera
camera.position.set(0f, 5f, 10f)
camera.lookAt(Vector3.ZERO)

// Update on window resize
window.onResize { width, height ->
    camera.aspect = width.toFloat() / height
    camera.updateProjectionMatrix()
}

// Zoom
camera.zoom = 2f  // 2x zoom
camera.updateProjectionMatrix()
```

---

## OrthographicCamera

Orthographic projection camera (no perspective distortion).

### Constructor

```kotlin
class OrthographicCamera(
    left: Float = -1f,
    right: Float = 1f,
    top: Float = 1f,
    bottom: Float = -1f,
    near: Float = 0.1f,
    far: Float = 2000f
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `left` | `Float` | Left plane |
| `right` | `Float` | Right plane |
| `top` | `Float` | Top plane |
| `bottom` | `Float` | Bottom plane |
| `near` | `Float` | Near clipping plane |
| `far` | `Float` | Far clipping plane |
| `zoom` | `Float` | Zoom factor |
| `view` | `View?` | Sub-view |

### Methods

```kotlin
// Update projection matrix
fun updateProjectionMatrix()

// Set view offset
fun setViewOffset(
    fullWidth: Float,
    fullHeight: Float,
    x: Float,
    y: Float,
    width: Float,
    height: Float
)

// Clear view offset
fun clearViewOffset()
```

### Example

```kotlin
// Fit to viewport
val frustumSize = 10f
val aspect = window.width.toFloat() / window.height

val camera = OrthographicCamera(
    left = -frustumSize * aspect / 2,
    right = frustumSize * aspect / 2,
    top = frustumSize / 2,
    bottom = -frustumSize / 2,
    near = 0.1f,
    far = 1000f
)

// 2D style setup (looking down Z axis)
camera.position.set(0f, 0f, 10f)

// Update on resize
window.onResize { width, height ->
    val newAspect = width.toFloat() / height
    camera.left = -frustumSize * newAspect / 2
    camera.right = frustumSize * newAspect / 2
    camera.updateProjectionMatrix()
}
```

---

## ArrayCamera

Camera with multiple sub-cameras for multi-view rendering.

### Constructor

```kotlin
class ArrayCamera(
    cameras: Array<Camera> = emptyArray()
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `cameras` | `Array<Camera>` | Sub-cameras |

### Usage

```kotlin
// Create split-screen setup
val cameraLeft = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f)
cameraLeft.viewport = Vector4(0f, 0f, 0.5f, 1f)  // Left half
cameraLeft.position.set(-5f, 0f, 5f)
cameraLeft.lookAt(Vector3.ZERO)

val cameraRight = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f)
cameraRight.viewport = Vector4(0.5f, 0f, 0.5f, 1f)  // Right half
cameraRight.position.set(5f, 0f, 5f)
cameraRight.lookAt(Vector3.ZERO)

val arrayCamera = ArrayCamera(arrayOf(cameraLeft, cameraRight))

// Render
renderer.render(scene, arrayCamera)
```

---

## CubeCamera

Six cameras for rendering cube maps (environment/reflection).

### Constructor

```kotlin
class CubeCamera(
    near: Float,
    far: Float,
    renderTarget: WebGLCubeRenderTarget
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `renderTarget` | `CubeRenderTarget` | Target cube texture |

### Methods

```kotlin
// Update cube map
fun update(renderer: Renderer, scene: Scene)
```

### Example

```kotlin
// Create cube camera for reflections
val cubeRenderTarget = WebGLCubeRenderTarget(256)
val cubeCamera = CubeCamera(0.1f, 1000f, cubeRenderTarget)

// Position at reflective object
cubeCamera.position.copy(reflectiveSphere.position)

// Render environment
fun updateEnvironment() {
    reflectiveSphere.visible = false
    cubeCamera.update(renderer, scene)
    reflectiveSphere.visible = true
    
    // Use cube map for reflections
    reflectiveMaterial.envMap = cubeRenderTarget.texture
}
```

---

## StereoCamera

Stereo camera for VR/3D rendering.

### Constructor

```kotlin
class StereoCamera()
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aspect` | `Float` | `1` | Aspect ratio |
| `eyeSep` | `Float` | `0.064` | Eye separation (meters) |
| `cameraL` | `PerspectiveCamera` | auto | Left eye camera |
| `cameraR` | `PerspectiveCamera` | auto | Right eye camera |

### Methods

```kotlin
// Update stereo cameras from a source camera
fun update(camera: PerspectiveCamera)
```

### Example

```kotlin
val stereoCamera = StereoCamera()
stereoCamera.eyeSep = 0.064f

// In render loop
stereoCamera.update(mainCamera)

// Render left eye
renderer.setViewport(0, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraL)

// Render right eye
renderer.setViewport(width / 2, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraR)
```

---

## Camera Helpers

### CameraHelper

Visualizes camera frustum for debugging.

```kotlin
class CameraHelper(camera: Camera) : LineSegments
```

### Example

```kotlin
val helper = CameraHelper(camera)
scene.add(helper)

// Update when camera changes
fun animate() {
    helper.update()
}
```

---

## Utility Functions

### Screen to World

```kotlin
// Convert screen coordinates to world ray
fun screenToWorldRay(
    screenX: Float,
    screenY: Float,
    camera: Camera,
    target: Ray = Ray()
): Ray {
    val ndc = Vector3(
        (screenX / renderer.width) * 2 - 1,
        -(screenY / renderer.height) * 2 + 1,
        0.5f
    )
    
    ndc.unproject(camera)
    
    val origin = camera.getWorldPosition()
    val direction = ndc.sub(origin).normalize()
    
    return target.set(origin, direction)
}
```

### World to Screen

```kotlin
// Convert world position to screen coordinates
fun worldToScreen(
    worldPosition: Vector3,
    camera: Camera
): Vector2 {
    val projected = worldPosition.clone().project(camera)
    
    return Vector2(
        (projected.x + 1) / 2 * renderer.width,
        (-projected.y + 1) / 2 * renderer.height
    )
}
```

---

## Common Patterns

### First-Person Camera

```kotlin
class FirstPersonCamera(
    fov: Float = 75f,
    aspect: Float = 1f
) : PerspectiveCamera(fov, aspect) {
    
    var pitch: Float = 0f
    var yaw: Float = 0f
    
    private val euler = Euler(0f, 0f, 0f, EulerOrder.YXZ)
    
    fun rotate(deltaX: Float, deltaY: Float) {
        yaw -= deltaX * 0.002f
        pitch -= deltaY * 0.002f
        pitch = pitch.coerceIn(-PI / 2 + 0.01f, PI / 2 - 0.01f)
        
        euler.set(pitch, yaw, 0f)
        quaternion.setFromEuler(euler)
    }
    
    fun move(forward: Float, right: Float, up: Float) {
        val direction = Vector3()
        
        // Forward/backward
        getWorldDirection(direction)
        position.add(direction.multiplyScalar(forward))
        
        // Left/right
        direction.set(1f, 0f, 0f).applyQuaternion(quaternion)
        position.add(direction.multiplyScalar(right))
        
        // Up/down
        position.y += up
    }
}
```

### Orbit Camera

```kotlin
class OrbitCamera(
    val target: Vector3 = Vector3(),
    fov: Float = 75f,
    aspect: Float = 1f
) : PerspectiveCamera(fov, aspect) {
    
    var distance: Float = 10f
    var theta: Float = 0f  // Horizontal angle
    var phi: Float = PI / 4  // Vertical angle (0 = top, PI = bottom)
    
    fun update() {
        phi = phi.coerceIn(0.01f, PI - 0.01f)
        
        position.x = target.x + distance * sin(phi) * sin(theta)
        position.y = target.y + distance * cos(phi)
        position.z = target.z + distance * sin(phi) * cos(theta)
        
        lookAt(target)
    }
    
    fun orbit(deltaTheta: Float, deltaPhi: Float) {
        theta += deltaTheta
        phi += deltaPhi
        update()
    }
    
    fun zoom(delta: Float) {
        distance *= (1f - delta * 0.1f)
        distance = distance.coerceIn(1f, 100f)
        update()
    }
}
```

---

## See Also

- [Controls API](controls.md) - Camera control systems
- [Core API](core.md) - Vector3, Matrix4 for transforms
- [Renderer API](renderer.md) - Rendering with cameras
