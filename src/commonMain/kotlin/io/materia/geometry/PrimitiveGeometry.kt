/**
 * Base class for primitive geometry generation with parameterized shapes
 * Provides standard primitive shapes with configurable parameters and efficient generation
 */
package io.materia.geometry

/**
 * Base class for all primitive geometries
 * Implements common functionality for parameterized shape generation
 */
abstract class PrimitiveGeometry : BufferGeometry() {

    /**
     * Parameters used to generate this primitive
     */
    abstract val parameters: PrimitiveParameters

    /**
     * Generate the geometry based on current parameters
     */
    abstract fun generate()

    /**
     * Update geometry if parameters have changed
     */
    fun updateIfNeeded() {
        if (parameters.hasChanged) {
            generate()
            parameters.markClean()
        }
    }

    /**
     * Get memory usage estimate for this primitive
     */
    fun getMemoryUsage(): Int {
        var usage = 0
        attributes.forEach { (_, attribute) ->
            usage = usage + attribute.array.size * when (attribute.itemSize) {
                1 -> 4  // Float32
                2 -> 8  // Vector2
                3 -> 12 // Vector3
                4 -> 16 // Vector4/Color
                else -> attribute.itemSize * 4
            }
        }
        index?.let { usage = usage + it.array.size * 4 }
        return usage
    }
}

/**
 * Base interface for primitive parameters
 */
abstract class PrimitiveParameters {
    protected var dirty = true

    val hasChanged: Boolean get() = dirty

    fun markDirty() {
        dirty = true
    }

    fun markClean() {
        dirty = false
    }
}
