# Core Module API Reference

The core module provides fundamental math primitives, scene graph system, and utilities that form the foundation of
Materia.

## Packages

- `io.materia.core.math` - Mathematical primitives
- `io.materia.core.scene` - Scene graph system
- `io.materia.core.utils` - Utility classes and helpers
- `io.materia.core.platform` - Platform abstraction layer

## Math Primitives

### Vector3

Three-component vector for positions, directions, and scales.

```kotlin
// Construction
val v1 = Vector3(x = 1f, y = 2f, z = 3f)
val v2 = Vector3(5f) // All components = 5
val v3 = Vector3()   // Zero vector

// Arithmetic (mutable)
v1.add(v2)           // v1 += v2
v1.multiply(2f)      // v1 *= 2
v1.normalize()       // Make unit length

// Arithmetic (immutable)
val sum = v1 + v2
val scaled = v1 * 2f
val normalized = v1.normalized()

// Vector operations
val dot = v1.dot(v2)
val cross = v1.clone().cross(v2)
val length = v1.length()
val distance = v1.distance(v2)

// Transformations
v1.applyMatrix4(matrix)
v1.applyQuaternion(quaternion)

// Constants
Vector3.ZERO     // (0, 0, 0)
Vector3.ONE      // (1, 1, 1)
Vector3.UP       // (0, 1, 0)
Vector3.RIGHT    // (1, 0, 0)
Vector3.FORWARD  // (0, 0, -1)
```

**Properties:**

- `x: Float` - X component
- `y: Float` - Y component
- `z: Float` - Z component

**Key Methods:**

- `set(x, y, z): Vector3` - Set components
- `add(v): Vector3` - Add vector
- `multiply(s): Vector3` - Multiply by scalar
- `normalize(): Vector3` - Normalize to unit length
- `dot(v): Float` - Dot product
- `cross(v): Vector3` - Cross product
- `length(): Float` - Vector length
- `distance(v): Float` - Distance to vector
- `clone(): Vector3` - Create copy

### Vector2

Two-component vector for UV coordinates and 2D positions.

```kotlin
val uv = Vector2(0.5f, 0.5f)
uv.add(Vector2(0.1f, 0.1f))
```

### Vector4

Four-component vector for RGBA colors and homogeneous coordinates.

```kotlin
val rgba = Vector4(1f, 0f, 0f, 1f) // Red with full opacity
```

### Matrix4

4x4 transformation matrix in column-major order.

```kotlin
// Construction
val m1 = Matrix4()  // Identity matrix
val m2 = Matrix4.translation(x, y, z)
val m3 = Matrix4.rotationY(angle)
val m4 = Matrix4.scale(sx, sy, sz)

// Operations
m1.multiply(m2)      // m1 = m1 * m2
m1.invert()          // Invert matrix
m1.transpose()       // Transpose matrix

// Transformations
m1.makeTranslation(x, y, z)
m1.makeRotationX(angle)
m1.makeScale(x, y, z)
m1.compose(position, quaternion, scale)
m1.decompose(position, quaternion, scale)

// Projections
m1.makePerspective(left, right, top, bottom, near, far)
m1.makeOrthographic(left, right, top, bottom, near, far)

// Utility
val det = m1.determinant()
val pos = m1.getPosition()
val scale = m1.getScale()
```

**Properties:**

- `elements: FloatArray` - 16-element array in column-major order

**Key Methods:**

- `identity(): Matrix4` - Reset to identity
- `multiply(m): Matrix4` - Matrix multiplication
- `invert(): Matrix4` - Invert matrix
- `transpose(): Matrix4` - Transpose matrix
- `compose(p, q, s): Matrix4` - Create from position, rotation, scale
- `decompose(p, q, s): Matrix4` - Extract position, rotation, scale
- `clone(): Matrix4` - Create copy

### Matrix3

3x3 matrix for normal transformations and 2D transforms.

```kotlin
val m = Matrix3()
m.setFromMatrix4(matrix4)  // Extract rotation from 4x4 matrix
m.invert()
```

### Quaternion

Rotation representation using quaternions.

```kotlin
// Construction
val q1 = Quaternion()  // Identity rotation
val q2 = Quaternion.fromAxisAngle(axis, angle)
val q3 = Quaternion.fromEuler(euler)

// Operations
q1.multiply(q2)      // Combine rotations
q1.slerp(q2, t)      // Spherical interpolation
q1.conjugate()       // Inverse rotation
q1.normalize()       // Normalize

// Conversions
q1.setFromEuler(euler)
q1.setFromAxisAngle(axis, angle)
q1.setFromRotationMatrix(matrix)
```

**Properties:**

- `x: Float` - X component
- `y: Float` - Y component
- `z: Float` - Z component
- `w: Float` - W component (scalar)

**Key Methods:**

- `multiply(q): Quaternion` - Combine rotations
- `slerp(q, t): Quaternion` - Spherical linear interpolation
- `setFromEuler(e): Quaternion` - Set from Euler angles
- `setFromAxisAngle(axis, angle): Quaternion` - Set from axis and angle
- `clone(): Quaternion` - Create copy

### Euler

Euler angle rotation (pitch, yaw, roll).

```kotlin
// Construction
val euler = Euler(x = 0f, y = PI.toFloat() / 2f, z = 0f)
euler.order = RotationOrder.YXZ

// Conversions
euler.setFromQuaternion(quaternion)
euler.setFromRotationMatrix(matrix)
```

**Properties:**

- `x: Float` - Rotation around X axis (pitch)
- `y: Float` - Rotation around Y axis (yaw)
- `z: Float` - Rotation around Z axis (roll)
- `order: RotationOrder` - Rotation order (default: XYZ)

### Color

RGB color representation.

```kotlin
// Construction
val red = Color(0xff0000)
val green = Color(0.0f, 1.0f, 0.0f)
val blue = Color("#0000ff")

// Operations
red.lerp(blue, 0.5f)    // Linear interpolation
red.multiply(0.5f)       // Darken
red.add(green)           // Additive color

// Conversions
val hex = red.getHex()           // 0xff0000
val style = red.getStyle()       // "#ff0000"
val hsl = red.getHSL()           // HSL representation
```

**Properties:**

- `r: Float` - Red component (0-1)
- `g: Float` - Green component (0-1)
- `b: Float` - Blue component (0-1)

**Key Methods:**

- `set(color): Color` - Set from another color
- `setHex(hex): Color` - Set from hex value
- `setRGB(r, g, b): Color` - Set from RGB
- `setHSL(h, s, l): Color` - Set from HSL
- `lerp(c, t): Color` - Linear interpolation
- `clone(): Color` - Create copy

### Box3

Axis-aligned bounding box.

```kotlin
val box = Box3()
box.setFromPoints(points)
box.expandByPoint(point)
box.containsPoint(point)
box.intersectsBox(otherBox)
```

### Sphere

Bounding sphere.

```kotlin
val sphere = Sphere(center, radius)
sphere.containsPoint(point)
sphere.intersectsSphere(otherSphere)
```

### Plane

Mathematical plane for clipping and raycasting.

```kotlin
val plane = Plane(normal, constant)
plane.distanceToPoint(point)
plane.projectPoint(point, target)
```

### Ray

Ray for raycasting.

```kotlin
val ray = Ray(origin, direction)
val intersection = ray.intersectBox(box)
val distance = ray.distanceToPoint(point)
```

## Scene Graph

### Object3D

Base class for all 3D objects in the scene.

```kotlin
// Transformation
object3d.position.set(x, y, z)
object3d.rotation.y = PI.toFloat() / 2f
object3d.scale.set(2f, 2f, 2f)

// Hierarchy
parent.add(child)
child.removeFromParent()
parent.remove(child)

// Traversal
object3d.traverse { obj ->
    println("Object: ${obj.name}")
}

// World transforms
val worldPos = object3d.getWorldPosition()
val worldQuat = object3d.getWorldQuaternion()
val worldScale = object3d.getWorldScale()

// Matrix updates
object3d.updateMatrix()
object3d.updateMatrixWorld()
```

**Properties:**

- `id: Int` - Unique identifier
- `name: String` - Object name
- `position: Vector3` - Local position
- `rotation: Euler` - Local rotation (Euler angles)
- `quaternion: Quaternion` - Local rotation (quaternion)
- `scale: Vector3` - Local scale
- `matrix: Matrix4` - Local transformation matrix
- `matrixWorld: Matrix4` - World transformation matrix
- `parent: Object3D?` - Parent object
- `children: List<Object3D>` - Child objects
- `visible: Boolean` - Visibility flag
- `castShadow: Boolean` - Cast shadows
- `receiveShadow: Boolean` - Receive shadows
- `layers: Layers` - Layer membership

**Key Methods:**

- `add(obj): Object3D` - Add child
- `remove(obj): Object3D` - Remove child
- `traverse(callback)` - Visit all descendants
- `getWorldPosition(target): Vector3` - Get world position
- `lookAt(target): Object3D` - Point towards target
- `updateMatrix()` - Update local matrix
- `updateMatrixWorld()` - Update world matrix
- `clone(): Object3D` - Create copy

### Scene

Root container for all 3D objects.

```kotlin
val scene = Scene()
scene.background = Color(0x000000)
scene.fog = Fog(0xffffff, near = 1f, far = 1000f)
scene.add(mesh, light, camera)
```

**Properties:**

- `background: Color?` - Background color
- `environment: Texture?` - Environment map
- `fog: Fog?` - Scene fog
- `overrideMaterial: Material?` - Override all materials

### Group

Container for organizing objects without rendering.

```kotlin
val group = Group()
group.name = "MyGroup"
group.add(mesh1, mesh2, mesh3)
scene.add(group)

// Transform all children together
group.rotation.y = PI.toFloat() / 4f
```

## Utilities

### Layers

Bit mask for selective rendering and raycasting.

```kotlin
val layers = Layers()
layers.enable(0)  // Enable layer 0
layers.disable(1) // Disable layer 1
layers.test(otherLayers)  // Test if any layer matches
```

### Event

Event system for object callbacks.

```kotlin
object3d.onBeforeRender = { obj ->
    println("Rendering ${obj.name}")
}

object3d.onAfterRender = { obj ->
    println("Rendered ${obj.name}")
}
```

## Performance Considerations

### Object Pooling

```kotlin
// Reuse vectors instead of creating new ones
val tempVector = Vector3()

fun computeDirection(from: Vector3, to: Vector3): Vector3 {
    return tempVector.copy(to).subtract(from).normalize()
}
```

### Matrix Updates

```kotlin
// Disable auto-update for static objects
staticObject.matrixAutoUpdate = false
staticObject.updateMatrix()  // Manual update when needed
```

### Dirty Flags

```kotlin
// Objects track changes automatically
object3d.position.x = 10f  // Marks matrix as dirty
object3d.updateMatrixWorld()  // Updates only if dirty
```

## See Also

- [Renderer Module](../renderer/README.md)
- [Scene Graph Guide](../../guides/scene-graph.md)
- [Performance Guide](../../architecture/performance.md)
