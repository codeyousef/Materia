# Geometry API Reference

Geometries define the shape of 3D objects. Materia provides a comprehensive set of primitive and
advanced geometries.

## Table of Contents

- [BufferGeometry](#buffergeometry) - Base geometry class
- [Primitive Geometries](#primitive-geometries)
    - [BoxGeometry](#boxgeometry)
    - [SphereGeometry](#spheregeometry)
    - [PlaneGeometry](#planegeometry)
    - [CylinderGeometry](#cylindergeometry)
    - [ConeGeometry](#conegeometry)
    - [TorusGeometry](#torusgeometry)
    - [TorusKnotGeometry](#torusknotgeometry)
- [Advanced Geometries](#advanced-geometries)
    - [ExtrudeGeometry](#extrudegeometry)
    - [LatheGeometry](#lathegeometry)
    - [TubeGeometry](#tubegeometry)
    - [TextGeometry](#textgeometry)
    - [ParametricGeometry](#parametricgeometry)
- [Platonic Solids](#platonic-solids)
- [Custom Geometries](#custom-geometries)

---

## BufferGeometry

Base class for all geometries. Stores vertex data in GPU-friendly buffer format.

### Properties

```kotlin
val attributes: Map<String, BufferAttribute>  // Vertex attributes
val index: BufferAttribute?                   // Index buffer
val morphAttributes: Map<String, List<BufferAttribute>>  // Morph targets
val groups: List<GeometryGroup>               // Material groups
var morphTargetsRelative: Boolean             // Morph target mode
val boundingBox: Box3?                        // Cached bounding box
val boundingSphere: Sphere?                   // Cached bounding sphere
```

### Attributes

Standard attributes:

- `position` - Vertex positions (Vector3)
- `normal` - Vertex normals (Vector3)
- `uv` - Texture coordinates (Vector2)
- `color` - Vertex colors (Color)
- `tangent` - Tangent vectors (Vector4)

### Basic Usage

```kotlin
val geometry = BufferGeometry()

// Set position attribute
val positions = floatArrayOf(
    -1f, -1f, 0f,  // Vertex 0
     1f, -1f, 0f,  // Vertex 1
     0f,  1f, 0f   // Vertex 2
)
geometry.setAttribute(
    "position",
    BufferAttribute(positions, itemSize = 3)
)

// Set indices
val indices = floatArrayOf(0f, 1f, 2f)
geometry.setIndex(BufferAttribute(indices, itemSize = 1))

// Compute normals
geometry.computeVertexNormals()
```

### Methods

```kotlin
// Attribute management
fun setAttribute(name: String, attribute: BufferAttribute)
fun getAttribute(name: String): BufferAttribute?
fun deleteAttribute(name: String)
fun hasAttribute(name: String): Boolean

// Index buffer
fun setIndex(index: BufferAttribute?)

// Bounding volumes
fun computeBoundingBox(): Box3
fun computeBoundingSphere(): Sphere

// Transformations
fun translate(x: Float, y: Float, z: Float)
fun scale(x: Float, y: Float, z: Float)
fun rotateX(angle: Float)
fun rotateY(angle: Float)
fun rotateZ(angle: Float)
fun applyMatrix4(matrix: Matrix4)

// Morph targets
fun setMorphAttribute(name: String, targets: Array<BufferAttribute>)
fun getMorphAttribute(name: String): List<BufferAttribute>?
fun computeMorphedBoundingBox()
fun computeMorphedBoundingSphere()

// Groups (for multi-material)
fun addGroup(start: Int, count: Int, materialIndex: Int = 0)
fun clearGroups()

// Instancing
fun setInstancedAttribute(name: String, attribute: BufferAttribute)
var instanceCount: Int
val isInstanced: Boolean

// Utility
fun getTriangleCount(): Int
fun getVertexCount(): Int
fun clone(): BufferGeometry
fun dispose()
```

---

## Primitive Geometries

### BoxGeometry

Rectangular box (cube).

```kotlin
val geometry = BoxGeometry(
    width = 1f,
    height = 1f,
    depth = 1f,
    widthSegments = 1,
    heightSegments = 1,
    depthSegments = 1
)
```

**Parameters**:

- `width` - Size along X axis
- `height` - Size along Y axis
- `depth` - Size along Z axis
- `widthSegments` - Number of segmented faces along width
- `heightSegments` - Number of segmented faces along height
- `depthSegments` - Number of segmented faces along depth

**Examples**:

```kotlin
// Simple cube
val cube = BoxGeometry(2f, 2f, 2f)

// Subdivided box (smoother shading)
val smoothBox = BoxGeometry(1f, 1f, 1f, 10, 10, 10)

// Rectangular prism
val platform = BoxGeometry(10f, 0.5f, 5f)
```

---

### SphereGeometry

Sphere with customizable segments.

```kotlin
val geometry = SphereGeometry(
    radius = 1f,
    widthSegments = 32,
    heightSegments = 16,
    phiStart = 0f,
    phiLength = PI.toFloat() * 2f,
    thetaStart = 0f,
    thetaLength = PI.toFloat()
)
```

**Parameters**:

- `radius` - Sphere radius
- `widthSegments` - Horizontal segments
- `heightSegments` - Vertical segments
- `phiStart` - Horizontal starting angle
- `phiLength` - Horizontal sweep angle
- `thetaStart` - Vertical starting angle
- `thetaLength` - Vertical sweep angle

**Examples**:

```kotlin
// Standard sphere
val sphere = SphereGeometry(radius = 1f, widthSegments = 32, heightSegments = 16)

// High-detail sphere
val smoothSphere = SphereGeometry(1f, 64, 64)

// Hemisphere
val hemisphere = SphereGeometry(
    radius = 1f,
    thetaLength = PI.toFloat() / 2f
)

// Sphere segment
val segment = SphereGeometry(
    radius = 1f,
    phiStart = 0f,
    phiLength = PI.toFloat()  // Half sphere horizontally
)
```

---

### PlaneGeometry

Flat rectangular surface.

```kotlin
val geometry = PlaneGeometry(
    width = 1f,
    height = 1f,
    widthSegments = 1,
    heightSegments = 1
)
```

**Examples**:

```kotlin
// Ground plane
val ground = PlaneGeometry(width = 100f, height = 100f)

// Subdivided for terrain
val terrain = PlaneGeometry(
    width = 50f,
    height = 50f,
    widthSegments = 128,
    heightSegments = 128
)

// Wall
val wall = PlaneGeometry(10f, 5f)
```

---

### CylinderGeometry

Cylinder with optional different top/bottom radii (cone).

```kotlin
val geometry = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 2f,
    radialSegments = 32,
    heightSegments = 1,
    openEnded = false,
    thetaStart = 0f,
    thetaLength = PI.toFloat() * 2f
)
```

**Parameters**:

- `radiusTop` - Top radius
- `radiusBottom` - Bottom radius
- `height` - Height of cylinder
- `radialSegments` - Number of faces around circumference
- `heightSegments` - Number of faces along height
- `openEnded` - Open or capped ends
- `thetaStart` - Starting angle
- `thetaLength` - Sweep angle

**Examples**:

```kotlin
// Standard cylinder
val cylinder = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 3f,
    radialSegments = 32
)

// Cone
val cone = CylinderGeometry(
    radiusTop = 0f,
    radiusBottom = 1f,
    height = 2f
)

// Pipe (open-ended)
val pipe = CylinderGeometry(
    radiusTop = 0.5f,
    radiusBottom = 0.5f,
    height = 5f,
    openEnded = true
)

// Truncated cone
val frustum = CylinderGeometry(
    radiusTop = 0.5f,
    radiusBottom = 1.5f,
    height = 3f
)
```

---

### ConeGeometry

Cone (specialized cylinder).

```kotlin
val geometry = ConeGeometry(
    radius = 1f,
    height = 2f,
    radialSegments = 32,
    heightSegments = 1,
    openEnded = false,
    thetaStart = 0f,
    thetaLength = PI.toFloat() * 2f
)
```

**Examples**:

```kotlin
// Standard cone
val cone = ConeGeometry(radius = 1f, height = 2f)

// Pyramid (low segments)
val pyramid = ConeGeometry(radius = 1f, height = 2f, radialSegments = 4)

// Party hat
val hat = ConeGeometry(radius = 1f, height = 3f, radialSegments = 64)
```

---

### TorusGeometry

Donut shape.

```kotlin
val geometry = TorusGeometry(
    radius = 1f,
    tube = 0.4f,
    radialSegments = 16,
    tubularSegments = 64,
    arc = PI.toFloat() * 2f
)
```

**Parameters**:

- `radius` - Torus radius (center to tube center)
- `tube` - Tube radius
- `radialSegments` - Segments around tube
- `tubularSegments` - Segments around torus
- `arc` - Central angle

**Examples**:

```kotlin
// Standard torus
val torus = TorusGeometry(radius = 1f, tube = 0.4f)

// Thin ring
val ring = TorusGeometry(radius = 2f, tube = 0.1f)

// Partial torus (arc)
val arc = TorusGeometry(
    radius = 1f,
    tube = 0.3f,
    arc = PI.toFloat()  // Half donut
)
```

---

### TorusKnotGeometry

Knot-shaped torus.

```kotlin
val geometry = TorusKnotGeometry(
    radius = 1f,
    tube = 0.4f,
    tubularSegments = 64,
    radialSegments = 8,
    p = 2,  // Winding number
    q = 3   // Winding number
)
```

**Examples**:

```kotlin
// Trefoil knot
val trefoil = TorusKnotGeometry(
    radius = 1f,
    tube = 0.3f,
    p = 2,
    q = 3
)

// Cinquefoil knot
val cinquefoil = TorusKnotGeometry(
    radius = 1f,
    tube = 0.3f,
    p = 5,
    q = 2
)

// Complex knot
val complex = TorusKnotGeometry(
    radius = 1f,
    tube = 0.2f,
    p = 3,
    q = 7,
    tubularSegments = 128,
    radialSegments = 16
)
```

---

## Advanced Geometries

### ExtrudeGeometry

Extrude 2D shapes into 3D.

```kotlin
// Define 2D shape
val shape = Shape().apply {
    moveTo(0f, 0f)
    lineTo(0f, 1f)
    lineTo(1f, 1f)
    lineTo(1f, 0f)
    lineTo(0f, 0f)
}

val geometry = ExtrudeGeometry(
    shapes = listOf(shape),
    options = ExtrudeGeometryOptions(
        depth = 1f,
        bevelEnabled = true,
        bevelThickness = 0.1f,
        bevelSize = 0.1f,
        bevelSegments = 3,
        steps = 1,
        curveSegments = 12
    )
)
```

**Examples**:

```kotlin
// Star extrusion
val starShape = Shape().apply {
    for (i in 0 until 10) {
        val angle = (i / 10f) * PI.toFloat() * 2f
        val radius = if (i % 2 == 0) 1f else 0.5f
        val x = cos(angle) * radius
        val y = sin(angle) * radius

        if (i == 0) moveTo(x, y)
        else lineTo(x, y)
    }
}

val star = ExtrudeGeometry(
    shapes = listOf(starShape),
    options = ExtrudeGeometryOptions(depth = 0.5f)
)

// Text extrusion
val textShape = createTextShape("HELLO")
val text3D = ExtrudeGeometry(
    shapes = listOf(textShape),
    options = ExtrudeGeometryOptions(
        depth = 0.2f,
        bevelEnabled = true,
        bevelThickness = 0.03f,
        bevelSize = 0.02f
    )
)
```

---

### LatheGeometry

Revolve a 2D curve around an axis.

```kotlin
val points = listOf(
    Vector2(0f, 0f),
    Vector2(0.5f, 0.5f),
    Vector2(0.3f, 1f),
    Vector2(0.1f, 1.5f),
    Vector2(0f, 2f)
)

val geometry = LatheGeometry(
    points = points,
    segments = 32,
    phiStart = 0f,
    phiLength = PI.toFloat() * 2f
)
```

**Examples**:

```kotlin
// Vase
val vaseProfile = listOf(
    Vector2(0f, 0f),
    Vector2(0.8f, 0.2f),
    Vector2(1f, 1f),
    Vector2(0.9f, 2f),
    Vector2(0.7f, 2.5f),
    Vector2(0.5f, 2.7f)
)
val vase = LatheGeometry(vaseProfile, segments = 64)

// Wine glass
val glassProfile = listOf(
    Vector2(0f, 0f),
    Vector2(0.3f, 0f),
    Vector2(0.1f, 1f),
    Vector2(0.3f, 1.5f),
    Vector2(0.8f, 2f),
    Vector2(0.8f, 2.5f)
)
val glass = LatheGeometry(glassProfile)
```

---

### TubeGeometry

Tube following a 3D curve.

```kotlin
// Define curve
val curve = CatmullRomCurve3(
    points = listOf(
        Vector3(0f, 0f, 0f),
        Vector3(1f, 1f, 0f),
        Vector3(2f, 0f, 1f),
        Vector3(3f, -1f, 0f)
    )
)

val geometry = TubeGeometry(
    path = curve,
    tubularSegments = 64,
    radius = 0.3f,
    radialSegments = 8,
    closed = false
)
```

**Examples**:

```kotlin
// Pipe following a path
val pipePath = QuadraticBezierCurve3(
    v0 = Vector3(0f, 0f, 0f),
    v1 = Vector3(5f, 5f, 0f),
    v2 = Vector3(10f, 0f, 0f)
)
val pipe = TubeGeometry(pipePath, tubularSegments = 64, radius = 0.5f)

// Cable
val cablePath = CubicBezierCurve3(
    v0 = Vector3(0f, 0f, 0f),
    v1 = Vector3(2f, 3f, 1f),
    v2 = Vector3(4f, -1f, 2f),
    v3 = Vector3(6f, 0f, 0f)
)
val cable = TubeGeometry(cablePath, radius = 0.1f, radialSegments = 6)
```

---

### TextGeometry

3D text from TrueType fonts.

```kotlin
val geometry = TextGeometry(
    text = "Hello World",
    parameters = TextGeometryParameters(
        font = font,
        size = 1f,
        height = 0.2f,
        curveSegments = 12,
        bevelEnabled = true,
        bevelThickness = 0.03f,
        bevelSize = 0.02f,
        bevelSegments = 3
    )
)
```

**Examples**:

```kotlin
// Load font
val fontLoader = FontLoader()
val font = fontLoader.load("fonts/helvetiker_regular.typeface.json")

// Simple text
val simpleText = TextGeometry(
    text = "Materia",
    parameters = TextGeometryParameters(
        font = font,
        size = 2f,
        height = 0.5f
    )
)

// Beveled text
val fancyText = TextGeometry(
    text = "3D TEXT",
    parameters = TextGeometryParameters(
        font = font,
        size = 1f,
        height = 0.3f,
        bevelEnabled = true,
        bevelThickness = 0.05f,
        bevelSize = 0.04f
    )
)
```

---

### ParametricGeometry

Geometry from parametric equations.

```kotlin
// Define parametric function
fun parametricFunction(u: Float, v: Float, target: Vector3) {
    val x = u * 2f - 1f
    val y = v * 2f - 1f
    val z = sin(u * PI.toFloat() * 2f) * cos(v * PI.toFloat() * 2f)
    target.set(x, y, z)
}

val geometry = ParametricGeometry(
    func = ::parametricFunction,
    slices = 32,
    stacks = 32
)
```

**Examples**:

```kotlin
// Wave surface
fun wave(u: Float, v: Float, target: Vector3) {
    val x = u * 10f - 5f
    val z = v * 10f - 5f
    val y = sin(x) * cos(z)
    target.set(x, y, z)
}
val waveSurface = ParametricGeometry(::wave, 64, 64)

// Mobius strip
fun mobius(u: Float, v: Float, target: Vector3) {
    val angle = u * PI.toFloat() * 2f
    val majorRadius = 2f
    val t = v * 2f - 1f
    val x = (majorRadius + t * cos(angle / 2f)) * cos(angle)
    val y = (majorRadius + t * cos(angle / 2f)) * sin(angle)
    val z = t * sin(angle / 2f)
    target.set(x, y, z)
}
val mobius = ParametricGeometry(::mobius, 64, 32)
```

---

## Platonic Solids

Perfect geometric shapes with all faces, edges, and angles equal.

```kotlin
// Tetrahedron (4 faces)
val tetrahedron = TetrahedronGeometry(radius = 1f, detail = 0)

// Octahedron (8 faces)
val octahedron = OctahedronGeometry(radius = 1f, detail = 0)

// Icosahedron (20 faces)
val icosahedron = IcosahedronGeometry(radius = 1f, detail = 0)

// Dodecahedron (12 faces)
val dodecahedron = DodecahedronGeometry(radius = 1f, detail = 0)

// Higher detail (subdivided)
val smoothIcosahedron = IcosahedronGeometry(radius = 1f, detail = 3)
```

---

## Custom Geometries

### Creating from Scratch

```kotlin
// Create geometry
val geometry = BufferGeometry()

// Define triangle vertices
val vertices = floatArrayOf(
    -1f, -1f, 0f,  // v0
     1f, -1f, 0f,  // v1
     0f,  1f, 0f   // v2
)

// Define normals
val normals = floatArrayOf(
    0f, 0f, 1f,  // n0
    0f, 0f, 1f,  // n1
    0f, 0f, 1f   // n2
)

// Define UVs
val uvs = floatArrayOf(
    0f, 0f,  // uv0
    1f, 0f,  // uv1
    0.5f, 1f // uv2
)

// Set attributes
geometry.setAttribute("position", BufferAttribute(vertices, 3))
geometry.setAttribute("normal", BufferAttribute(normals, 3))
geometry.setAttribute("uv", BufferAttribute(uvs, 2))

// Compute additional data
geometry.computeBoundingBox()
geometry.computeBoundingSphere()
```

### Modifying Existing Geometry

```kotlin
val geometry = BoxGeometry(1f, 1f, 1f)

// Access position attribute
val positionAttr = geometry.getAttribute("position")
val positions = positionAttr!!.array

// Modify vertices
for (i in positions.indices step 3) {
    positions[i] *= 2f     // Scale X
    positions[i + 1] *= 2f // Scale Y
    positions[i + 2] *= 2f // Scale Z
}

// Mark as needing update
positionAttr.needsUpdate = true

// Recompute bounds
geometry.computeBoundingBox()
geometry.computeBoundingSphere()
```

---

## Geometry Utilities

### Merging Geometries

```kotlin
val merged = BufferGeometryUtils.mergeBufferGeometries(
    listOf(geometry1, geometry2, geometry3)
)
```

### Computing Normals

```kotlin
// Smooth normals
geometry.computeVertexNormals()

// Flat normals
geometry.computeFlatNormals()
```

### Computing Tangents

```kotlin
geometry.computeTangents()
```

### Bounding Volumes

```kotlin
val bbox = geometry.computeBoundingBox()
println("Min: ${bbox.min}, Max: ${bbox.max}")

val sphere = geometry.computeBoundingSphere()
println("Center: ${sphere.center}, Radius: ${sphere.radius}")
```

---

## Performance Tips

1. **Reuse geometries**: Share geometry instances between meshes
2. **Minimize segments**: Use only as many segments as needed
3. **Use indexed geometry**: Reduces vertex count
4. **Dispose properly**: Call `geometry.dispose()` when done
5. **Merge static geometry**: Combine multiple static objects

---

## See Also

- [BufferGeometry Source](../../../src/commonMain/kotlin/io/materia/geometry/BufferGeometry.kt)
- [Primitives Source](../../../src/commonMain/kotlin/io/materia/geometry/primitives/)
- [Materials](../material/materials.md)
- [Mesh](../scene/scene-graph.md#mesh)
