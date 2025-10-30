# Camera Module API Reference

The camera module provides various camera types for viewing and rendering 3D scenes from different perspectives.

## Overview

Cameras in Materia define the viewpoint from which scenes are rendered. They compute view and
projection matrices that
transform 3D world coordinates into 2D screen coordinates.

## Camera Types

### PerspectiveCamera

Perspective camera with field-of-view projection that simulates realistic depth perception.

```kotlin
/**
 * Perspective camera with field of view projection.
 *
 * Simulates human eye perspective where distant objects appear smaller.
 * Most commonly used camera type for 3D scenes.
 *
 * @property fov Field of view in degrees (default: 50°)
 * @property aspect Aspect ratio width/height (default: 1.0)
 * @property near Near clipping plane distance (default: 0.1)
 * @property far Far clipping plane distance (default: 2000.0)
 */
class PerspectiveCamera(
    fov: Float = 50f,
    aspect: Float = 1f,
    near: Float = 0.1f,
    far: Float = 2000f
) : Camera()
```

**Example Usage:**

```kotlin
// Create a camera with 75° FOV, 16:9 aspect ratio
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 0.1f,
    far = 1000f
)

// Position camera
camera.position.set(0f, 5f, 10f)
camera.lookAt(Vector3.ZERO)

// Update FOV
camera.fov = 60f
camera.updateProjectionMatrix() // Required after changes

// Zoom in (smaller FOV = zoom)
camera.zoom = 2f // 2x zoom

// Calculate focal length
val focalLength = camera.getFocalLength() // mm
```

**Properties:**

| Property     | Type  | Description                       |
|--------------|-------|-----------------------------------|
| `fov`        | Float | Vertical field of view in degrees |
| `aspect`     | Float | Aspect ratio (width / height)     |
| `zoom`       | Float | Zoom factor (default: 1.0)        |
| `focus`      | Float | Focus distance for DOF effects    |
| `filmGauge`  | Float | Film gauge in mm (default: 35mm)  |
| `filmOffset` | Float | Film offset for lens shift        |

**Methods:**

- `updateProjectionMatrix()` - Recalculate projection matrix
- `setViewOffset(fullWidth, fullHeight, x, y, width, height)` - Set view offset for tiled rendering
- `clearViewOffset()` - Clear view offset
- `getFocalLength(): Float` - Get focal length in mm
- `setFocalLength(focalLength: Float)` - Set focal length
- `getEffectiveFOV(): Float` - Get FOV accounting for zoom
- `getFilmWidth(): Float` - Get film width in mm
- `getFilmHeight(): Float` - Get film height in mm

### OrthographicCamera

Orthographic camera with parallel projection (no perspective distortion).

```kotlin
/**
 * Orthographic camera with parallel projection.
 *
 * Objects maintain their size regardless of distance.
 * Useful for 2D scenes, CAD applications, and technical visualizations.
 *
 * @property left Left extent of viewing frustum
 * @property right Right extent of viewing frustum
 * @property top Top extent of viewing frustum
 * @property bottom Bottom extent of viewing frustum
 * @property near Near clipping plane
 * @property far Far clipping plane
 */
class OrthographicCamera(
    left: Float = -1f,
    right: Float = 1f,
    top: Float = 1f,
    bottom: Float = -1f,
    near: Float = 0.1f,
    far: Float = 2000f
) : Camera()
```

**Example Usage:**

```kotlin
// Create orthographic camera for 2D scene
val camera = OrthographicCamera(
    left = -10f,
    right = 10f,
    top = 10f,
    bottom = -10f,
    near = 0.1f,
    far = 100f
)

// Adjust view bounds
camera.left = -5f
camera.right = 5f
camera.top = 5f
camera.bottom = -5f
camera.updateProjectionMatrix()

// Zoom (scale the view bounds)
camera.zoom = 2f // 2x zoom
camera.updateProjectionMatrix()

// Set view offset for multi-monitor setup
camera.setViewOffset(
    fullWidth = 1920,
    fullHeight = 1080,
    x = 0,
    y = 0,
    width = 960,
    height = 1080
)
```

**Properties:**

| Property | Type  | Description                        |
|----------|-------|------------------------------------|
| `left`   | Float | Left plane of frustum              |
| `right`  | Float | Right plane of frustum             |
| `top`    | Float | Top plane of frustum               |
| `bottom` | Float | Bottom plane of frustum            |
| `zoom`   | Float | Zoom factor (affects frustum size) |

**Methods:**

- `updateProjectionMatrix()` - Recalculate projection matrix
- `setViewOffset(fullWidth, fullHeight, x, y, width, height)` - Set view offset
- `clearViewOffset()` - Clear view offset

### ArrayCamera

Multi-camera array for split-screen or multi-view rendering.

```kotlin
/**
 * Array of cameras for split-screen rendering.
 *
 * Renders the scene from multiple viewpoints in a single frame.
 * Useful for split-screen multiplayer or multi-monitor setups.
 */
class ArrayCamera(
    val cameras: Array<Camera> = emptyArray()
) : Camera()
```

**Example Usage:**

```kotlin
// Create split-screen setup
val leftCamera = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f).apply {
    viewport = Viewport(0, 0, 640, 480) // Left half
}

val rightCamera = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f).apply {
    viewport = Viewport(640, 0, 640, 480) // Right half
}

val arrayCamera = ArrayCamera(arrayOf(leftCamera, rightCamera))

// Render with array camera
renderer.render(scene, arrayCamera)
```

**Properties:**

| Property  | Type          | Description          |
|-----------|---------------|----------------------|
| `cameras` | Array<Camera> | Array of sub-cameras |

### CubeCamera

Camera for capturing environment maps (cubemaps).

```kotlin
/**
 * Camera for capturing cubemap environment maps.
 *
 * Renders the scene from 6 directions to create a cubemap texture.
 * Used for environment reflections and IBL.
 *
 * @property near Near clipping plane
 * @property far Far clipping plane
 * @property renderTarget Render target for cubemap
 */
class CubeCamera(
    near: Float = 0.1f,
    far: Float = 1000f,
    renderTarget: WebGLCubeRenderTarget
) : Object3D()
```

**Example Usage:**

```kotlin
// Create cube camera for reflections
val cubeRenderTarget = WebGLCubeRenderTarget(512)
val cubeCamera = CubeCamera(0.1f, 1000f, cubeRenderTarget)

// Position at reflection point
cubeCamera.position.copy(reflectiveObject.position)

// Capture environment
cubeCamera.update(renderer, scene)

// Use as environment map
material.envMap = cubeRenderTarget.texture
```

**Methods:**

- `update(renderer: Renderer, scene: Scene)` - Capture environment from current position

### StereoCamera

Stereo camera for VR rendering with separate left and right eye views.

```kotlin
/**
 * Stereo camera for VR rendering.
 *
 * Generates separate left and right eye views with appropriate
 * eye separation for stereoscopic 3D.
 */
class StereoCamera() : Camera()
```

**Example Usage:**

```kotlin
// Create stereo camera
val stereoCamera = StereoCamera()

// Update from main camera
stereoCamera.update(mainCamera)

// Render left eye
renderer.setViewport(0, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraL)

// Render right eye
renderer.setViewport(width / 2, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraR)
```

**Properties:**

| Property  | Type              | Description                   |
|-----------|-------------------|-------------------------------|
| `cameraL` | PerspectiveCamera | Left eye camera               |
| `cameraR` | PerspectiveCamera | Right eye camera              |
| `aspect`  | Float             | Aspect ratio                  |
| `eyeSep`  | Float             | Eye separation distance (IPD) |

**Methods:**

- `update(camera: PerspectiveCamera)` - Update from main camera

## Base Camera Class

All cameras inherit from the `Camera` base class:

```kotlin
abstract class Camera : Object3D() {
    /**
     * Projection matrix
     */
    val projectionMatrix: Matrix4

    /**
     * Inverse projection matrix
     */
    val projectionMatrixInverse: Matrix4

    /**
     * Layers this camera renders
     */
    val layers: Layers

    /**
     * Near clipping plane
     */
    var near: Float

    /**
     * Far clipping plane
     */
    var far: Float

    /**
     * Zoom factor
     */
    abstract var zoom: Float

    /**
     * Update projection matrix
     */
    abstract fun updateProjectionMatrix()

    /**
     * Get world direction this camera is facing
     */
    fun getWorldDirection(target: Vector3 = Vector3()): Vector3

    /**
     * Clone this camera
     */
    abstract fun clone(): Camera

    /**
     * Copy camera properties
     */
    fun copy(source: Camera, recursive: Boolean = true): Camera
}
```

## Common Camera Operations

### Positioning and Orientation

```kotlin
// Position camera in world space
camera.position.set(x, y, z)

// Look at a point
camera.lookAt(Vector3(0f, 0f, 0f))

// Look at an object
camera.lookAt(object.position)

// Rotate camera
camera.rotation.y = PI.toFloat() / 4f // 45° rotation

// Use quaternion rotation
camera.quaternion.setFromEuler(euler)
```

### Projection Control

```kotlin
// Update projection after parameter changes
camera.fov = 60f
camera.aspect = newWidth.toFloat() / newHeight
camera.updateProjectionMatrix()

// Zoom in/out
camera.zoom *= 1.1f  // Zoom in 10%
camera.updateProjectionMatrix()
```

### Frustum and Raycasting

```kotlin
// Extract frustum planes
val frustum = Frustum().setFromProjectionMatrix(
    Matrix4().multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)
)

// Check if object is in view
if (frustum.intersectsObject(object)) {
    // Object is visible
}

// Raycast from screen coordinates
val raycaster = Raycaster()
raycaster.setFromCamera(Vector2(mouseX, mouseY), camera)
val intersects = raycaster.intersectObjects(scene.children)
```

### View Offset (Tiled Rendering)

```kotlin
// Set up for tiled rendering (e.g., multi-monitor)
camera.setViewOffset(
    fullWidth = 3840,  // Total width of all monitors
    fullHeight = 1080, // Height
    x = 1920,          // X offset of this monitor
    y = 0,             // Y offset
    width = 1920,      // Width of this monitor
    height = 1080      // Height of this monitor
)

// Clear when done
camera.clearViewOffset()
```

## Advanced Topics

### Custom Projection Matrices

```kotlin
// Set custom projection matrix
camera.projectionMatrix.set(
    m00, m01, m02, m03,
    m10, m11, m12, m13,
    m20, m21, m22, m23,
    m30, m31, m32, m33
)
camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert()
```

### Camera Layers

```kotlin
// Set camera to only render certain layers
camera.layers.set(0) // Layer 0 only
camera.layers.enable(1) // Also layer 1
camera.layers.toggle(2) // Toggle layer 2

// Objects also have layers
object.layers.set(1) // Only visible to cameras with layer 1 enabled
```

### Camera Animation

```kotlin
// Smooth camera movement
fun animateCamera(deltaTime: Float) {
    val targetPosition = Vector3(10f, 5f, 10f)
    camera.position.lerp(targetPosition, deltaTime * 2f)

    val targetLookAt = object.position
    camera.lookAt(targetLookAt)
}
```

## Best Practices

### FOV and Aspect Ratio

```kotlin
// Typical FOV values
val narrowFOV = 35f  // Telephoto lens
val normalFOV = 50f  // Normal lens
val wideFOV = 75f    // Wide-angle lens
val ultraWideFOV = 120f // Ultra-wide

// Update aspect ratio on window resize
fun onWindowResize(width: Int, height: Int) {
    camera.aspect = width.toFloat() / height.toFloat()
    camera.updateProjectionMatrix()
    renderer.setSize(width, height)
}
```

### Clipping Planes

```kotlin
// Choose appropriate clipping planes
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 0.1f,  // Too small = Z-fighting
    far = 1000f   // Too large = precision issues
)

// For large scenes
val sceneCamera = PerspectiveCamera(
    fov = 75f,
    aspect = 16f / 9f,
    near = 1f,     // Larger near plane
    far = 10000f   // Larger far plane
)
```

### Performance Tips

1. **Minimize updateProjectionMatrix() calls** - Only call when parameters change
2. **Use layer culling** - Exclude objects from certain cameras
3. **Frustum culling** - Automatically handled by renderer
4. **Cache camera matrices** - Reuse for multiple render passes

## Examples

### First-Person Camera

```kotlin
val camera = PerspectiveCamera(75f, aspect, 0.1f, 1000f)
camera.position.y = 1.7f // Eye height

// In update loop
fun update(input: InputState, deltaTime: Float) {
    val speed = 5f * deltaTime

    if (input.isKeyPressed("W")) {
        val forward = camera.getWorldDirection()
        camera.position.add(forward.multiply(speed))
    }

    // Mouse look
    camera.rotation.y -= input.mouseDeltaX * 0.002f
    camera.rotation.x -= input.mouseDeltaY * 0.002f
}
```

### Orbit Camera

```kotlin
val target = Vector3(0f, 0f, 0f)
val camera = PerspectiveCamera(75f, aspect, 0.1f, 1000f)

var radius = 10f
var theta = 0f
var phi = PI.toFloat() / 4f

fun update(deltaTime: Float) {
    theta += deltaTime

    camera.position.x = radius * sin(phi) * cos(theta)
    camera.position.y = radius * cos(phi)
    camera.position.z = radius * sin(phi) * sin(theta)

    camera.lookAt(target)
}
```

### Top-Down 2D Camera

```kotlin
val camera = OrthographicCamera(
    left = -10f,
    right = 10f,
    top = 10f,
    bottom = -10f,
    near = 0.1f,
    far = 100f
)

camera.position.y = 50f
camera.lookAt(Vector3.ZERO)

// Pan camera
fun pan(deltaX: Float, deltaY: Float) {
    val scale = (camera.right - camera.left) / viewportWidth
    camera.left -= deltaX * scale
    camera.right -= deltaX * scale
    camera.top += deltaY * scale
    camera.bottom += deltaY * scale
    camera.updateProjectionMatrix()
}
```

## See Also

- [Controls Module](../controls/README.md) - Camera control implementations
- [Renderer Module](../renderer/README.md) - Rendering with cameras
- [Scene Graph](../core/README.md) - Object3D base class
- [Raycaster](../core/README.md) - Mouse picking and raycasting

---

**Module**: `io.materia.camera`
**Since**: 1.0.0
**Status**: ✅ Stable
