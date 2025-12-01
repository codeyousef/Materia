# Controls API Reference

The controls module provides camera and object interaction controls.

## Overview

```kotlin
import io.materia.controls.*
```

---

## OrbitControls

Orbiting camera controls for rotating around a target.

### Constructor

```kotlin
class OrbitControls(
    camera: Camera,
    domElement: HTMLElement  // or Window on JVM
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable controls |
| `target` | `Vector3` | `(0,0,0)` | Orbit target point |
| `minDistance` | `Float` | `0` | Minimum zoom distance |
| `maxDistance` | `Float` | `Infinity` | Maximum zoom distance |
| `minZoom` | `Float` | `0` | Minimum zoom (ortho) |
| `maxZoom` | `Float` | `Infinity` | Maximum zoom (ortho) |
| `minPolarAngle` | `Float` | `0` | Minimum vertical angle |
| `maxPolarAngle` | `Float` | `PI` | Maximum vertical angle |
| `minAzimuthAngle` | `Float` | `-Infinity` | Minimum horizontal angle |
| `maxAzimuthAngle` | `Float` | `Infinity` | Maximum horizontal angle |
| `enableDamping` | `Boolean` | `false` | Enable smooth motion |
| `dampingFactor` | `Float` | `0.05` | Damping amount |
| `enableZoom` | `Boolean` | `true` | Enable zooming |
| `zoomSpeed` | `Float` | `1.0` | Zoom sensitivity |
| `enableRotate` | `Boolean` | `true` | Enable rotation |
| `rotateSpeed` | `Float` | `1.0` | Rotation sensitivity |
| `enablePan` | `Boolean` | `true` | Enable panning |
| `panSpeed` | `Float` | `1.0` | Pan sensitivity |
| `screenSpacePanning` | `Boolean` | `true` | Pan in screen space |
| `keyPanSpeed` | `Float` | `7.0` | Keyboard pan speed |
| `autoRotate` | `Boolean` | `false` | Auto-rotate |
| `autoRotateSpeed` | `Float` | `2.0` | Auto-rotate speed |
| `enableKeys` | `Boolean` | `true` | Enable keyboard |
| `keys` | `Keys` | default | Key bindings |
| `mouseButtons` | `MouseButtons` | default | Mouse bindings |
| `touches` | `Touches` | default | Touch bindings |

### Key Bindings

```kotlin
data class Keys(
    val left: String = "ArrowLeft",
    val up: String = "ArrowUp",
    val right: String = "ArrowRight",
    val bottom: String = "ArrowDown"
)
```

### Mouse Bindings

```kotlin
data class MouseButtons(
    val left: MouseAction = MouseAction.ROTATE,
    val middle: MouseAction = MouseAction.DOLLY,
    val right: MouseAction = MouseAction.PAN
)

enum class MouseAction {
    ROTATE, DOLLY, PAN, NONE
}
```

### Methods

```kotlin
// Update controls (call each frame)
fun update(): Boolean

// Reset to initial state
fun reset()

// Save current state
fun saveState()

// Get polar angle
fun getPolarAngle(): Float

// Get azimuthal angle
fun getAzimuthalAngle(): Float

// Get distance to target
fun getDistance(): Float

// Listen to target changes
fun listenToKeyEvents(element: HTMLElement)

// Dispose event listeners
fun dispose()
```

### Events

```kotlin
controls.addEventListener("change") {
    renderer.render(scene, camera)
}

controls.addEventListener("start") {
    // Interaction started
}

controls.addEventListener("end") {
    // Interaction ended
}
```

### Example

```kotlin
val controls = OrbitControls(camera, renderer.domElement)
controls.enableDamping = true
controls.dampingFactor = 0.05f
controls.minDistance = 2f
controls.maxDistance = 50f
controls.maxPolarAngle = PI / 2  // Don't go below ground

// Animate
fun animate() {
    controls.update()  // Required for damping
    renderer.render(scene, camera)
}

// Focus on object
controls.target.copy(mesh.position)
controls.update()
```

---

## MapControls

Extends OrbitControls for map-style navigation (pan with left mouse).

### Constructor

```kotlin
class MapControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Differences from OrbitControls

- Left mouse button pans
- Right mouse button rotates
- Good for top-down views

### Example

```kotlin
val controls = MapControls(camera, renderer.domElement)
controls.enableDamping = true
controls.screenSpacePanning = false  // Pan parallel to ground
controls.maxPolarAngle = PI / 2
```

---

## TrackballControls

Unconstrained rotation controls (no up direction).

### Constructor

```kotlin
class TrackballControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable controls |
| `rotateSpeed` | `Float` | `1.0` | Rotation speed |
| `zoomSpeed` | `Float` | `1.2` | Zoom speed |
| `panSpeed` | `Float` | `0.3` | Pan speed |
| `noRotate` | `Boolean` | `false` | Disable rotation |
| `noZoom` | `Boolean` | `false` | Disable zoom |
| `noPan` | `Boolean` | `false` | Disable pan |
| `staticMoving` | `Boolean` | `false` | No inertia |
| `dynamicDampingFactor` | `Float` | `0.2` | Inertia damping |
| `minDistance` | `Float` | `0` | Min distance |
| `maxDistance` | `Float` | `Infinity` | Max distance |

### Methods

```kotlin
fun update()
fun reset()
fun dispose()
```

---

## FlyControls

Flight simulator-style controls.

### Constructor

```kotlin
class FlyControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `movementSpeed` | `Float` | `1.0` | Movement speed |
| `rollSpeed` | `Float` | `0.005` | Roll speed |
| `dragToLook` | `Boolean` | `false` | Require drag to look |
| `autoForward` | `Boolean` | `false` | Always move forward |

### Methods

```kotlin
fun update(delta: Float)
fun dispose()
```

### Example

```kotlin
val controls = FlyControls(camera, renderer.domElement)
controls.movementSpeed = 10f
controls.rollSpeed = PI / 24

fun animate(deltaTime: Float) {
    controls.update(deltaTime)
    renderer.render(scene, camera)
}
```

---

## FirstPersonControls

First-person shooter-style controls.

### Constructor

```kotlin
class FirstPersonControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable controls |
| `movementSpeed` | `Float` | `1.0` | Movement speed |
| `lookSpeed` | `Float` | `0.005` | Look sensitivity |
| `lookVertical` | `Boolean` | `true` | Allow vertical look |
| `autoForward` | `Boolean` | `false` | Auto move forward |
| `activeLook` | `Boolean` | `true` | Mouse look |
| `heightSpeed` | `Boolean` | `false` | Height affects speed |
| `heightCoef` | `Float` | `1.0` | Height coefficient |
| `heightMin` | `Float` | `0` | Minimum height |
| `heightMax` | `Float` | `1.0` | Maximum height |
| `constrainVertical` | `Boolean` | `false` | Limit vertical angle |
| `verticalMin` | `Float` | `0` | Min vertical angle |
| `verticalMax` | `Float` | `PI` | Max vertical angle |

### Methods

```kotlin
fun update(delta: Float)
fun lookAt(target: Vector3)
fun dispose()
```

---

## PointerLockControls

First-person controls with pointer lock (mouse capture).

### Constructor

```kotlin
class PointerLockControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `isLocked` | `Boolean` | `false` | Is pointer locked |
| `minPolarAngle` | `Float` | `0` | Min vertical angle |
| `maxPolarAngle` | `Float` | `PI` | Max vertical angle |

### Methods

```kotlin
// Lock pointer
fun lock()

// Unlock pointer
fun unlock()

// Connect/disconnect events
fun connect()
fun disconnect()

// Get look direction
fun getDirection(target: Vector3): Vector3

// Movement
fun moveForward(distance: Float)
fun moveRight(distance: Float)

fun dispose()
```

### Events

```kotlin
controls.addEventListener("lock") {
    instructions.style.display = "none"
}

controls.addEventListener("unlock") {
    instructions.style.display = "block"
}
```

### Example

```kotlin
val controls = PointerLockControls(camera, document.body)
scene.add(controls.getObject())  // Add camera to scene

// Click to lock
document.body.addEventListener("click") {
    controls.lock()
}

// Movement
val velocity = Vector3()
val direction = Vector3()

var moveForward = false
var moveBackward = false
var moveLeft = false
var moveRight = false

document.addEventListener("keydown") { event ->
    when (event.code) {
        "KeyW" -> moveForward = true
        "KeyS" -> moveBackward = true
        "KeyA" -> moveLeft = true
        "KeyD" -> moveRight = true
    }
}

document.addEventListener("keyup") { event ->
    when (event.code) {
        "KeyW" -> moveForward = false
        "KeyS" -> moveBackward = false
        "KeyA" -> moveLeft = false
        "KeyD" -> moveRight = false
    }
}

fun animate(delta: Float) {
    if (controls.isLocked) {
        direction.z = (if (moveForward) 1 else 0) - (if (moveBackward) 1 else 0)
        direction.x = (if (moveRight) 1 else 0) - (if (moveLeft) 1 else 0)
        direction.normalize()
        
        velocity.x -= velocity.x * 10f * delta
        velocity.z -= velocity.z * 10f * delta
        
        if (moveForward || moveBackward) velocity.z -= direction.z * 400f * delta
        if (moveLeft || moveRight) velocity.x -= direction.x * 400f * delta
        
        controls.moveRight(-velocity.x * delta)
        controls.moveForward(-velocity.z * delta)
    }
    
    renderer.render(scene, camera)
}
```

---

## TransformControls

Gizmo for translating, rotating, and scaling objects.

### Constructor

```kotlin
class TransformControls(
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable controls |
| `mode` | `String` | `"translate"` | Transform mode |
| `space` | `String` | `"world"` | Coordinate space |
| `size` | `Float` | `1` | Gizmo size |
| `axis` | `String?` | `null` | Active axis |
| `showX` | `Boolean` | `true` | Show X axis |
| `showY` | `Boolean` | `true` | Show Y axis |
| `showZ` | `Boolean` | `true` | Show Z axis |
| `translationSnap` | `Float?` | `null` | Translation snap |
| `rotationSnap` | `Float?` | `null` | Rotation snap |
| `scaleSnap` | `Float?` | `null` | Scale snap |
| `dragging` | `Boolean` | `false` | Is dragging |

### Modes

```kotlin
// Translation (move)
controls.setMode("translate")

// Rotation
controls.setMode("rotate")

// Scale
controls.setMode("scale")
```

### Spaces

```kotlin
// World space
controls.setSpace("world")

// Local space (object-relative)
controls.setSpace("local")
```

### Methods

```kotlin
// Attach to object
fun attach(object3d: Object3D): TransformControls

// Detach from object
fun detach(): TransformControls

// Set mode
fun setMode(mode: String)

// Set space
fun setSpace(space: String)

// Set size
fun setSize(size: Float)

// Get raycaster
fun getRaycaster(): Raycaster

fun dispose()
```

### Events

```kotlin
controls.addEventListener("change") {
    renderer.render(scene, camera)
}

controls.addEventListener("dragging-changed") { event ->
    orbitControls.enabled = !event.value
}

controls.addEventListener("objectChange") {
    // Object was transformed
}
```

### Example

```kotlin
val transformControls = TransformControls(camera, renderer.domElement)
scene.add(transformControls)

// Attach to selected object
transformControls.attach(selectedMesh)

// Keyboard shortcuts
document.addEventListener("keydown") { event ->
    when (event.key) {
        "g" -> transformControls.setMode("translate")
        "r" -> transformControls.setMode("rotate")
        "s" -> transformControls.setMode("scale")
        "x" -> transformControls.showX = !transformControls.showX
        "y" -> transformControls.showY = !transformControls.showY
        "z" -> transformControls.showZ = !transformControls.showZ
        " " -> transformControls.setSpace(
            if (transformControls.space == "local") "world" else "local"
        )
    }
}

// Disable orbit controls while transforming
transformControls.addEventListener("dragging-changed") { event ->
    orbitControls.enabled = !event.value
}
```

---

## DragControls

Drag objects in the scene.

### Constructor

```kotlin
class DragControls(
    objects: List<Object3D>,
    camera: Camera,
    domElement: HTMLElement
)
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `true` | Enable controls |
| `transformGroup` | `Boolean` | `false` | Transform group |

### Events

```kotlin
controls.addEventListener("dragstart") { event ->
    event.`object`.material.emissive.set(0xaaaaaa)
}

controls.addEventListener("drag") { event ->
    // Object being dragged
}

controls.addEventListener("dragend") { event ->
    event.`object`.material.emissive.set(0x000000)
}

controls.addEventListener("hoveron") { event ->
    // Hovering over object
}

controls.addEventListener("hoveroff") { event ->
    // No longer hovering
}
```

### Example

```kotlin
val draggableObjects = listOf(cube1, cube2, cube3)
val dragControls = DragControls(draggableObjects, camera, renderer.domElement)

// Disable orbit while dragging
dragControls.addEventListener("dragstart") {
    orbitControls.enabled = false
}

dragControls.addEventListener("dragend") {
    orbitControls.enabled = true
}
```

---

## ArcballControls

Arcball-style rotation controls.

### Constructor

```kotlin
class ArcballControls(
    camera: Camera,
    domElement: HTMLElement,
    scene: Scene? = null
)
```

### Properties

Similar to OrbitControls with additional:

| Property | Type | Description |
|----------|------|-------------|
| `cursorZoom` | `Boolean` | Zoom at cursor position |
| `scaleFactor` | `Float` | Zoom scale factor |
| `focusAnimationTime` | `Float` | Focus animation duration |

---

## See Also

- [Camera API](camera.md) - Camera types
- [Core API](core.md) - Vector3, Raycaster
- [Examples](../examples/basic-usage.md) - Control examples
