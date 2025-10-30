# Camera API Reference

Cameras define the viewpoint for rendering 3D scenes. Materia provides multiple camera types for
different projection and
rendering needs.

## Table of Contents

- [Camera (Base Class)](#camera-base-class)
- [PerspectiveCamera](#perspectivecamera)
- [OrthographicCamera](#orthographiccamera)
- [ArrayCamera](#arraycamera)
- [CubeCamera](#cubecamera)
- [StereoCamera](#stereocamera)
- [Camera Controls](#camera-controls)

---

## Camera (Base Class)

Abstract base class for all cameras.

### Properties

```kotlin
val projectionMatrix: Matrix4     // Projection matrix
var projectionMatrixNeedsUpdate: Boolean  // Dirty flag
var near: Float                   // Near clipping plane
var far: Float                    // Far clipping plane
var zoom: Float                   // Zoom factor (default: 1.0)
```

### Methods

```kotlin
// Update projection matrix
abstract fun updateProjectionMatrix()

// Coordinate transformations
fun worldToNDC(worldPosition: Vector3, target: Vector3): Vector3
fun ndcToWorld(ndcPosition: Vector3, target: Vector3): Vector3

// View offset (for tiled rendering / VR)
abstract fun setViewOffset(
    fullWidth: Int, fullHeight: Int,
    x: Int, y: Int,
    width: Int, height: Int
)
abstract fun clearViewOffset()

// Film properties
abstract fun getEffectiveFOV(): Float
abstract fun getFilmWidth(): Float
abstract fun getFilmHeight(): Float

// Field of view
abstract fun setFieldOfView(fov: Float)

// Focal length
abstract fun setFocalLength(focalLength: Float)
abstract fun getFocalLength(): Float
```

---

## PerspectiveCamera

Perspective projection camera simulating human eye perspective. Most commonly used camera type.

### Constructor

```kotlin
val camera = PerspectiveCamera(
    fov = 75f,            // Vertical field of view in degrees
    aspect = 16f / 9f,    // Aspect ratio (width / height)
    near = 0.1f,          // Near clipping plane
    far = 1000f           // Far clipping plane
)
```

### Properties

```kotlin
var fov: Float          // Vertical field of view (degrees)
var aspect: Float       // Aspect ratio (width / height)
var near: Float         // Near clipping distance
var far: Float          // Far clipping distance
var zoom: Float         // Zoom factor (default: 1.0)
var focus: Float        // Focus distance for depth of field (default: 10.0)
var filmGauge: Float    // Film gauge in mm (default: 35.0)
var filmOffset: Float   // Film offset for lens shift effects
```

### Basic Usage

```kotlin
// Create camera
val camera = PerspectiveCamera(
    fov = 75f,
    aspect = window.width.toFloat() / window.height.toFloat(),
    near = 0.1f,
    far = 1000f
)

// Position camera
camera.position.set(0f, 5f, 10f)

// Look at scene center
camera.lookAt(Vector3(0f, 0f, 0f))

// Update matrices
camera.updateMatrixWorld()
```

### Field of View

```kotlin
// Set FOV and update projection
camera.fov = 90f
camera.updateProjectionMatrix()

// Get effective FOV (accounting for zoom)
val effectiveFov = camera.getEffectiveFOV()

// Get horizontal FOV
val hFOV = camera.getHorizontalFOV()
```

### Aspect Ratio

```kotlin
// Update aspect ratio (e.g., on window resize)
camera.aspect = newWidth.toFloat() / newHeight.toFloat()
camera.updateProjectionMatrix()

// Extension function
camera.setAspectFromViewport(width, height)
```

### Focal Length

```kotlin
// Set focal length in millimeters
camera.setFocalLength(50f)  // Standard lens

// Get current focal length
val focal = camera.getFocalLength()

// Set lens parameters
camera.setLens(
    focalLength = 50f,
    filmGauge = 35f
)
```

### Zoom

```kotlin
// Zoom in (2x magnification)
camera.zoom = 2f

// Automatically updates projection matrix
// (zoom setter calls updateProjectionMatrix())

// For manual control
camera.zoom = 1.5f
camera.updateProjectionMatrix()
```

### View Offset (Tiled Rendering / VR)

```kotlin
// Set view offset for tiled rendering
camera.setViewOffset(
    fullWidth = 1920,
    fullHeight = 1080,
    x = 0,
    y = 0,
    width = 960,
    height = 1080
)

// Clear view offset
camera.clearViewOffset()
```

### View Bounds

```kotlin
// Get view bounds at distance
val bounds = camera.getViewBounds(distance = 10f)
println("Width: ${bounds.max.x - bounds.min.x}")

// Get view size at distance
val size = camera.getViewSize(distance = 10f)
println("Width: ${size.x}, Height: ${size.y}")
```

### Framing Objects

```kotlin
// Calculate distance to fit a sphere
val distance = camera.getDistanceToFitSphere(
    radius = 5f,
    padding = 1.1f  // 10% padding
)

// Calculate distance to fit a box
val box = Box3(
    min = Vector3(-5f, -5f, -5f),
    max = Vector3(5f, 5f, 5f)
)
val dist = camera.getDistanceToFitBox(box, padding = 1.2f)

// Frame an object automatically
camera.frameObject(
    targetPosition = Vector3(0f, 0f, 0f),
    objectSize = 10f,
    padding = 1.1f,
    direction = Vector3(0f, 0f, 1f)  // Look direction
)
```

### Raycasting

```kotlin
// Create ray from NDC coordinates
val ray = camera.createRay(
    ndcX = 0f,     // -1 to 1
    ndcY = 0f      // -1 to 1
)

// Create ray from screen coordinates
val ray2 = camera.createRayFromScreen(
    screenX = mouseX,
    screenY = mouseY,
    screenWidth = window.width.toFloat(),
    screenHeight = window.height.toFloat()
)
```

### Builder DSL

```kotlin
val camera = perspectiveCamera {
    fov(75f)
    aspect(16f / 9f)
    near(0.1f)
    far(1000f)
    zoom(1.5f)

    position(0f, 5f, 10f)
    lookAt(0f, 0f, 0f)
}
```

### JSON Export

```kotlin
val json: Map<String, Any> = camera.toJSON()
```

### Cloning

```kotlin
val clone = camera.clone(recursive = true)
```

---

## OrthographicCamera

Orthographic projection camera without perspective distortion. Useful for 2D games, CAD, and technical diagrams.

### Constructor

```kotlin
val camera = OrthographicCamera(
    left = -10f,
    right = 10f,
    top = 10f,
    bottom = -10f,
    near = 0.1f,
    far = 1000f
)
```

### Properties

```kotlin
var left: Float     // Left plane of view frustum
var right: Float    // Right plane of view frustum
var top: Float      // Top plane of view frustum
var bottom: Float   // Bottom plane of view frustum
var near: Float     // Near clipping plane
var far: Float      // Far clipping plane
var zoom: Float     // Zoom factor
```

### Basic Usage

```kotlin
// Create orthographic camera
val camera = OrthographicCamera(
    left = -100f,
    right = 100f,
    top = 100f,
    bottom = -100f,
    near = 0.1f,
    far = 1000f
)

// Position and orientation
camera.position.set(0f, 100f, 0f)
camera.lookAt(Vector3.ZERO)
```

### Updating Frustum

```kotlin
// Resize orthographic frustum
camera.left = -width / 2f
camera.right = width / 2f
camera.top = height / 2f
camera.bottom = -height / 2f
camera.updateProjectionMatrix()
```

### Zoom

```kotlin
// Zoom in (2x magnification)
camera.zoom = 2f  // View area is halved

// Zoom out
camera.zoom = 0.5f  // View area is doubled
```

### Use Cases

```kotlin
// Top-down view for strategy game
val topDown = OrthographicCamera(
    left = -50f, right = 50f,
    top = 50f, bottom = -50f,
    near = 1f, far = 100f
).apply {
    position.set(0f, 50f, 0f)
    lookAt(Vector3.ZERO)
}

// Side view for platformer
val sideView = OrthographicCamera(
    left = -10f, right = 10f,
    top = 7.5f, bottom = -7.5f,
    near = 0.1f, far = 100f
).apply {
    position.set(0f, 0f, 10f)
}

// CAD isometric view
val isometric = OrthographicCamera(
    left = -20f, right = 20f,
    top = 20f, bottom = -20f,
    near = 0.1f, far = 1000f
).apply {
    position.set(50f, 50f, 50f)
    lookAt(Vector3.ZERO)
}
```

---

## ArrayCamera

Renders scene multiple times from different cameras (useful for multi-viewport rendering).

### Constructor

```kotlin
val arrayCamera = ArrayCamera(
    cameras = listOf(camera1, camera2, camera3)
)
```

### Basic Usage

```kotlin
// Create cameras for different viewports
val leftCamera = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f)
val rightCamera = PerspectiveCamera(75f, 0.5f, 0.1f, 1000f)

// Arrange side-by-side
val arrayCamera = ArrayCamera(listOf(leftCamera, rightCamera))

// Configure viewports (automatically done by renderer)
// Renderer calls each camera's render
```

---

## CubeCamera

Renders scene to a cubemap texture (useful for reflections and environment maps).

### Constructor

```kotlin
val cubeCamera = CubeCamera(
    near = 0.1f,
    far = 1000f,
    cubeResolution = 512
)
```

### Basic Usage

```kotlin
// Create cube camera
val cubeCamera = CubeCamera(
    near = 0.1f,
    far = 1000f,
    cubeResolution = 1024
)

// Position at object that needs reflections
cubeCamera.position.copy(reflectiveObject.position)

// Update cubemap
cubeCamera.update(renderer, scene)

// Use cubemap for reflections
reflectiveMaterial.envMap = cubeCamera.renderTarget.texture
```

---

## StereoCamera

Renders scene from two cameras for stereoscopic 3D (VR/3D glasses).

### Constructor

```kotlin
val stereoCamera = StereoCamera()
```

### Properties

```kotlin
var eyeSeparation: Float  // Distance between eyes (default: 0.064)
val cameraL: PerspectiveCamera  // Left eye camera
val cameraR: PerspectiveCamera  // Right eye camera
```

### Basic Usage

```kotlin
// Create stereo camera
val stereoCamera = StereoCamera()

// Update cameras from parent camera
stereoCamera.update(mainCamera)

// Render left and right views
renderer.setViewport(0, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraL)

renderer.setViewport(width / 2, 0, width / 2, height)
renderer.render(scene, stereoCamera.cameraR)
```

---

## Camera Controls

Camera controls handle user input for navigating the 3D scene.

### OrbitControls

Rotate, pan, and zoom around a target point.

```kotlin
import io.materia.controls.OrbitControls

val controls = OrbitControls(camera, canvas).apply {
    target.set(0f, 0f, 0f)  // Look at scene center
    enableDamping = true
    dampingFactor = 0.05f
    enableZoom = true
    enablePan = true
    enableRotate = true

    // Constraints
    minDistance = 5f
    maxDistance = 100f
    minPolarAngle = 0f
    maxPolarAngle = PI.toFloat()
    minAzimuthAngle = -Infinity
    maxAzimuthAngle = Infinity
}

// Update in animation loop
fun animate() {
    controls.update()
    renderer.render(scene, camera)
}
```

### FlyControls

Free-flying controls for first-person navigation.

```kotlin
import io.materia.controls.FlyControls

val controls = FlyControls(camera, canvas).apply {
    movementSpeed = 10f
    rollSpeed = PI.toFloat() / 24f
    autoForward = false
    dragToLook = true
}

// Update with delta time
fun animate(deltaTime: Float) {
    controls.update(deltaTime)
    renderer.render(scene, camera)
}
```

### FirstPersonControls

First-person controls with ground constraints.

```kotlin
import io.materia.controls.FirstPersonControls

val controls = FirstPersonControls(camera, canvas).apply {
    movementSpeed = 10f
    lookSpeed = 0.1f
    constrainVertical = true
    verticalMin = 0f
    verticalMax = PI.toFloat()
}

fun animate(deltaTime: Float) {
    controls.update(deltaTime)
    renderer.render(scene, camera)
}
```

### TrackballControls

Rotation around any axis (like a virtual trackball).

```kotlin
import io.materia.controls.TrackballControls

val controls = TrackballControls(camera, canvas).apply {
    rotateSpeed = 1.0f
    zoomSpeed = 1.2f
    panSpeed = 0.8f
    enableDamping = true
}

fun animate() {
    controls.update()
    renderer.render(scene, camera)
}
```

### ArcballControls

Advanced trackball controls with gizmo.

```kotlin
import io.materia.controls.ArcballControls

val controls = ArcballControls(camera, canvas, scene).apply {
    setGizmosVisible(true)
    enableZoom = true
    enablePan = true
    enableRotate = true
    cursorZoom = true
}

fun animate() {
    controls.update()
    renderer.render(scene, camera)
}
```

### Controls Factory

```kotlin
import io.materia.controls.ControlsFactory
import io.materia.controls.ControlsType

// Create controls by type
val controls = ControlsFactory.create(
    type = ControlsType.ORBIT,
    camera = camera,
    domElement = canvas
)

controls.update()
```

---

## Common Patterns

### Window Resize Handling

```kotlin
// Perspective camera
fun handleResize(width: Int, height: Int) {
    camera.aspect = width.toFloat() / height.toFloat()
    camera.updateProjectionMatrix()
    renderer.setSize(width, height)
}

// Orthographic camera
fun handleResize(width: Int, height: Int) {
    val aspect = width.toFloat() / height.toFloat()
    camera.left = -frustumSize * aspect / 2f
    camera.right = frustumSize * aspect / 2f
    camera.top = frustumSize / 2f
    camera.bottom = -frustumSize / 2f
    camera.updateProjectionMatrix()
    renderer.setSize(width, height)
}
```

### Screen to World Coordinates

```kotlin
fun screenToWorld(screenX: Float, screenY: Float): Vector3 {
    // Convert screen to NDC
    val ndcX = (screenX / screenWidth) * 2f - 1f
    val ndcY = -(screenY / screenHeight) * 2f + 1f

    // Create ray
    val ray = camera.createRay(ndcX, ndcY)

    // Intersect with plane at z=0
    val plane = Plane(Vector3.UNIT_Z, 0f)
    val intersection = Vector3()

    if (plane.intersectRay(ray, intersection)) {
        return intersection
    }

    return Vector3.ZERO
}
```

### Camera Animation

```kotlin
// Smooth camera movement
fun animateCamera(
    targetPosition: Vector3,
    targetLookAt: Vector3,
    duration: Float
) {
    val startPos = camera.position.clone()
    val startLookAt = controls.target.clone()

    var elapsed = 0f

    fun update(deltaTime: Float) {
        elapsed += deltaTime
        val t = (elapsed / duration).coerceIn(0f, 1f)

        // Smooth easing
        val smoothT = t * t * (3f - 2f * t)

        // Interpolate position
        camera.position.lerpVectors(startPos, targetPosition, smoothT)

        // Interpolate look-at target
        controls.target.lerpVectors(startLookAt, targetLookAt, smoothT)

        camera.updateMatrixWorld()

        if (t < 1f) {
            requestAnimationFrame { update(it) }
        }
    }

    update(0f)
}
```

### Minimap Camera

```kotlin
// Top-down minimap camera
val minimapCamera = OrthographicCamera(
    left = -50f, right = 50f,
    top = 50f, bottom = -50f,
    near = 0.1f, far = 100f
).apply {
    position.set(0f, 50f, 0f)
    lookAt(Vector3.ZERO)
}

// Render minimap to separate viewport
renderer.setViewport(width - 200, height - 200, 200, 200)
renderer.render(scene, minimapCamera)

// Restore main viewport
renderer.setViewport(0, 0, width, height)
renderer.render(scene, mainCamera)
```

---

## Performance Tips

1. **Update projection only when needed**: Avoid calling `updateProjectionMatrix()` every frame
2. **Reuse cameras**: Don't create new cameras every frame
3. **Optimize controls**: Use damping for smoother motion with less updates
4. **Frustum culling**: Materia automatically culls objects outside camera view
5. **LOD**: Use LOD (Level of Detail) based on camera distance

---

## See Also

- [Controls Source](../../../src/commonMain/kotlin/io/materia/controls/)
- [Camera Tutorial](../../guides/cameras-guide.md)
- [Scene Graph](../scene/scene-graph.md)
- [Rendering](../renderer/renderer.md)
