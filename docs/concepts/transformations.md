# Transformations

Understanding how to position, rotate, and scale objects in 3D space.

## Overview

Every Object3D has three fundamental transform properties:

- **Position**: Where the object is located (Vector3)
- **Rotation**: How the object is oriented (Euler or Quaternion)
- **Scale**: How large the object is (Vector3)

```kotlin
val mesh = Mesh(geometry, material)

mesh.position.set(1f, 2f, 3f)
mesh.rotation.y = PI / 2
mesh.scale.set(2f, 2f, 2f)
```

## Position

Position is a `Vector3` representing the object's location relative to its parent.

### Setting Position

```kotlin
// Set all components
mesh.position.set(x, y, z)

// Set individual components
mesh.position.x = 5f
mesh.position.y = 0f
mesh.position.z = -3f

// Copy from another vector
mesh.position.copy(otherPosition)

// Add to current position
mesh.position.add(offset)
```

### World vs Local Position

```kotlin
// Local position (relative to parent)
val local = mesh.position  // Vector3

// World position (absolute)
val world = mesh.getWorldPosition(Vector3())  // Allocates or reuses target
```

### Translation

```kotlin
// Move along local axes
mesh.translateX(distance)  // Local X axis
mesh.translateY(distance)  // Local Y axis
mesh.translateZ(distance)  // Local Z axis

// Move along arbitrary axis
mesh.translateOnAxis(axis, distance)
```

## Rotation

Rotation can be represented as Euler angles or Quaternions.

### Euler Angles

Euler angles are intuitive but can suffer from gimbal lock.

```kotlin
// Set rotation (radians)
mesh.rotation.x = PI / 4  // 45° around X
mesh.rotation.y = PI / 2  // 90° around Y
mesh.rotation.z = 0f

// Set all at once
mesh.rotation.set(x, y, z)

// Rotation order matters
mesh.rotation.order = EulerOrder.YXZ  // Default is XYZ
```

### Quaternions

Quaternions avoid gimbal lock and interpolate smoothly.

```kotlin
// Set from axis and angle
mesh.quaternion.setFromAxisAngle(Vector3.UP, angle)

// Set from Euler
mesh.quaternion.setFromEuler(euler)

// Set from rotation matrix
mesh.quaternion.setFromRotationMatrix(matrix)

// Combine rotations
mesh.quaternion.multiply(otherQuaternion)

// Interpolate (smooth rotation)
mesh.quaternion.slerp(targetQuaternion, t)  // t = 0 to 1
```

### Look At

Point an object at a target:

```kotlin
// Look at world position
mesh.lookAt(target)
mesh.lookAt(x, y, z)

// Cameras typically look down -Z
camera.lookAt(Vector3.ZERO)
```

### Rotation Methods

```kotlin
// Rotate around local axes
mesh.rotateX(angle)
mesh.rotateY(angle)
mesh.rotateZ(angle)

// Rotate around arbitrary local axis
mesh.rotateOnAxis(axis, angle)

// Rotate around world axis
mesh.rotateOnWorldAxis(Vector3.UP, angle)
```

## Scale

Scale is a `Vector3` multiplier for the object's size.

```kotlin
// Uniform scale
mesh.scale.setScalar(2f)  // 2x in all directions

// Non-uniform scale
mesh.scale.set(2f, 1f, 0.5f)  // Wide and flat

// Individual axes
mesh.scale.x = 1.5f
```

### Scale Inheritance

Child scale combines with parent scale:

```kotlin
parent.scale.set(2f, 2f, 2f)
child.scale.set(0.5f, 0.5f, 0.5f)
// Child appears at 1x scale in world (2 * 0.5 = 1)
```

## Transform Matrices

Internally, transforms are represented as 4x4 matrices.

### Local Matrix

```kotlin
// Auto-updated from position/rotation/scale
mesh.matrix

// Manual composition
mesh.matrixAutoUpdate = false
mesh.matrix.compose(position, quaternion, scale)
```

### World Matrix

```kotlin
// Includes all parent transforms
mesh.matrixWorld

// Force update
mesh.updateMatrixWorld(force = true)
```

### Decomposition

Extract position/rotation/scale from a matrix:

```kotlin
val position = Vector3()
val quaternion = Quaternion()
val scale = Vector3()

matrix.decompose(position, quaternion, scale)
```

## Coordinate Spaces

### Local Space

Relative to the object's parent:

```kotlin
// Position in parent's coordinate system
mesh.position

// Direction in local space
val forward = Vector3(0f, 0f, 1f)  // +Z is forward in local space
```

### World Space

Absolute coordinates in the scene:

```kotlin
// Convert local to world
val worldPoint = localPoint.clone()
mesh.localToWorld(worldPoint)

// Convert world to local
val localPoint = worldPoint.clone()
mesh.worldToLocal(localPoint)
```

### Screen Space

2D coordinates on the viewport:

```kotlin
// World to screen (normalized device coordinates)
val screenPos = worldPos.clone().project(camera)

// Screen to world ray
val raycaster = Raycaster()
raycaster.setFromCamera(mouseNDC, camera)
```

## Common Patterns

### Orbiting

```kotlin
var angle = 0f
val radius = 5f
val center = Vector3()

fun animate(deltaTime: Float) {
    angle += deltaTime
    mesh.position.x = center.x + cos(angle) * radius
    mesh.position.z = center.z + sin(angle) * radius
    mesh.lookAt(center)
}
```

### Following a Path

```kotlin
val path = CatmullRomCurve3(points)
var t = 0f

fun animate(deltaTime: Float) {
    t = (t + deltaTime * speed) % 1f
    
    // Position on curve
    path.getPoint(t, mesh.position)
    
    // Orientation along curve
    val tangent = path.getTangent(t)
    mesh.lookAt(mesh.position.clone().add(tangent))
}
```

### Smooth Movement

```kotlin
val targetPosition = Vector3()
val lerpFactor = 0.1f

fun animate() {
    mesh.position.lerp(targetPosition, lerpFactor)
}
```

### Smooth Rotation

```kotlin
val targetQuaternion = Quaternion()
val slerpFactor = 0.1f

fun animate() {
    mesh.quaternion.slerp(targetQuaternion, slerpFactor)
}
```

### Billboard (Face Camera)

```kotlin
fun animate() {
    // Full billboard
    mesh.quaternion.copy(camera.quaternion)
    
    // Y-axis only (cylindrical)
    mesh.rotation.y = atan2(
        camera.position.x - mesh.position.x,
        camera.position.z - mesh.position.z
    )
}
```

### Pivot Point

By default, objects rotate around their origin. To change the pivot:

```kotlin
// Method 1: Offset geometry
geometry.translate(0f, -0.5f, 0f)  // Move pivot up

// Method 2: Use a parent group
val pivot = Group()
pivot.position.copy(desiredPivot)

val mesh = Mesh(geometry, material)
mesh.position.sub(desiredPivot)  // Offset mesh

pivot.add(mesh)
scene.add(pivot)

// Rotate around the pivot
pivot.rotation.y += 0.01f
```

## Transform Helpers

### AxesHelper

Visualize local axes:

```kotlin
val axes = AxesHelper(5f)  // 5 unit length
mesh.add(axes)
```

### ArrowHelper

Visualize a direction:

```kotlin
val arrow = ArrowHelper(
    direction = Vector3.UP,
    origin = Vector3.ZERO,
    length = 2f,
    color = Color.RED
)
scene.add(arrow)
```

### BoxHelper

Visualize bounding box:

```kotlin
val boxHelper = BoxHelper(mesh, Color.YELLOW)
scene.add(boxHelper)
```

## Performance Tips

1. **Minimize matrix updates**: Set `matrixAutoUpdate = false` for static objects
2. **Batch transforms**: Update multiple objects before calling `updateMatrixWorld`
3. **Use quaternions**: More efficient than Euler for runtime rotations
4. **Avoid deep hierarchies**: Each level adds matrix multiplication

```kotlin
// For many static objects
mesh.matrixAutoUpdate = false
mesh.updateMatrix()

// Only update when needed
if (needsUpdate) {
    mesh.position.copy(newPosition)
    mesh.updateMatrix()
}
```

## See Also

- [Scene Graph](scene-graph.md) - Transform hierarchy
- [Core API Reference](../api-reference/core.md) - Vector3, Matrix4, Quaternion
- [Animation](animation.md) - Animated transforms
