# Geometry API Reference

The geometry module provides classes for defining 3D shapes and mesh data.

## Overview

```kotlin
import io.materia.geometry.*
```

---

## BufferGeometry

Base class for all geometry. Stores vertex data in typed arrays for GPU efficiency.

### Constructor

```kotlin
class BufferGeometry()
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `Int` | Unique identifier |
| `uuid` | `String` | UUID string |
| `name` | `String` | Optional name |
| `attributes` | `Map<String, BufferAttribute>` | Vertex attributes |
| `index` | `BufferAttribute?` | Index buffer (for indexed geometry) |
| `groups` | `List<Group>` | Sub-mesh groups |
| `boundingBox` | `Box3?` | Axis-aligned bounding box |
| `boundingSphere` | `Sphere?` | Bounding sphere |

### Methods

#### Attributes

```kotlin
// Set a vertex attribute
fun setAttribute(name: String, attribute: BufferAttribute): BufferGeometry

// Get a vertex attribute
fun getAttribute(name: String): BufferAttribute?

// Delete an attribute
fun deleteAttribute(name: String): BufferGeometry

// Check if attribute exists
fun hasAttribute(name: String): Boolean

// Set index buffer
fun setIndex(index: BufferAttribute): BufferGeometry
fun setIndex(indices: IntArray): BufferGeometry
```

#### Standard Attribute Names

| Name | Components | Description |
|------|------------|-------------|
| `position` | 3 | Vertex positions (x, y, z) |
| `normal` | 3 | Vertex normals (nx, ny, nz) |
| `uv` | 2 | Texture coordinates (u, v) |
| `uv2` | 2 | Secondary UVs (lightmaps) |
| `color` | 3 | Vertex colors (r, g, b) |
| `tangent` | 4 | Tangent vectors (x, y, z, w) |
| `skinIndex` | 4 | Bone indices for skinning |
| `skinWeight` | 4 | Bone weights for skinning |

#### Groups

```kotlin
// Add a draw group (for multi-material meshes)
fun addGroup(start: Int, count: Int, materialIndex: Int = 0)

// Clear all groups
fun clearGroups()
```

#### Bounding Volumes

```kotlin
// Compute bounding box
fun computeBoundingBox()

// Compute bounding sphere
fun computeBoundingSphere()
```

#### Normals & Tangents

```kotlin
// Compute vertex normals from face normals
fun computeVertexNormals()

// Compute tangents for normal mapping
fun computeTangents()
```

#### Transformations

```kotlin
// Apply matrix transform to all vertices
fun applyMatrix4(matrix: Matrix4): BufferGeometry

// Apply quaternion rotation
fun applyQuaternion(q: Quaternion): BufferGeometry

// Rotate around X axis
fun rotateX(angle: Float): BufferGeometry

// Rotate around Y axis
fun rotateY(angle: Float): BufferGeometry

// Rotate around Z axis
fun rotateZ(angle: Float): BufferGeometry

// Translate all vertices
fun translate(x: Float, y: Float, z: Float): BufferGeometry

// Scale all vertices
fun scale(x: Float, y: Float, z: Float): BufferGeometry

// Center geometry at origin
fun center(): BufferGeometry
```

#### Utilities

```kotlin
// Merge another geometry
fun merge(geometry: BufferGeometry, offset: Int = 0): BufferGeometry

// Convert to non-indexed geometry
fun toNonIndexed(): BufferGeometry

// Clone geometry
fun clone(): BufferGeometry

// Dispose GPU resources
fun dispose()
```

### Example

```kotlin
// Create custom geometry
val geometry = BufferGeometry()

// Define triangle vertices
val positions = floatArrayOf(
    0f, 1f, 0f,    // top
    -1f, -1f, 0f,  // bottom left
    1f, -1f, 0f    // bottom right
)

// Define normals
val normals = floatArrayOf(
    0f, 0f, 1f,
    0f, 0f, 1f,
    0f, 0f, 1f
)

// Define UVs
val uvs = floatArrayOf(
    0.5f, 1f,
    0f, 0f,
    1f, 0f
)

geometry.setAttribute("position", Float32BufferAttribute(positions, 3))
geometry.setAttribute("normal", Float32BufferAttribute(normals, 3))
geometry.setAttribute("uv", Float32BufferAttribute(uvs, 2))

val mesh = Mesh(geometry, material)
```

---

## BufferAttribute

Stores vertex attribute data.

### Constructor

```kotlin
class Float32BufferAttribute(
    array: FloatArray,
    itemSize: Int,
    normalized: Boolean = false
)

class Int32BufferAttribute(
    array: IntArray,
    itemSize: Int,
    normalized: Boolean = false
)

class Uint16BufferAttribute(
    array: ShortArray,
    itemSize: Int,
    normalized: Boolean = false
)
```

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `array` | `TypedArray` | Underlying data |
| `itemSize` | `Int` | Components per vertex |
| `count` | `Int` | Number of vertices |
| `normalized` | `Boolean` | Normalize to 0-1 range |
| `needsUpdate` | `Boolean` | Flag to update GPU buffer |

### Methods

```kotlin
// Get value at index
fun getX(index: Int): Float
fun getY(index: Int): Float
fun getZ(index: Int): Float
fun getW(index: Int): Float

// Set value at index
fun setX(index: Int, value: Float): BufferAttribute
fun setY(index: Int, value: Float): BufferAttribute
fun setZ(index: Int, value: Float): BufferAttribute
fun setW(index: Int, value: Float): BufferAttribute
fun setXYZ(index: Int, x: Float, y: Float, z: Float): BufferAttribute
fun setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float): BufferAttribute

// Copy from array
fun copyArray(array: FloatArray): BufferAttribute

// Clone
fun clone(): BufferAttribute
```

---

## Primitive Geometries

### BoxGeometry

A rectangular box (cuboid).

```kotlin
class BoxGeometry(
    width: Float = 1f,
    height: Float = 1f,
    depth: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1,
    depthSegments: Int = 1
)
```

| Parameter | Description |
|-----------|-------------|
| `width` | Width along X axis |
| `height` | Height along Y axis |
| `depth` | Depth along Z axis |
| `*Segments` | Subdivisions per face |

```kotlin
// Simple cube
val cube = Mesh(BoxGeometry(1f, 1f, 1f), material)

// Detailed box for displacement mapping
val detailedBox = Mesh(BoxGeometry(2f, 1f, 3f, 10, 10, 10), material)
```

---

### SphereGeometry

A UV sphere.

```kotlin
class SphereGeometry(
    radius: Float = 1f,
    widthSegments: Int = 32,
    heightSegments: Int = 16,
    phiStart: Float = 0f,
    phiLength: Float = PI * 2f,
    thetaStart: Float = 0f,
    thetaLength: Float = PI
)
```

| Parameter | Description |
|-----------|-------------|
| `radius` | Sphere radius |
| `widthSegments` | Horizontal segments |
| `heightSegments` | Vertical segments |
| `phiStart` | Horizontal start angle |
| `phiLength` | Horizontal sweep angle |
| `thetaStart` | Vertical start angle |
| `thetaLength` | Vertical sweep angle |

```kotlin
// Full sphere
val sphere = Mesh(SphereGeometry(0.5f, 32, 16), material)

// Hemisphere
val hemisphere = Mesh(
    SphereGeometry(1f, 32, 16, 0f, PI * 2, 0f, PI / 2),
    material
)
```

---

### PlaneGeometry

A flat rectangular surface.

```kotlin
class PlaneGeometry(
    width: Float = 1f,
    height: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1
)
```

| Parameter | Description |
|-----------|-------------|
| `width` | Width along X axis |
| `height` | Height along Y axis |
| `*Segments` | Subdivisions |

```kotlin
// Ground plane
val ground = Mesh(PlaneGeometry(100f, 100f), material)
ground.rotation.x = -PI / 2  // Rotate to horizontal
```

---

### CylinderGeometry

A cylinder or cone.

```kotlin
class CylinderGeometry(
    radiusTop: Float = 1f,
    radiusBottom: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = PI * 2f
)
```

| Parameter | Description |
|-----------|-------------|
| `radiusTop` | Top cap radius (0 for cone) |
| `radiusBottom` | Bottom cap radius |
| `height` | Height along Y axis |
| `radialSegments` | Segments around circumference |
| `heightSegments` | Segments along height |
| `openEnded` | Remove top/bottom caps |
| `thetaStart` | Start angle |
| `thetaLength` | Sweep angle |

```kotlin
// Cylinder
val cylinder = Mesh(CylinderGeometry(0.5f, 0.5f, 2f), material)

// Cone
val cone = Mesh(CylinderGeometry(0f, 0.5f, 1f), material)

// Tube (open cylinder)
val tube = Mesh(
    CylinderGeometry(0.5f, 0.5f, 2f, 32, 1, openEnded = true),
    material
)
```

---

### ConeGeometry

A cone (convenience class).

```kotlin
class ConeGeometry(
    radius: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = PI * 2f
)
```

---

### TorusGeometry

A torus (donut shape).

```kotlin
class TorusGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    radialSegments: Int = 12,
    tubularSegments: Int = 48,
    arc: Float = PI * 2f
)
```

| Parameter | Description |
|-----------|-------------|
| `radius` | Distance from center to tube center |
| `tube` | Tube radius |
| `radialSegments` | Segments around tube |
| `tubularSegments` | Segments around torus |
| `arc` | Sweep angle |

```kotlin
val torus = Mesh(TorusGeometry(1f, 0.3f, 16, 100), material)
```

---

### TorusKnotGeometry

A torus knot.

```kotlin
class TorusKnotGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    tubularSegments: Int = 64,
    radialSegments: Int = 8,
    p: Int = 2,
    q: Int = 3
)
```

| Parameter | Description |
|-----------|-------------|
| `p` | Wraps around axis of rotational symmetry |
| `q` | Wraps around interior |

---

### RingGeometry

A flat ring (washer shape).

```kotlin
class RingGeometry(
    innerRadius: Float = 0.5f,
    outerRadius: Float = 1f,
    thetaSegments: Int = 32,
    phiSegments: Int = 1,
    thetaStart: Float = 0f,
    thetaLength: Float = PI * 2f
)
```

---

### CircleGeometry

A flat circle.

```kotlin
class CircleGeometry(
    radius: Float = 1f,
    segments: Int = 32,
    thetaStart: Float = 0f,
    thetaLength: Float = PI * 2f
)
```

---

### IcosahedronGeometry

An icosahedron (20-sided polyhedron).

```kotlin
class IcosahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
)
```

| Parameter | Description |
|-----------|-------------|
| `detail` | Subdivision level (0 = 20 faces) |

```kotlin
// Low-poly sphere
val icosphere = Mesh(IcosahedronGeometry(1f, 2), material)
```

---

### OctahedronGeometry

An octahedron (8-sided).

```kotlin
class OctahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
)
```

---

### TetrahedronGeometry

A tetrahedron (4-sided).

```kotlin
class TetrahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
)
```

---

### DodecahedronGeometry

A dodecahedron (12-sided).

```kotlin
class DodecahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
)
```

---

## Advanced Geometries

### ExtrudeGeometry

Extrudes a 2D shape into 3D.

```kotlin
class ExtrudeGeometry(
    shape: Shape,
    options: ExtrudeGeometryOptions = ExtrudeGeometryOptions()
)
```

```kotlin
data class ExtrudeGeometryOptions(
    val depth: Float = 1f,
    val bevelEnabled: Boolean = true,
    val bevelThickness: Float = 0.2f,
    val bevelSize: Float = 0.1f,
    val bevelOffset: Float = 0f,
    val bevelSegments: Int = 3,
    val curveSegments: Int = 12,
    val steps: Int = 1,
    val extrudePath: Curve? = null
)
```

```kotlin
// Create a star shape
val shape = Shape()
val outerRadius = 2f
val innerRadius = 1f
val spikes = 5

for (i in 0 until spikes * 2) {
    val radius = if (i % 2 == 0) outerRadius else innerRadius
    val angle = (i / (spikes * 2f)) * PI * 2 - PI / 2
    val x = cos(angle) * radius
    val y = sin(angle) * radius
    if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
}
shape.closePath()

// Extrude to 3D
val geometry = ExtrudeGeometry(shape, ExtrudeGeometryOptions(depth = 0.5f))
```

---

### LatheGeometry

Creates geometry by revolving a 2D shape around the Y axis.

```kotlin
class LatheGeometry(
    points: List<Vector2>,
    segments: Int = 12,
    phiStart: Float = 0f,
    phiLength: Float = PI * 2f
)
```

```kotlin
// Create a vase profile
val points = listOf(
    Vector2(0f, 0f),
    Vector2(0.5f, 0f),
    Vector2(0.5f, 0.5f),
    Vector2(0.3f, 1f),
    Vector2(0.4f, 1.5f),
    Vector2(0.35f, 2f),
    Vector2(0f, 2f)
)

val vase = Mesh(LatheGeometry(points, 32), material)
```

---

### TubeGeometry

A tube following a 3D curve.

```kotlin
class TubeGeometry(
    path: Curve3,
    tubularSegments: Int = 64,
    radius: Float = 1f,
    radialSegments: Int = 8,
    closed: Boolean = false
)
```

```kotlin
// Create a curved tube
val curve = CatmullRomCurve3(listOf(
    Vector3(-10f, 0f, 10f),
    Vector3(-5f, 5f, 5f),
    Vector3(0f, 0f, 0f),
    Vector3(5f, -5f, 5f),
    Vector3(10f, 0f, 10f)
))

val tube = Mesh(TubeGeometry(curve, 20, 0.2f, 8), material)
```

---

### ShapeGeometry

Creates a flat shape from a 2D path.

```kotlin
class ShapeGeometry(
    shape: Shape,
    curveSegments: Int = 12
)
```

---

### TextGeometry

3D text geometry from font data.

```kotlin
class TextGeometry(
    text: String,
    options: TextGeometryOptions
)
```

```kotlin
data class TextGeometryOptions(
    val font: Font,
    val size: Float = 100f,
    val height: Float = 50f,
    val curveSegments: Int = 12,
    val bevelEnabled: Boolean = false,
    val bevelThickness: Float = 10f,
    val bevelSize: Float = 8f,
    val bevelOffset: Float = 0f,
    val bevelSegments: Int = 3
)
```

```kotlin
// Load font and create text
FontLoader().load("fonts/helvetiker.json") { font ->
    val textGeometry = TextGeometry("Hello!", TextGeometryOptions(
        font = font,
        size = 1f,
        height = 0.2f,
        bevelEnabled = true,
        bevelThickness = 0.05f,
        bevelSize = 0.03f
    ))
    
    textGeometry.center()  // Center the text
    val textMesh = Mesh(textGeometry, material)
    scene.add(textMesh)
}
```

---

## Instanced Geometry

### InstancedBufferGeometry

Geometry for instanced rendering.

```kotlin
class InstancedBufferGeometry : BufferGeometry() {
    var instanceCount: Int = Infinity
}
```

### InstancedBufferAttribute

Per-instance attribute data.

```kotlin
class InstancedBufferAttribute(
    array: FloatArray,
    itemSize: Int,
    normalized: Boolean = false,
    meshPerAttribute: Int = 1
)
```

```kotlin
// Create instanced geometry
val geometry = InstancedBufferGeometry()
geometry.copy(BoxGeometry(1f, 1f, 1f))

// Per-instance positions
val instanceCount = 1000
val offsets = FloatArray(instanceCount * 3)
val colors = FloatArray(instanceCount * 3)

for (i in 0 until instanceCount) {
    offsets[i * 3] = (Math.random() - 0.5) * 100
    offsets[i * 3 + 1] = (Math.random() - 0.5) * 100
    offsets[i * 3 + 2] = (Math.random() - 0.5) * 100
    
    colors[i * 3] = Math.random()
    colors[i * 3 + 1] = Math.random()
    colors[i * 3 + 2] = Math.random()
}

geometry.setAttribute("offset", InstancedBufferAttribute(offsets, 3))
geometry.setAttribute("instanceColor", InstancedBufferAttribute(colors, 3))
geometry.instanceCount = instanceCount
```

---

## Utility Classes

### EdgesGeometry

Creates geometry representing edges of another geometry.

```kotlin
class EdgesGeometry(
    geometry: BufferGeometry,
    thresholdAngle: Float = 1f  // degrees
)
```

```kotlin
val edges = EdgesGeometry(BoxGeometry(1f, 1f, 1f))
val line = LineSegments(edges, LineBasicMaterial(Color.WHITE))
```

---

### WireframeGeometry

Creates wireframe representation.

```kotlin
class WireframeGeometry(geometry: BufferGeometry)
```

---

## See Also

- [Material API](material.md) - Materials for geometry
- [Core API](core.md) - Vector3, Matrix4 for transforms
- [Loader API](loader.md) - Loading geometry from files
