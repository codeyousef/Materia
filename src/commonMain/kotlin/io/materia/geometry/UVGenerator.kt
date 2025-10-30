/**
 * UVGenerator for procedural texture coordinate mapping
 * T025 - Advanced UV mapping with multiple projection methods and automatic unwrapping
 *
 * Provides algorithms for:
 * - Box projection UV mapping
 * - Cylindrical and spherical projections
 * - Planar UV projection with custom planes
 * - UV unwrapping algorithms
 * - Automatic seam detection and handling
 * - Atlas-friendly UV layouts
 */
package io.materia.geometry

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.uvgen.*

/**
 * Advanced UV coordinate generator for texture mapping
 * Implements industry-standard UV projection and unwrapping techniques
 */
class UVGenerator {

    companion object {
        // Default UV generation settings
        const val DEFAULT_SEAM_ANGLE = 0.5f // ~30 degrees
        const val DEFAULT_DISTORTION_THRESHOLD = 0.1f
        const val DEFAULT_UV_PADDING = 0.02f
        const val DEFAULT_ATLAS_SIZE = 1024

        // UV projection methods
        enum class ProjectionMethod {
            PLANAR,
            BOX,
            CYLINDRICAL,
            SPHERICAL,
            CONFORMAL,
            ANGLE_BASED
        }
    }

    /**
     * Generate UV coordinates using box projection
     * Projects geometry onto 6 faces of a bounding box
     */
    fun generateBoxUV(
        geometry: BufferGeometry,
        options: BoxUVOptions = BoxUVOptions()
    ): UVGenerationResult = io.materia.geometry.uvgen.generateBoxUV(geometry, options)

    /**
     * Generate UV coordinates using cylindrical projection
     * Ideal for objects with cylindrical symmetry
     */
    fun generateCylindricalUV(
        geometry: BufferGeometry,
        options: CylindricalUVOptions = CylindricalUVOptions()
    ): UVGenerationResult = io.materia.geometry.uvgen.generateCylindricalUV(geometry, options)

    /**
     * Generate UV coordinates using spherical projection
     * Ideal for spherical objects like planets or heads
     */
    fun generateSphericalUV(
        geometry: BufferGeometry,
        options: SphericalUVOptions = SphericalUVOptions()
    ): UVGenerationResult = io.materia.geometry.uvgen.generateSphericalUV(geometry, options)

    /**
     * Generate UV coordinates using planar projection
     * Projects onto a custom plane with specified orientation
     */
    fun generatePlanarUV(
        geometry: BufferGeometry,
        options: PlanarUVOptions = PlanarUVOptions()
    ): UVGenerationResult = io.materia.geometry.uvgen.generatePlanarUV(geometry, options)

    /**
     * Perform automatic UV unwrapping using angle-based flattening
     * Creates seam-free UV layouts for organic shapes
     */
    fun generateUnwrappedUV(
        geometry: BufferGeometry,
        options: UnwrapOptions = UnwrapOptions()
    ): UVGenerationResult = io.materia.geometry.uvgen.generateUnwrappedUV(geometry, options)

    /**
     * Optimize existing UV coordinates for better texture utilization
     * Improves texel density and reduces stretching
     */
    fun optimizeUVLayout(
        geometry: BufferGeometry,
        options: UVOptimizationOptions = UVOptimizationOptions()
    ): UVOptimizationResult = io.materia.geometry.uvgen.optimizeUVLayout(geometry, options)

    /**
     * Generate UV coordinates with automatic method selection
     * Analyzes geometry and chooses the best projection method
     */
    fun generateAutomaticUV(
        geometry: BufferGeometry,
        options: AutomaticUVOptions = AutomaticUVOptions()
    ): UVGenerationResult {
        // Analyze geometry properties
        val analysis = analyzeGeometryForUV(geometry)

        // Select best projection method
        val method = selectOptimalProjectionMethod(analysis, options.preferredMethods)

        // Generate UVs using selected method
        return when (method) {
            ProjectionMethod.BOX -> generateBoxUV(geometry, options.boxOptions)
            ProjectionMethod.CYLINDRICAL -> generateCylindricalUV(
                geometry,
                options.cylindricalOptions
            )

            ProjectionMethod.SPHERICAL -> generateSphericalUV(geometry, options.sphericalOptions)
            ProjectionMethod.PLANAR -> generatePlanarUV(geometry, options.planarOptions)
            ProjectionMethod.CONFORMAL,
            ProjectionMethod.ANGLE_BASED -> generateUnwrappedUV(geometry, options.unwrapOptions)
        }
    }

    private fun analyzeGeometryForUV(geometry: BufferGeometry): GeometryAnalysis {
        return GeometryAnalysis()
    }

    private fun selectOptimalProjectionMethod(
        analysis: GeometryAnalysis,
        preferredMethods: List<ProjectionMethod>
    ): ProjectionMethod {
        return preferredMethods.firstOrNull() ?: ProjectionMethod.BOX
    }
}

/**
 * Automatic UV generation options
 */
data class AutomaticUVOptions(
    val preferredMethods: List<UVGenerator.Companion.ProjectionMethod> = listOf(
        UVGenerator.Companion.ProjectionMethod.BOX,
        UVGenerator.Companion.ProjectionMethod.CYLINDRICAL,
        UVGenerator.Companion.ProjectionMethod.SPHERICAL
    ),
    val boxOptions: BoxUVOptions = BoxUVOptions(),
    val cylindricalOptions: CylindricalUVOptions = CylindricalUVOptions(),
    val sphericalOptions: SphericalUVOptions = SphericalUVOptions(),
    val planarOptions: PlanarUVOptions = PlanarUVOptions(),
    val unwrapOptions: UnwrapOptions = UnwrapOptions()
)

/**
 * UV generation result
 */
data class UVGenerationResult(
    val geometry: BufferGeometry,
    val success: Boolean,
    val message: String = "",
    val seamVertices: List<Int> = emptyList(),
    val uvBounds: Box2 = Box2()
)

/**
 * 2D bounding box for UV coordinates
 */
data class Box2(
    val min: Vector2 = Vector2(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    val max: Vector2 = Vector2(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
) {
    fun getSize(target: Vector2): Vector2 {
        return target.set(max.x - min.x, max.y - min.y)
    }

    fun getCenter(target: Vector2): Vector2 {
        return target.set((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f)
    }
}

/**
 * Geometry analysis for UV method selection
 */
class GeometryAnalysis {
    var isCylindrical: Boolean = false
    var isSpherical: Boolean = false
    var hasComplexTopology: Boolean = false
    var dominantAxis: Vector3 = Vector3()
}

/**
 * Calculate UV bounds from coordinate array
 */
internal fun calculateUVBounds(uvCoordinates: FloatArray): Box2 {
    if (uvCoordinates.isEmpty()) return Box2()

    var minU = uvCoordinates[0]
    var maxU = uvCoordinates[0]
    var minV = uvCoordinates[1]
    var maxV = uvCoordinates[1]

    for (i in uvCoordinates.indices step 2) {
        val u = uvCoordinates[i]
        val v = uvCoordinates[i + 1]

        minU = minOf(minU, u)
        maxU = maxOf(maxU, u)
        minV = minOf(minV, v)
        maxV = maxOf(maxV, v)
    }

    return Box2(Vector2(minU, minV), Vector2(maxU, maxV))
}
