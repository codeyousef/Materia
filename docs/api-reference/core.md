# Core API Reference

The core module provides foundational classes for 3D math, scene graph management, and utilities.

## Overview

```kotlin
import io.materia.core.*
```

---

## Vector3

A 3D vector representing positions, directions, or scales.

### Constructor

```kotlin
class Vector3(x: Float = 0f, y: Float = 0f, z: Float = 0f)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `x` | `Float` | X component |
| `y` | `Float` | Y component |
| `z` | `Float` | Z component |

### Static Properties

```kotlin
Vector3.ZERO      // (0, 0, 0)
Vector3.ONE       // (1, 1, 1)
Vector3.UP        // (0, 1, 0)
Vector3.DOWN      // (0, -1, 0)
Vector3.LEFT      // (-1, 0, 0)
Vector3.RIGHT     // (1, 0, 0)
Vector3.FORWARD   // (0, 0, -1)
Vector3.BACK      // (0, 0, 1)
```

### Methods

#### Setting Values

```kotlin
// Set all components
fun set(x: Float, y: Float, z: Float): Vector3

// Set from another vector
fun copy(v: Vector3): Vector3

// Set from array
fun fromArray(array: FloatArray, offset: Int = 0): Vector3

// Clone to new instance
fun clone(): Vector3
```

#### Arithmetic Operations (Mutable)

```kotlin
// Add vector in-place
fun add(v: Vector3): Vector3

// Add scalar to all components
fun addScalar(s: Float): Vector3

// Subtract vector in-place
fun sub(v: Vector3): Vector3

// Multiply component-wise
fun multiply(v: Vector3): Vector3

// Multiply by scalar
fun multiplyScalar(s: Float): Vector3

// Divide component-wise
fun divide(v: Vector3): Vector3

// Divide by scalar
fun divideScalar(s: Float): Vector3

// Negate all components
fun negate(): Vector3
```

#### Arithmetic Operators (Immutable)

```kotlin
operator fun plus(v: Vector3): Vector3
operator fun minus(v: Vector3): Vector3
operator fun times(scalar: Float): Vector3
operator fun div(scalar: Float): Vector3
operator fun unaryMinus(): Vector3
```

#### Vector Operations

```kotlin
// Dot product
fun dot(v: Vector3): Float

// Cross product (modifies this vector)
fun cross(v: Vector3): Vector3

// Cross product (returns new vector)
fun crossed(v: Vector3): Vector3

// Length (magnitude)
fun length(): Float

// Squared length (faster, no sqrt)
fun lengthSq(): Float

// Distance to another point
fun distanceTo(v: Vector3): Float

// Squared distance (faster)
fun distanceToSquared(v: Vector3): Float

// Normalize to unit length
fun normalize(): Vector3

// Set to specific length
fun setLength(length: Float): Vector3

// Linear interpolation
fun lerp(v: Vector3, alpha: Float): Vector3

// Clamp components
fun clamp(min: Vector3, max: Vector3): Vector3
```

#### Transform Operations

```kotlin
// Apply matrix4 transformation
fun applyMatrix4(m: Matrix4): Vector3

// Apply quaternion rotation
fun applyQuaternion(q: Quaternion): Vector3

// Apply euler rotation
fun applyEuler(euler: Euler): Vector3

// Project onto another vector
fun project(v: Vector3): Vector3

// Reflect off a normal
fun reflect(normal: Vector3): Vector3
```

### Example

```kotlin
val position = Vector3(1f, 2f, 3f)
val direction = Vector3(0f, 0f, -1f)

// Move position by direction * 5
position.add(direction.clone().multiplyScalar(5f))

// Get distance
val dist = position.distanceTo(Vector3.ZERO)

// Normalize direction
direction.normalize()

// Using operators
val newPos = position + direction * 10f
```

---

## Matrix4

A 4x4 transformation matrix for 3D transforms.

### Constructor

```kotlin
class Matrix4()  // Identity matrix
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `elements` | `FloatArray` | 16 matrix elements (column-major) |

### Static Properties

```kotlin
Matrix4.IDENTITY  // Identity matrix
```

### Methods

#### Setting Values

```kotlin
// Set to identity
fun identity(): Matrix4

// Copy from another matrix
fun copy(m: Matrix4): Matrix4

// Clone to new instance
fun clone(): Matrix4

// Set all 16 elements
fun set(
    n11: Float, n12: Float, n13: Float, n14: Float,
    n21: Float, n22: Float, n23: Float, n24: Float,
    n31: Float, n32: Float, n33: Float, n34: Float,
    n41: Float, n42: Float, n43: Float, n44: Float
): Matrix4
```

#### Transform Composition

```kotlin
// Create translation matrix
fun makeTranslation(x: Float, y: Float, z: Float): Matrix4

// Create rotation from quaternion
fun makeRotationFromQuaternion(q: Quaternion): Matrix4

// Create rotation from euler
fun makeRotationFromEuler(euler: Euler): Matrix4

// Create scale matrix
fun makeScale(x: Float, y: Float, z: Float): Matrix4

// Compose from position, rotation, scale
fun compose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4

// Decompose to position, rotation, scale
fun decompose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4
```

#### Matrix Operations

```kotlin
// Multiply matrices
fun multiply(m: Matrix4): Matrix4

// Pre-multiply (this = m * this)
fun premultiply(m: Matrix4): Matrix4

// Invert matrix
fun invert(): Matrix4

// Get inverse (non-mutating)
fun getInverse(): Matrix4

// Transpose matrix
fun transpose(): Matrix4

// Determinant
fun determinant(): Float
```

#### View/Projection

```kotlin
// Create look-at matrix
fun lookAt(eye: Vector3, target: Vector3, up: Vector3): Matrix4

// Create perspective projection
fun makePerspective(
    fov: Float,
    aspect: Float,
    near: Float,
    far: Float
): Matrix4

// Create orthographic projection
fun makeOrthographic(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    near: Float,
    far: Float
): Matrix4
```

### Example

```kotlin
// Create model matrix
val modelMatrix = Matrix4()
modelMatrix.compose(
    position = Vector3(0f, 1f, 0f),
    quaternion = Quaternion().setFromAxisAngle(Vector3.UP, PI / 4),
    scale = Vector3(1f, 1f, 1f)
)

// Apply transform to a point
val worldPos = localPos.clone().applyMatrix4(modelMatrix)

// Create view-projection matrix
val viewMatrix = Matrix4().lookAt(cameraPos, target, Vector3.UP)
val projMatrix = Matrix4().makePerspective(75f, aspect, 0.1f, 1000f)
val vpMatrix = projMatrix.clone().multiply(viewMatrix)
```

---

## Quaternion

A quaternion for representing rotations without gimbal lock.

### Constructor

```kotlin
class Quaternion(
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    w: Float = 1f
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `x` | `Float` | X component |
| `y` | `Float` | Y component |
| `z` | `Float` | Z component |
| `w` | `Float` | W component (scalar) |

### Static Properties

```kotlin
Quaternion.IDENTITY  // (0, 0, 0, 1)
```

### Methods

#### Setting Values

```kotlin
// Set all components
fun set(x: Float, y: Float, z: Float, w: Float): Quaternion

// Copy from another quaternion
fun copy(q: Quaternion): Quaternion

// Clone to new instance
fun clone(): Quaternion

// Set to identity rotation
fun identity(): Quaternion
```

#### Creating Rotations

```kotlin
// From axis and angle (radians)
fun setFromAxisAngle(axis: Vector3, angle: Float): Quaternion

// From euler angles
fun setFromEuler(euler: Euler): Quaternion

// From rotation matrix
fun setFromRotationMatrix(m: Matrix4): Quaternion

// Look rotation
fun setFromUnitVectors(from: Vector3, to: Vector3): Quaternion
```

#### Operations

```kotlin
// Multiply quaternions (combine rotations)
fun multiply(q: Quaternion): Quaternion

// Pre-multiply
fun premultiply(q: Quaternion): Quaternion

// Conjugate (inverse for unit quaternions)
fun conjugate(): Quaternion

// Invert quaternion
fun invert(): Quaternion

// Normalize to unit quaternion
fun normalize(): Quaternion

// Length
fun length(): Float

// Dot product
fun dot(q: Quaternion): Float

// Spherical interpolation
fun slerp(q: Quaternion, t: Float): Quaternion
```

### Example

```kotlin
// Create rotation around Y axis
val rotation = Quaternion().setFromAxisAngle(Vector3.UP, PI / 2)

// Combine rotations
val combined = rotation.clone().multiply(otherRotation)

// Interpolate between rotations
val interpolated = startRotation.clone().slerp(endRotation, 0.5f)

// Apply to vector
val rotatedDir = direction.clone().applyQuaternion(rotation)
```

---

## Euler

Euler angles representing rotation as X, Y, Z rotations in a specific order.

### Constructor

```kotlin
class Euler(
    x: Float = 0f,
    y: Float = 0f,
    z: Float = 0f,
    order: EulerOrder = EulerOrder.XYZ
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `x` | `Float` | Rotation around X axis (radians) |
| `y` | `Float` | Rotation around Y axis (radians) |
| `z` | `Float` | Rotation around Z axis (radians) |
| `order` | `EulerOrder` | Rotation order (XYZ, YXZ, etc.) |

### Euler Orders

```kotlin
enum class EulerOrder {
    XYZ, YXZ, ZXY, ZYX, YZX, XZY
}
```

### Methods

```kotlin
// Set all values
fun set(x: Float, y: Float, z: Float, order: EulerOrder = this.order): Euler

// Copy from another Euler
fun copy(euler: Euler): Euler

// Clone to new instance
fun clone(): Euler

// Set from rotation matrix
fun setFromRotationMatrix(m: Matrix4, order: EulerOrder = this.order): Euler

// Set from quaternion
fun setFromQuaternion(q: Quaternion, order: EulerOrder = this.order): Euler
```

### Example

```kotlin
// Create rotation (90° around Y axis)
val euler = Euler(0f, PI / 2, 0f)

// Convert to quaternion
val quaternion = Quaternion().setFromEuler(euler)

// Apply to object
mesh.rotation.copy(euler)
```

---

## Color

RGB color representation.

### Constructor

```kotlin
class Color(r: Float = 1f, g: Float = 1f, b: Float = 1f)
class Color(hex: Int)  // From hex integer (0xRRGGBB)
class Color(hex: String)  // From hex string ("#RRGGBB")
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `r` | `Float` | Red component (0-1) |
| `g` | `Float` | Green component (0-1) |
| `b` | `Float` | Blue component (0-1) |

### Static Properties

```kotlin
Color.WHITE     // (1, 1, 1)
Color.BLACK     // (0, 0, 0)
Color.RED       // (1, 0, 0)
Color.GREEN     // (0, 1, 0)
Color.BLUE      // (0, 0, 1)
Color.YELLOW    // (1, 1, 0)
Color.CYAN      // (0, 1, 1)
Color.MAGENTA   // (1, 0, 1)
```

### Methods

```kotlin
// Set RGB values
fun set(r: Float, g: Float, b: Float): Color

// Set from hex integer
fun setHex(hex: Int): Color

// Set from HSL
fun setHSL(h: Float, s: Float, l: Float): Color

// Get hex integer
fun getHex(): Int

// Get HSL values
fun getHSL(): HSL

// Copy from another color
fun copy(c: Color): Color

// Clone
fun clone(): Color

// Linear interpolation
fun lerp(c: Color, alpha: Float): Color

// Multiply colors
fun multiply(c: Color): Color

// Multiply by scalar
fun multiplyScalar(s: Float): Color

// Convert to linear space
fun convertSRGBToLinear(): Color

// Convert to sRGB space
fun convertLinearToSRGB(): Color
```

### Example

```kotlin
// Create from hex
val red = Color(0xff0000)
val green = Color("#00ff00")

// Create from RGB
val blue = Color(0f, 0f, 1f)

// Blend colors
val blended = red.clone().lerp(blue, 0.5f)  // Purple

// Apply to material
material.color = Color(0x44aa88)
```

---

## Object3D

Base class for all 3D objects in the scene graph.

### Constructor

```kotlin
open class Object3D()
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `Int` | Unique identifier |
| `uuid` | `String` | UUID string |
| `name` | `String` | Optional name |
| `parent` | `Object3D?` | Parent in scene graph |
| `children` | `List<Object3D>` | Child objects |
| `position` | `Vector3` | Local position |
| `rotation` | `Euler` | Local rotation |
| `quaternion` | `Quaternion` | Local rotation as quaternion |
| `scale` | `Vector3` | Local scale |
| `visible` | `Boolean` | Visibility flag |
| `castShadow` | `Boolean` | Cast shadows |
| `receiveShadow` | `Boolean` | Receive shadows |
| `frustumCulled` | `Boolean` | Enable frustum culling |
| `renderOrder` | `Int` | Render order (for transparency) |
| `userData` | `MutableMap<String, Any?>` | Custom data storage |

### Transform Properties

```kotlin
// Local transform matrix (auto-updated)
val matrix: Matrix4

// World transform matrix (auto-updated)
val matrixWorld: Matrix4

// Control automatic matrix updates
var matrixAutoUpdate: Boolean
var matrixWorldNeedsUpdate: Boolean
```

### Methods

#### Hierarchy

```kotlin
// Add child object
fun add(object3d: Object3D): Object3D

// Remove child object
fun remove(object3d: Object3D): Object3D

// Remove from parent
fun removeFromParent(): Object3D

// Clear all children
fun clear(): Object3D

// Attach to new parent (preserving world transform)
fun attach(object3d: Object3D): Object3D

// Get object by name
fun getObjectByName(name: String): Object3D?

// Get object by ID
fun getObjectById(id: Int): Object3D?

// Traverse all descendants
fun traverse(callback: (Object3D) -> Unit)

// Traverse only visible objects
fun traverseVisible(callback: (Object3D) -> Unit)

// Traverse ancestors
fun traverseAncestors(callback: (Object3D) -> Unit)
```

#### Transform

```kotlin
// Apply matrix to local transform
fun applyMatrix4(matrix: Matrix4): Object3D

// Apply quaternion to rotation
fun applyQuaternion(q: Quaternion): Object3D

// Set rotation from axis and angle
fun setRotationFromAxisAngle(axis: Vector3, angle: Float): Object3D

// Set rotation from euler
fun setRotationFromEuler(euler: Euler): Object3D

// Set rotation from matrix
fun setRotationFromMatrix(m: Matrix4): Object3D

// Set rotation from quaternion
fun setRotationFromQuaternion(q: Quaternion): Object3D

// Rotate around local X axis
fun rotateX(angle: Float): Object3D

// Rotate around local Y axis
fun rotateY(angle: Float): Object3D

// Rotate around local Z axis
fun rotateZ(angle: Float): Object3D

// Rotate around arbitrary axis
fun rotateOnAxis(axis: Vector3, angle: Float): Object3D

// Rotate around world axis
fun rotateOnWorldAxis(axis: Vector3, angle: Float): Object3D

// Translate along local X axis
fun translateX(distance: Float): Object3D

// Translate along local Y axis
fun translateY(distance: Float): Object3D

// Translate along local Z axis
fun translateZ(distance: Float): Object3D

// Translate along arbitrary axis
fun translateOnAxis(axis: Vector3, distance: Float): Object3D

// Look at a point
fun lookAt(target: Vector3): Object3D
fun lookAt(x: Float, y: Float, z: Float): Object3D
```

#### World Space

```kotlin
// Get world position
fun getWorldPosition(target: Vector3 = Vector3()): Vector3

// Get world quaternion
fun getWorldQuaternion(target: Quaternion = Quaternion()): Quaternion

// Get world scale
fun getWorldScale(target: Vector3 = Vector3()): Vector3

// Get world direction (local Z axis in world space)
fun getWorldDirection(target: Vector3 = Vector3()): Vector3

// Convert local point to world space
fun localToWorld(vector: Vector3): Vector3

// Convert world point to local space
fun worldToLocal(vector: Vector3): Vector3
```

#### Lifecycle

```kotlin
// Update local matrix
fun updateMatrix()

// Update world matrix
fun updateMatrixWorld(force: Boolean = false)

// Clone object
fun clone(recursive: Boolean = true): Object3D

// Copy properties from another object
fun copy(source: Object3D, recursive: Boolean = true): Object3D
```

### Example

```kotlin
val group = Group()
group.position.set(0f, 1f, 0f)

val child = Mesh(geometry, material)
child.position.set(1f, 0f, 0f)  // Local position
group.add(child)

scene.add(group)

// Get world position of child
val worldPos = child.getWorldPosition()  // (1, 1, 0)

// Find object
val found = scene.getObjectByName("myMesh")

// Traverse hierarchy
scene.traverse { obj ->
    if (obj is Mesh) {
        obj.material.color = Color.RED
    }
}
```

---

## Scene

Root container for a 3D scene.

### Constructor

```kotlin
class Scene()
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `background` | `Color?` | Background color |
| `backgroundTexture` | `Texture?` | Background texture/skybox |
| `environment` | `Texture?` | Environment map for IBL |
| `fog` | `Fog?` | Scene fog |
| `overrideMaterial` | `Material?` | Override all materials |

### Example

```kotlin
val scene = Scene()
scene.background = Color(0x1a1a2e)

// Add fog
scene.fog = Fog(Color(0x1a1a2e), 10f, 100f)

// Set environment map for reflections
scene.environment = cubeTexture
```

---

## Group

Empty container for organizing objects.

### Constructor

```kotlin
class Group()
```

### Usage

```kotlin
// Group related objects
val robot = Group()
robot.name = "robot"

val body = Mesh(bodyGeometry, bodyMaterial)
val head = Mesh(headGeometry, headMaterial)
head.position.y = 1.5f

robot.add(body)
robot.add(head)

scene.add(robot)

// Transform whole group
robot.rotation.y = PI / 4
```

---

## Fog

Atmospheric fog that fades objects with distance.

### Linear Fog

```kotlin
class Fog(
    color: Color,
    near: Float,
    far: Float
)
```

| Property | Type | Description |
|----------|------|-------------|
| `color` | `Color` | Fog color |
| `near` | `Float` | Start distance |
| `far` | `Float` | End distance (fully fogged) |

### Exponential Fog

```kotlin
class FogExp2(
    color: Color,
    density: Float
)
```

| Property | Type | Description |
|----------|------|-------------|
| `color` | `Color` | Fog color |
| `density` | `Float` | Fog density |

### Example

```kotlin
// Linear fog
scene.fog = Fog(Color(0x000000), 10f, 100f)

// Exponential fog (more realistic)
scene.fog = FogExp2(Color(0x000000), 0.01f)
```

---

## Clock

High-resolution time tracking.

### Constructor

```kotlin
class Clock(autoStart: Boolean = true)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `running` | `Boolean` | Is clock running |
| `elapsedTime` | `Float` | Total elapsed time (seconds) |

### Methods

```kotlin
// Start the clock
fun start()

// Stop the clock
fun stop()

// Get time since last call to getDelta()
fun getDelta(): Float

// Get total elapsed time
fun getElapsedTime(): Float
```

### Example

```kotlin
val clock = Clock()

fun animate() {
    val delta = clock.getDelta()
    
    // Update animations
    mixer.update(delta)
    
    // Move object
    object.position.x += speed * delta
    
    renderer.render(scene, camera)
}
```

---

## See Also

- [Geometry API](geometry.md) - Shapes and mesh data
- [Material API](material.md) - Materials and shaders
- [Camera API](camera.md) - Cameras and projections
- [Animation API](animation.md) - Animation system
