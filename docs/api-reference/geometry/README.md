# Geometry Module API Reference

The geometry module provides classes for defining 3D shapes and procedural geometry generation.

## Overview

Geometries in Materia define the shape and structure of 3D objects through vertex positions,
normals, UVs, and indices.
All geometries extend `BufferGeometry`, which stores attributes in GPU-friendly buffers.

## Primitive Geometries

### BoxGeometry

Rectangular prism (cube or box) with configurable subdivisions.

```kotlin
/**
 * Box geometry with configurable dimensions and subdivision.
 *
 * @property width Size along X axis (default: 1.0)
 * @property height Size along Y axis (default: 1.0)
 * @property depth Size along Z axis (default: 1.0)
 * @property widthSegments Number of subdivisions along width (default: 1)
 * @property heightSegments Number of subdivisions along height (default: 1)
 * @property depthSegments Number of subdivisions along depth (default: 1)
 */
class BoxGeometry(
    width: Float = 1f,
    height: Float = 1f,
    depth: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1,
    depthSegments: Int = 1
) : PrimitiveGeometry()
```

**Example Usage:**

```kotlin
// Simple cube
val cube = BoxGeometry(1f, 1f, 1f)

// Rectangular box
val box = BoxGeometry(
    width = 2f,
    height = 1f,
    depth = 3f
)

// Subdivided box (smoother normals)
val detailedBox = BoxGeometry(
    width = 1f,
    height = 1f,
    depth = 1f,
    widthSegments = 10,
    heightSegments = 10,
    depthSegments = 10
)
```

**Generated Attributes:**

- `position` - Vertex positions (vec3)
- `normal` - Surface normals (vec3)
- `uv` - Texture coordinates (vec2)
- `index` - Triangle indices

### SphereGeometry

UV sphere with configurable detail level.

```kotlin
/**
 * Sphere geometry with latitude/longitude subdivision.
 *
 * @property radius Sphere radius (default: 1.0)
 * @property widthSegments Number of horizontal segments (default: 32)
 * @property heightSegments Number of vertical segments (default: 16)
 * @property phiStart Horizontal starting angle (default: 0)
 * @property phiLength Horizontal sweep angle (default: 2π)
 * @property thetaStart Vertical starting angle (default: 0)
 * @property thetaLength Vertical sweep angle (default: π)
 */
class SphereGeometry(
    radius: Float = 1f,
    widthSegments: Int = 32,
    heightSegments: Int = 16,
    phiStart: Float = 0f,
    phiLength: Float = PI.toFloat() * 2f,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat()
) : PrimitiveGeometry()
```

**Example Usage:**

```kotlin
// Standard sphere
val sphere = SphereGeometry(radius = 1f)

// Low-poly sphere
val lowPolySphere = SphereGeometry(
    radius = 1f,
    widthSegments = 8,
    heightSegments = 6
)

// Hemisphere (half sphere)
val hemisphere = SphereGeometry(
    radius = 1f,
    phiStart = 0f,
    phiLength = PI.toFloat() * 2f,
    thetaStart = 0f,
    thetaLength = PI.toFloat() / 2f
)

// Sphere segment
val sphereSegment = SphereGeometry(
    radius = 1f,
    phiStart = 0f,
    phiLength = PI.toFloat(), // 180° horizontal
    thetaStart = 0f,
    thetaLength = PI.toFloat() / 2f // 90° vertical
)
```

### PlaneGeometry

Flat rectangular plane with subdivisions.

```kotlin
/**
 * Plane geometry lying in XY plane.
 *
 * @property width Width along X axis (default: 1.0)
 * @property height Height along Y axis (default: 1.0)
 * @property widthSegments Number of segments along width (default: 1)
 * @property heightSegments Number of segments along height (default: 1)
 */
class PlaneGeometry(
    width: Float = 1f,
    height: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1
) : PrimitiveGeometry()
```

**Example Usage:**

```kotlin
// Simple plane
val plane = PlaneGeometry(10f, 10f)

// Ground plane with subdivisions
val ground = PlaneGeometry(
    width = 100f,
    height = 100f,
    widthSegments = 50,
    heightSegments = 50
)

// Rotate to be horizontal
ground.rotation.x = -PI.toFloat() / 2f
```

### CylinderGeometry

Cylinder or cone with configurable radii.

```kotlin
/**
 * Cylinder geometry with optional cone shape.
 *
 * @property radiusTop Radius at top (default: 1.0)
 * @property radiusBottom Radius at bottom (default: 1.0)
 * @property height Height along Y axis (default: 1.0)
 * @property radialSegments Number of segments around circumference (default: 32)
 * @property heightSegments Number of segments along height (default: 1)
 * @property openEnded Whether to cap the ends (default: false)
 * @property thetaStart Starting angle (default: 0)
 * @property thetaLength Sweep angle (default: 2π)
 */
class CylinderGeometry(
    radiusTop: Float = 1f,
    radiusBottom: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry()
```

**Example Usage:**

```kotlin
// Standard cylinder
val cylinder = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 2f
)

// Cone (radiusTop = 0)
val cone = CylinderGeometry(
    radiusTop = 0f,
    radiusBottom = 1f,
    height = 2f
)

// Open-ended tube
val tube = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 5f,
    openEnded = true
)

// Half cylinder
val halfCylinder = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 2f,
    thetaStart = 0f,
    thetaLength = PI.toFloat()
)
```

### TorusGeometry

Donut shape with major and minor radii.

```kotlin
/**
 * Torus (donut) geometry.
 *
 * @property radius Radius from center to tube center (default: 1.0)
 * @property tube Tube radius (default: 0.4)
 * @property radialSegments Segments around tube (default: 12)
 * @property tubularSegments Segments around torus (default: 48)
 * @property arc Arc length (default: 2π)
 */
class TorusGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    radialSegments: Int = 12,
    tubularSegments: Int = 48,
    arc: Float = PI.toFloat() * 2f
) : PrimitiveGeometry()
```

**Example Usage:**

```kotlin
// Standard torus
val torus = TorusGeometry(
    radius = 2f,
    tube = 0.5f
)

// Low-poly torus
val lowPolyTorus = TorusGeometry(
    radius = 2f,
    tube = 0.5f,
    radialSegments = 6,
    tubularSegments = 12
)

// Partial torus (arc)
val torusArc = TorusGeometry(
    radius = 2f,
    tube = 0.5f,
    arc = PI.toFloat() // 180° arc
)
```

### RingGeometry

Flat ring or disc in XY plane.

```kotlin
/**
 * Ring geometry (flat donut shape).
 *
 * @property innerRadius Inner radius (default: 0.5)
 * @property outerRadius Outer radius (default: 1.0)
 * @property thetaSegments Circumference segments (default: 32)
 * @property phiSegments Radial segments (default: 1)
 * @property thetaStart Starting angle (default: 0)
 * @property thetaLength Sweep angle (default: 2π)
 */
class RingGeometry(
    innerRadius: Float = 0.5f,
    outerRadius: Float = 1f,
    thetaSegments: Int = 32,
    phiSegments: Int = 1,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry()
```

## Advanced Geometries

### CapsuleGeometry

Capsule shape (cylinder with hemispherical caps).

```kotlin
/**
 * Capsule geometry (cylinder with rounded caps).
 *
 * @property radius Capsule radius
 * @property length Length of cylindrical section
 * @property capSegments Segments in each hemispherical cap
 * @property radialSegments Segments around circumference
 */
class CapsuleGeometry(
    radius: Float = 1f,
    length: Float = 1f,
    capSegments: Int = 4,
    radialSegments: Int = 8
) : PrimitiveGeometry()
```

### TorusKnotGeometry

Parametric torus knot.

```kotlin
/**
 * Torus knot geometry (knot wrapped around torus).
 *
 * @property radius Radius of torus
 * @property tube Tube radius
 * @property tubularSegments Segments along tube path
 * @property radialSegments Segments around tube
 * @property p Winding number around torus
 * @property q Winding number through torus
 */
class TorusKnotGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    tubularSegments: Int = 64,
    radialSegments: Int = 8,
    p: Int = 2,
    q: Int = 3
) : PrimitiveGeometry()
```

**Example:**

```kotlin
// Trefoil knot (classic)
val trefoil = TorusKnotGeometry(
    radius = 2f,
    tube = 0.6f,
    p = 2,
    q = 3
)

// Figure-eight knot
val figureEight = TorusKnotGeometry(
    radius = 2f,
    tube = 0.6f,
    p = 3,
    q = 2
)
```

### PolyhedronGeometry

Base class for platonic solids.

Specific implementations:

- `TetrahedronGeometry` - 4 faces
- `OctahedronGeometry` - 8 faces
- `IcosahedronGeometry` - 20 faces
- `DodecahedronGeometry` - 12 faces

```kotlin
// Icosahedron (good sphere approximation)
val icosahedron = IcosahedronGeometry(
    radius = 1f,
    detail = 2 // Subdivisions for smoother surface
)
```

### ExtrudeGeometry

Extrude 2D shapes into 3D.

```kotlin
/**
 * Extrude a 2D shape along Z axis.
 *
 * @property shapes Shape or shapes to extrude
 * @property options Extrusion parameters
 */
class ExtrudeGeometry(
    shapes: List<Shape>,
    options: ExtrudeGeometryOptions
) : BufferGeometry()

data class ExtrudeGeometryOptions(
    val depth: Float = 1f,
    val bevelEnabled: Boolean = true,
    val bevelThickness: Float = 0.2f,
    val bevelSize: Float = 0.1f,
    val bevelSegments: Int = 3,
    val steps: Int = 1,
    val extrudePath: Curve? = null
)
```

**Example:**

```kotlin
// Create star shape
val shape = Shape()
for (i in 0 until 10) {
    val angle = (i / 10.0 * PI * 2).toFloat()
    val radius = if (i % 2 == 0) 1f else 0.5f
    val x = cos(angle) * radius
    val y = sin(angle) * radius
    if (i == 0) shape.moveTo(x, y)
    else shape.lineTo(x, y)
}

// Extrude star
val extrudedStar = ExtrudeGeometry(
    shapes = listOf(shape),
    options = ExtrudeGeometryOptions(
        depth = 0.5f,
        bevelEnabled = true,
        bevelThickness = 0.1f
    )
)
```

### LatheGeometry

Rotate 2D profile around Y axis.

```kotlin
/**
 * Create geometry by rotating points around Y axis.
 *
 * @property points 2D points to revolve
 * @property segments Number of circumference segments
 * @property phiStart Starting angle
 * @property phiLength Sweep angle
 */
class LatheGeometry(
    points: List<Vector2>,
    segments: Int = 12,
    phiStart: Float = 0f,
    phiLength: Float = PI.toFloat() * 2f
) : BufferGeometry()
```

**Example:**

```kotlin
// Vase profile
val points = listOf(
    Vector2(0.0f, 0.0f),
    Vector2(0.3f, 0.2f),
    Vector2(0.4f, 0.5f),
    Vector2(0.3f, 0.8f),
    Vector2(0.4f, 1.0f)
)

val vase = LatheGeometry(points, segments = 32)
```

### TubeGeometry

Tube following a 3D curve.

```kotlin
/**
 * Tube geometry following a 3D curve.
 *
 * @property path 3D curve to follow
 * @property tubularSegments Segments along path
 * @property radius Tube radius
 * @property radialSegments Segments around tube
 * @property closed Whether tube forms closed loop
 */
class TubeGeometry(
    path: Curve3,
    tubularSegments: Int = 64,
    radius: Float = 1f,
    radialSegments: Int = 8,
    closed: Boolean = false
) : BufferGeometry()
```

### TextGeometry

3D text geometry from font.

```kotlin
/**
 * 3D text geometry.
 *
 * @property text Text string to render
 * @property parameters Text generation parameters
 */
class TextGeometry(
    text: String,
    parameters: TextGeometryParameters
) : ExtrudeGeometry()

data class TextGeometryParameters(
    val font: Font,
    val size: Float = 100f,
    val height: Float = 50f,
    val curveSegments: Int = 12,
    val bevelEnabled: Boolean = false,
    val bevelThickness: Float = 10f,
    val bevelSize: Float = 8f,
    val bevelSegments: Int = 3
)
```

### ParametricGeometry

Custom parametric surface.

```kotlin
/**
 * Geometry from parametric function.
 *
 * @property func Function (u, v) -> Vector3
 * @property slices U segments
 * @property stacks V segments
 */
class ParametricGeometry(
    func: (u: Float, v: Float) -> Vector3,
    slices: Int,
    stacks: Int
) : BufferGeometry()
```

**Example:**

```kotlin
// Klein bottle
val klein = ParametricGeometry(
    func = { u, v ->
        val u = u * PI.toFloat() * 2
        val v = v * PI.toFloat() * 2
        // Klein bottle parametric equations
        Vector3(/* ... */)
    },
    slices = 25,
    stacks = 25
)
```

## Geometry Processing

### NormalGenerator

Generate or recalculate surface normals.

```kotlin
/**
 * Generate smooth or flat normals for geometry.
 */
object NormalGenerator {
    /**
     * Compute smooth normals (averaged)
     */
    fun computeVertexNormals(geometry: BufferGeometry)

    /**
     * Compute flat normals (per-face)
     */
    fun computeFaceNormals(geometry: BufferGeometry)
}
```

**Example:**

```kotlin
// Smooth shading
NormalGenerator.computeVertexNormals(geometry)

// Flat shading
NormalGenerator.computeFaceNormals(geometry)
```

### TangentGenerator

Generate tangent vectors for normal mapping.

```kotlin
/**
 * Generate tangent space for normal mapping.
 */
object TangentGenerator {
    /**
     * Compute tangents from positions, normals, and UVs
     */
    fun computeTangents(geometry: BufferGeometry)
}
```

### BoundingVolumeCalculator

Calculate bounding volumes.

```kotlin
/**
 * Calculate bounding boxes and spheres.
 */
object BoundingVolumeCalculator {
    /**
     * Compute axis-aligned bounding box
     */
    fun computeBoundingBox(geometry: BufferGeometry): Box3

    /**
     * Compute bounding sphere
     */
    fun computeBoundingSphere(geometry: BufferGeometry): Sphere
}
```

### LODGenerator

Generate level-of-detail variants.

```kotlin
/**
 * Generate LOD levels for geometry.
 */
class LODGenerator {
    /**
     * Generate LOD with target triangle count
     */
    fun generateLOD(
        geometry: BufferGeometry,
        targetTriangles: Int
    ): BufferGeometry
}
```

### MeshSimplifier

Reduce polygon count.

```kotlin
/**
 * Simplify mesh topology.
 */
class MeshSimplifier {
    /**
     * Simplify to target quality
     *
     * @param quality 0.0 to 1.0 (1.0 = original)
     */
    fun simplify(
        geometry: BufferGeometry,
        quality: Float
    ): BufferGeometry
}
```

### VertexOptimizer

Optimize vertex cache usage.

```kotlin
/**
 * Optimize vertex order for GPU cache.
 */
object VertexOptimizer {
    /**
     * Reorder vertices for optimal cache usage
     */
    fun optimize(geometry: BufferGeometry)
}
```

## BufferGeometry

Base class for all geometries.

```kotlin
/**
 * Base geometry class storing vertex data in typed buffers.
 */
abstract class BufferGeometry {
    /**
     * Unique identifier
     */
    val id: Int

    /**
     * Geometry name
     */
    var name: String

    /**
     * Vertex attributes (position, normal, uv, etc.)
     */
    val attributes: MutableMap<String, BufferAttribute>

    /**
     * Triangle indices (optional)
     */
    var index: BufferAttribute?

    /**
     * Axis-aligned bounding box
     */
    var boundingBox: Box3?

    /**
     * Bounding sphere
     */
    var boundingSphere: Sphere?

    /**
     * Drawing range (start, count)
     */
    var drawRange: DrawRange

    /**
     * Groups for multi-material
     */
    val groups: MutableList<GeometryGroup>

    /**
     * Morph target attributes
     */
    val morphAttributes: MutableMap<String, List<BufferAttribute>>

    /**
     * Set vertex attribute
     */
    fun setAttribute(name: String, attribute: BufferAttribute)

    /**
     * Get vertex attribute
     */
    fun getAttribute(name: String): BufferAttribute?

    /**
     * Delete vertex attribute
     */
    fun deleteAttribute(name: String)

    /**
     * Set triangle indices
     */
    fun setIndex(index: BufferAttribute?)

    /**
     * Add geometry group (for multi-material)
     */
    fun addGroup(start: Int, count: Int, materialIndex: Int = 0)

    /**
     * Clear all groups
     */
    fun clearGroups()

    /**
     * Set draw range
     */
    fun setDrawRange(start: Int, count: Int)

    /**
     * Compute bounding box
     */
    fun computeBoundingBox()

    /**
     * Compute bounding sphere
     */
    fun computeBoundingSphere()

    /**
     * Apply transformation matrix
     */
    fun applyMatrix4(matrix: Matrix4)

    /**
     * Center geometry at origin
     */
    fun center()

    /**
     * Clone geometry
     */
    fun clone(): BufferGeometry

    /**
     * Dispose GPU resources
     */
    fun dispose()
}
```

### BufferAttribute

Typed array for vertex data.

```kotlin
/**
 * Typed buffer for vertex attributes.
 */
class BufferAttribute(
    val array: FloatArray,
    val itemSize: Int,
    val normalized: Boolean = false
) {
    /**
     * Number of items (array.size / itemSize)
     */
    val count: Int

    /**
     * Get value at index
     */
    fun getX(index: Int): Float
    fun getY(index: Int): Float
    fun getZ(index: Int): Float
    fun getW(index: Int): Float

    /**
     * Set value at index
     */
    fun setX(index: Int, x: Float)
    fun setY(index: Int, y: Float)
    fun setZ(index: Int, z: Float)
    fun setW(index: Int, w: Float)

    /**
     * Set XYZ at index
     */
    fun setXYZ(index: Int, x: Float, y: Float, z: Float)

    /**
     * Set XYZW at index
     */
    fun setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float)

    /**
     * Copy from another attribute
     */
    fun copy(source: BufferAttribute)

    /**
     * Clone attribute
     */
    fun clone(): BufferAttribute
}
```

## UV Generation

### Projection Types

```kotlin
// Box projection
val boxUV = BoxUVProjection()
boxUV.generate(geometry)

// Cylindrical projection
val cylindricalUV = CylindricalUVProjection()
cylindricalUV.generate(geometry)

// Spherical projection
val sphericalUV = SphericalUVProjection()
sphericalUV.generate(geometry)

// Planar projection
val planarUV = PlanarUVProjection(
    normal = Vector3.UP
)
planarUV.generate(geometry)
```

### UV Unwrapping

```kotlin
// Automatic UV unwrapping
val unwrapper = UVUnwrapping()
unwrapper.unwrap(geometry)

// UV optimization (reduce seams)
val optimizer = UVOptimization()
optimizer.optimize(geometry)
```

## Best Practices

### Geometry Creation

```kotlin
// Reuse geometries when possible
val sharedGeometry = BoxGeometry(1f, 1f, 1f)
val mesh1 = Mesh(sharedGeometry, material1)
val mesh2 = Mesh(sharedGeometry, material2)

// Dispose when done
geometry.dispose()
```

### LOD Management

```kotlin
// Create LOD levels
val lod = LOD()
lod.addLevel(highDetailGeometry, 0f)   // 0-100 units
lod.addLevel(medDetailGeometry, 100f)  // 100-500 units
lod.addLevel(lowDetailGeometry, 500f)  // 500+ units
scene.add(lod)
```

### Performance Tips

1. **Merge static geometries** - Reduce draw calls
2. **Use instancing** for repeated geometry
3. **Generate LODs** for distant objects
4. **Optimize vertex count** - Remove unused vertices
5. **Use indexed geometry** - Reduce memory

## Examples

### Custom Geometry

```kotlin
// Create custom triangle
val positions = floatArrayOf(
    0f, 1f, 0f,  // Top
    -1f, -1f, 0f, // Bottom left
    1f, -1f, 0f   // Bottom right
)

val normals = floatArrayOf(
    0f, 0f, 1f,
    0f, 0f, 1f,
    0f, 0f, 1f
)

val uvs = floatArrayOf(
    0.5f, 1f,
    0f, 0f,
    1f, 0f
)

val geometry = BufferGeometry()
geometry.setAttribute("position", BufferAttribute(positions, 3))
geometry.setAttribute("normal", BufferAttribute(normals, 3))
geometry.setAttribute("uv", BufferAttribute(uvs, 2))
```

### Merged Geometry

```kotlin
// Merge multiple geometries
val merged = BufferGeometry()
val geometries = listOf(geo1, geo2, geo3)

GeometryMerger.merge(merged, geometries)
```

## See Also

- [Material Module](../material/README.md) - Materials for rendering
- [Mesh](../core/README.md) - Combining geometry with material
- [Optimization](../../guides/performance.md) - Performance optimization

---

**Module**: `io.materia.geometry`
**Since**: 1.0.0
**Status**: ✅ Stable
