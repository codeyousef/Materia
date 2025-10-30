# Core Math API Reference

The Materia math library provides fundamental mathematical primitives for 3D graphics programming.
All math classes are
designed for performance with operator overloading, method chaining, and careful memory management.

## Table of Contents

- [Vector3](#vector3) - 3D vectors
- [Vector2](#vector2) - 2D vectors
- [Vector4](#vector4) - 4D vectors
- [Matrix4](#matrix4) - 4x4 transformation matrices
- [Matrix3](#matrix3) - 3x3 matrices
- [Quaternion](#quaternion) - Rotation representation
- [Euler](#euler) - Euler angle rotations
- [Color](#color) - RGB/HSL color representation
- [Box3](#box3) - Axis-aligned bounding boxes
- [Sphere](#sphere) - Bounding spheres
- [Ray](#ray) - Rays for raycasting
- [Plane](#plane) - Mathematical planes

---

## Vector3

Three-component vector for positions, directions, scales, and more.

### Constructor

```kotlin
// Zero vector
val v1 = Vector3()

// Component values
val v2 = Vector3(x = 1f, y = 2f, z = 3f)

// Uniform scalar
val v3 = Vector3(5f) // (5, 5, 5)

// Copy constructor
val v4 = Vector3(v2)

// From Vector2
val v5 = Vector3(Vector2(1f, 2f), z = 3f)
```

### Properties

```kotlin
var x: Float        // X component
var y: Float        // Y component
var z: Float        // Z component
val normalized: Vector3  // Normalized copy (doesn't modify original)
```

### Basic Operations

```kotlin
val v = Vector3(1f, 2f, 3f)

// Set components
v.set(4f, 5f, 6f)
v.set(10f) // All components to 10
v.set(otherVector)

// Copy
v.copy(otherVector)

// Clone (creates new instance)
val clone = v.clone()
```

### Arithmetic Operations

```kotlin
val a = Vector3(1f, 2f, 3f)
val b = Vector3(4f, 5f, 6f)

// Mutable operations (modify in-place)
a.add(b)              // a = a + b
a.subtract(b)         // a = a - b
a.multiply(b)         // Component-wise: a = a * b
a.divide(b)           // Component-wise: a = a / b
a.multiply(2f)        // Scalar: a = a * 2
a.divide(2f)          // Scalar: a = a / 2
a.negate()            // a = -a

// Immutable operations (return new vector)
val sum = a + b
val diff = a - b
val product = a * b
val scaled = a * 2f
val divided = a / 2f
val negated = -a

// Compound assignment
a += b   // a = a + b
a -= b   // a = a - b
a *= 2f  // a = a * 2
```

### Vector Operations

```kotlin
val a = Vector3(1f, 0f, 0f)
val b = Vector3(0f, 1f, 0f)

// Dot product
val dot: Float = a.dot(b)  // 0.0

// Cross product (perpendicular vector)
val cross = a.clone().cross(b)  // Vector3(0, 0, 1)

// Cross product of two vectors
val c = Vector3().crossVectors(a, b)

// Length
val length: Float = a.length()
val lengthSq: Float = a.lengthSquared()

// Normalization (make length = 1)
a.normalize()  // Modifies in-place
val normalized = a.normalized()  // Returns new vector

// Distance
val dist: Float = a.distance(b)
val distSq: Float = a.distanceSquared(b)

// Three.js aliases
a.distanceTo(b)
a.distanceToSquared(b)
a.lengthSq()
```

### Interpolation

```kotlin
val start = Vector3(0f, 0f, 0f)
val end = Vector3(10f, 10f, 10f)

// Linear interpolation
start.lerp(end, 0.5f)  // Modifies start to (5, 5, 5)

// Interpolate between two vectors
val mid = Vector3().lerpVectors(start, end, 0.5f)

// Add/subtract vectors
val result = Vector3().addVectors(a, b)
val diff = Vector3().subVectors(a, b)
```

### Transformations

```kotlin
val v = Vector3(1f, 2f, 3f)

// Apply transformation matrix
v.applyMatrix4(transformMatrix)

// Apply rotation quaternion
v.applyQuaternion(rotation)

// Transform direction (no translation)
v.transformDirection(matrix)

// Apply 3x3 matrix
v.applyMatrix3(matrix3)
```

### Component Operations

```kotlin
val v = Vector3(3f, -2f, 5f)

// Min/max with another vector
v.min(Vector3(2f, 0f, 4f))  // Component-wise min
v.max(Vector3(4f, 1f, 6f))  // Component-wise max

// Clamp between two vectors
v.clamp(
    Vector3(-1f, -1f, -1f),
    Vector3(1f, 1f, 1f)
)

// Clamp to scalar range
v.clampScalar(-5f, 5f)

// Floor/ceil/round
v.floor()
v.ceil()
v.round()

// Component access
val x = v.componentAt(0)
val maxComp = v.maxComponent()  // Returns largest absolute value
val minComp = v.minComponent()  // Returns smallest absolute value
```

### Projection and Unprojection

```kotlin
val worldPos = Vector3(10f, 5f, 0f)

// Project to normalized device coordinates
worldPos.project(camera)

// Unproject from NDC to world space
val ndcPos = Vector3(-0.5f, 0.5f, 0f)
ndcPos.unproject(camera)
```

### Utility Methods

```kotlin
val v = Vector3(1f, 2f, 3f)

// Check if zero
if (v.isZero()) { /* ... */ }

// Equality with tolerance
if (v.equals(otherVector, epsilon = 0.0001f)) { /* ... */ }

// Coerce length
v.coerceLength(minLength = 1f, maxLength = 10f)

// Set from matrix column
v.setFromMatrixColumn(matrix, columnIndex = 0)

// Set from buffer attribute
v.fromBufferAttribute(attribute, index = 0)
```

### Constants

```kotlin
Vector3.ZERO      // (0, 0, 0)
Vector3.ONE       // (1, 1, 1)
Vector3.UNIT_X    // (1, 0, 0)
Vector3.UNIT_Y    // (0, 1, 0)
Vector3.UNIT_Z    // (0, 0, 1)
Vector3.UP        // (0, 1, 0)
Vector3.DOWN      // (0, -1, 0)
Vector3.LEFT      // (-1, 0, 0)
Vector3.RIGHT     // (1, 0, 0)
Vector3.FORWARD   // (0, 0, -1)
Vector3.BACK      // (0, 0, 1)
```

### Examples

```kotlin
// Create a direction vector
val direction = Vector3(1f, 0f, 0f).normalize()

// Calculate midpoint
val midpoint = start.clone().add(end).multiply(0.5f)

// Move along direction
position.add(direction * speed * deltaTime)

// Reflect vector
val reflected = direction.clone()
    .subtract(normal * (2f * direction.dot(normal)))

// Smooth movement with lerp
position.lerp(targetPosition, smoothness * deltaTime)
```

---

## Matrix4

4x4 transformation matrix stored in column-major order (OpenGL/WebGL convention).

### Constructor

```kotlin
// Identity matrix
val m1 = Matrix4()

// From array (column-major)
val m2 = Matrix4(floatArrayOf(
    1f, 0f, 0f, 0f,  // column 0
    0f, 1f, 0f, 0f,  // column 1
    0f, 0f, 1f, 0f,  // column 2
    0f, 0f, 0f, 1f   // column 3
))
```

### Factory Methods

```kotlin
// Identity
val identity = Matrix4.identity()

// Translation
val translation = Matrix4.translation(x = 10f, y = 0f, z = 5f)

// Scale
val scale = Matrix4.scale(x = 2f, y = 2f, z = 2f)

// Rotation
val rotX = Matrix4.rotationX(angle = PI.toFloat() / 2f)
val rotY = Matrix4.rotationY(angle = PI.toFloat() / 4f)
val rotZ = Matrix4.rotationZ(angle = PI.toFloat() / 6f)
```

### Element Access

```kotlin
val matrix = Matrix4()

// Access elements (row-column notation)
val m00: Float = matrix.m00  // Row 0, Col 0
val m01: Float = matrix.m01  // Row 0, Col 1
// ... m10, m11, m12, m13
// ... m20, m21, m22, m23
// ... m30, m31, m32, m33

// Direct array access (column-major)
val elements: FloatArray = matrix.elements
```

### Transformation Construction

```kotlin
val matrix = Matrix4()

// Make translation
matrix.makeTranslation(x = 5f, y = 10f, z = 0f)

// Make scale
matrix.makeScale(x = 2f, y = 2f, z = 2f)

// Make rotation
matrix.makeRotationX(angle)
matrix.makeRotationY(angle)
matrix.makeRotationZ(angle)
matrix.makeRotationFromQuaternion(quaternion)

// Compose from components
matrix.compose(
    position = Vector3(0f, 0f, 0f),
    quaternion = Quaternion.identity(),
    scale = Vector3(1f, 1f, 1f)
)

// Look-at matrix
matrix.lookAt(
    eye = Vector3(0f, 0f, 5f),
    target = Vector3(0f, 0f, 0f),
    up = Vector3(0f, 1f, 0f)
)
```

### Matrix Operations

```kotlin
val a = Matrix4()
val b = Matrix4.translation(10f, 0f, 0f)

// Multiply (a = a * b)
a.multiply(b)

// Premultiply (a = b * a)
a.premultiply(b)

// Multiply two matrices (a = b * c)
a.multiplyMatrices(b, c)

// Operator overloading
val result = a * b  // Returns new matrix

// Determinant
val det: Float = matrix.determinant()

// Invert (a = a^-1)
matrix.invert()

// Get inverse (doesn't modify original)
val inverse = matrix.inverse()

// Transpose
matrix.transpose()
```

### Decomposition

```kotlin
val matrix = Matrix4()

// Extract position
val position: Vector3 = matrix.getPosition()

// Extract scale
val scale: Vector3 = matrix.getScale()

// Extract rotation
val rotation: Quaternion = matrix.getRotation()

// Extract translation to target
val translation = Vector3()
matrix.extractTranslation(translation)

// Decompose into components
val pos = Vector3()
val rot = Quaternion()
val scl = Vector3()
matrix.decompose(pos, rot, scl)
```

### Projection Matrices

```kotlin
// Perspective projection
val perspective = Matrix4().makePerspective(
    left = -1f,
    right = 1f,
    top = 1f,
    bottom = -1f,
    near = 0.1f,
    far = 1000f
)

// Orthographic projection
val orthographic = Matrix4().makeOrthographic(
    left = -10f,
    right = 10f,
    top = 10f,
    bottom = -10f,
    near = 0.1f,
    far = 1000f
)
```

### Transformation Application

```kotlin
val matrix = Matrix4.translation(10f, 0f, 0f)

// Transform point (includes translation)
val point = Vector3(1f, 2f, 3f)
val transformed = matrix.transformPoint(point)

// Transform direction (no translation)
val direction = Vector3(1f, 0f, 0f)
val transformedDir = matrix.transformDirection(direction)

// Apply to matrix position
matrix.setPosition(x = 5f, y = 10f, z = 0f)
matrix.setPosition(Vector3(5f, 10f, 0f))
```

### Combining Transformations

```kotlin
val matrix = Matrix4()

// Translate
matrix.translate(Vector3(10f, 0f, 0f))

// Rotate
matrix.rotate(Quaternion.fromAxisAngle(Vector3.UNIT_Y, PI.toFloat() / 4f))

// Scale
matrix.scale(Vector3(2f, 2f, 2f))
```

### Utility Methods

```kotlin
val matrix = Matrix4()

// Check if identity
if (matrix.isIdentity()) { /* ... */ }

// Reset to identity
matrix.identity()

// Clone
val clone = matrix.clone()

// Copy
matrix.copy(otherMatrix)

// Set elements
matrix.set(
    m11 = 1f, m12 = 0f, m13 = 0f, m14 = 0f,
    m21 = 0f, m22 = 1f, m23 = 0f, m24 = 0f,
    m31 = 0f, m32 = 0f, m33 = 1f, m34 = 0f,
    m41 = 0f, m42 = 0f, m43 = 0f, m44 = 1f
)

// Convert to/from array
val array: FloatArray = matrix.toArray()
matrix.fromArray(array, offset = 0)

// Get view matrix (inverse)
val viewMatrix = matrix.viewMatrix
```

### Examples

```kotlin
// Create transformation matrix
val transform = Matrix4()
    .makeTranslation(0f, 0f, 0f)
    .multiply(Matrix4.rotationY(PI.toFloat() / 4f))
    .multiply(Matrix4.scale(2f, 2f, 2f))

// Apply to vector
val position = Vector3(1f, 0f, 0f)
position.applyMatrix4(transform)

// Camera view matrix
val camera = Matrix4().lookAt(
    eye = Vector3(0f, 5f, 10f),
    target = Vector3(0f, 0f, 0f),
    up = Vector3.UP
)
```

---

## Quaternion

Quaternion for representing rotations without gimbal lock.

### Constructor

```kotlin
// Identity quaternion (no rotation)
val q1 = Quaternion()

// From components
val q2 = Quaternion(x = 0f, y = 0f, z = 0f, w = 1f)
```

### Factory Methods

```kotlin
// Identity
val identity = Quaternion.identity()

// From axis-angle
val q = Quaternion.fromAxisAngle(
    axis = Vector3.UNIT_Y,
    angle = PI.toFloat() / 4f
)

// From Euler angles
val euler = Euler(x = 0f, y = PI.toFloat() / 2f, z = 0f)
val q2 = Quaternion.fromEuler(euler)

// From rotation matrix
val q3 = Quaternion.fromRotationMatrix(matrix)
```

### Basic Operations

```kotlin
val q = Quaternion()

// Set components
q.set(x = 0f, y = 0f, z = 0f, w = 1f)

// Set from axis-angle
q.setFromAxisAngle(axis = Vector3.UNIT_X, angle = PI.toFloat() / 2f)

// Set from Euler
q.setFromEuler(Euler(0f, PI.toFloat() / 2f, 0f))

// Set from rotation matrix
q.setFromRotationMatrix(matrix)

// Set from unit vectors
q.setFromUnitVectors(
    vFrom = Vector3.UNIT_X,
    vTo = Vector3.UNIT_Y
)

// Clone and copy
val clone = q.clone()
q.copy(otherQuaternion)

// Reset to identity
q.identity()
```

### Quaternion Math

```kotlin
val q1 = Quaternion.fromAxisAngle(Vector3.UNIT_Y, PI.toFloat() / 4f)
val q2 = Quaternion.fromAxisAngle(Vector3.UNIT_X, PI.toFloat() / 6f)

// Multiply (combine rotations)
q1.multiply(q2)  // q1 = q1 * q2

// Premultiply
q1.premultiply(q2)  // q1 = q2 * q1

// Multiply quaternions
val q3 = Quaternion().multiplyQuaternions(q1, q2)

// Operator
val result = q1 * q2

// Invert (opposite rotation)
q1.invert()

// Get inverse (doesn't modify original)
val inverse = q1.inverse()

// Conjugate
q1.conjugate()

// Dot product
val dot: Float = q1.dot(q2)

// Length
val length: Float = q1.length()
val lengthSq: Float = q1.lengthSq()

// Normalize
q1.normalize()
val normalized = q1.normalized()
```

### Interpolation

```kotlin
val start = Quaternion.identity()
val end = Quaternion.fromAxisAngle(Vector3.UNIT_Y, PI.toFloat())

// Spherical linear interpolation
start.slerp(end, t = 0.5f)

// Slerp between two quaternions
val mid = Quaternion.slerp(start, end, t = 0.5f)
```

### Conversion

```kotlin
val q = Quaternion.fromAxisAngle(Vector3.UNIT_Y, PI.toFloat() / 4f)

// To Euler angles
val euler: Vector3 = q.toEulerAngles()

// To axis-angle
val (axis, angle) = q.toAxisAngle()

// To array
val array: FloatArray = q.toArray()

// From array
q.fromArray(floatArrayOf(0f, 0f, 0f, 1f))
```

### Utility Methods

```kotlin
val q = Quaternion()

// Check if identity
if (q.isIdentity()) { /* ... */ }

// Equality with tolerance
if (q.equals(otherQuaternion, tolerance = 0.0001f)) { /* ... */ }
```

### Examples

```kotlin
// Rotate object to face target
val direction = target.clone().subtract(position).normalize()
val rotation = Quaternion.setFromUnitVectors(
    Vector3.FORWARD,
    direction
)

// Smooth rotation
val current = Quaternion.identity()
val target = Quaternion.fromAxisAngle(Vector3.UP, angle)
current.slerp(target, smoothness * deltaTime)

// Apply rotation to vector
val v = Vector3(1f, 0f, 0f)
v.applyQuaternion(rotation)
```

---

## Color

RGB color representation with HSL support.

### Constructor

```kotlin
// Black (0, 0, 0)
val c1 = Color()

// RGB components
val c2 = Color(r = 1f, g = 0.5f, b = 0.25f)

// From hex
val c3 = Color(0xFF6B46) // RGB hex
val c4 = Color(0xFF6B46C1u) // RGBA hex (unsigned)

// From string
val c5 = Color("#FF6B46")
val c6 = Color("rgb(255, 107, 70)")
val c7 = Color("hsl(14, 100%, 64%)")

// Copy constructor
val c8 = Color(c2)
```

### Properties

```kotlin
var r: Float  // Red (0.0 - 1.0)
var g: Float  // Green (0.0 - 1.0)
var b: Float  // Blue (0.0 - 1.0)
```

### Basic Operations

```kotlin
val color = Color()

// Set RGB
color.setRGB(r = 1f, g = 0.5f, b = 0f)

// Set from hex
color.setHex(0xFF6B46)

// Set from HSL
color.setHSL(h = 0.5f, s = 1f, l = 0.5f)

// Copy
color.copy(otherColor)

// Clone
val clone = color.clone()
```

### Color Operations

```kotlin
val c1 = Color(1f, 0f, 0f)
val c2 = Color(0f, 1f, 0f)

// Add colors
c1.add(c2)  // c1 = c1 + c2

// Multiply (component-wise)
c1.multiply(c2)

// Multiply by scalar
c1.multiplyScalar(0.5f)

// Lerp
c1.lerp(c2, t = 0.5f)
```

### Conversion

```kotlin
val color = Color(1f, 0.5f, 0.25f)

// To hex
val hex: Int = color.getHex()  // 0xFF8040

// To hex string
val hexString: String = color.getHexString()  // "ff8040"

// To style string
val style: String = color.getStyle()  // "rgb(255,128,64)"

// To HSL
val (h, s, l) = color.getHSL()

// From HSL
val hsl = HSL(h = 0.5f, s = 1f, l = 0.5f)
color.setHSL(hsl.h, hsl.s, hsl.l)
```

### Utility Methods

```kotlin
val color = Color(1f, 0f, 0f)

// Equality with tolerance
if (color.equals(otherColor, epsilon = 0.001f)) { /* ... */ }

// Convert to array
val array: FloatArray = color.toArray()  // [r, g, b]

// Set from array
color.fromArray(floatArrayOf(1f, 0.5f, 0.25f))
```

### Named Colors

```kotlin
Color.WHITE   // (1, 1, 1)
Color.BLACK   // (0, 0, 0)
Color.RED     // (1, 0, 0)
Color.GREEN   // (0, 1, 0)
Color.BLUE    // (0, 0, 1)
Color.YELLOW  // (1, 1, 0)
Color.CYAN    // (0, 1, 1)
Color.MAGENTA // (1, 0, 1)
Color.GRAY    // (0.5, 0.5, 0.5)
```

### Examples

```kotlin
// Create a gradient
val startColor = Color(0xFF0000)  // Red
val endColor = Color(0x0000FF)    // Blue
val midColor = startColor.clone().lerp(endColor, 0.5f)

// Adjust brightness
val darkened = color.clone().multiplyScalar(0.5f)

// Mix colors
val mixed = color1.clone().add(color2).multiplyScalar(0.5f)
```

---

## Additional Math Classes

### Box3 - Axis-Aligned Bounding Box

```kotlin
// Empty box
val box = Box3()

// From min/max
val box2 = Box3(
    min = Vector3(-1f, -1f, -1f),
    max = Vector3(1f, 1f, 1f)
)

// Operations
box.setFromPoints(listOf(v1, v2, v3))
box.setFromCenterAndSize(center, size)
box.expandByPoint(point)
box.expandByScalar(scalar)
box.containsPoint(point)
box.intersectsBox(otherBox)
val center = box.getCenter()
val size = box.getSize()
```

### Sphere - Bounding Sphere

```kotlin
val sphere = Sphere(
    center = Vector3(0f, 0f, 0f),
    radius = 5f
)

sphere.setFromPoints(points)
sphere.containsPoint(point)
sphere.intersectsSphere(otherSphere)
sphere.clampPoint(point, target)
```

### Plane - Mathematical Plane

```kotlin
val plane = Plane(
    normal = Vector3(0f, 1f, 0f),
    constant = 0f
)

plane.setFromNormalAndCoplanarPoint(normal, point)
plane.distanceToPoint(point)
plane.projectPoint(point, target)
plane.intersectLine(line, target)
```

---

## Performance Tips

1. **Reuse vectors** instead of creating new ones:
   ```kotlin
   // Bad
   position = position + velocity

   // Good
   position.add(velocity)
   ```

2. **Use object pooling** for frequently allocated vectors
3. **Prefer in-place operations** over operators in hot loops
4. **Cache computed values** like normalized vectors
5. **Use lengthSquared()** instead of length() when possible (avoids sqrt)

---

## Coordinate System

Materia uses a **right-handed coordinate system**:

- **+X**: Right
- **+Y**: Up
- **+Z**: Forward (toward viewer)
- Rotations follow right-hand rule

---

## See Also

- [Scene Graph](../scene/scene-graph.md) - Using math with Object3D
- [Cameras](../camera/cameras.md) - Projection matrices
- [Transformations](../../guides/transformations-guide.md) - Transformation guide
