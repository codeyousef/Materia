package io.materia.morph

import io.materia.geometry.BufferAttribute

/**
 * Interface for geometries that support morph targets.
 * Morph targets allow smooth interpolation between different vertex positions and normals.
 */
interface MorphTargetGeometry {
    /**
     * Map of morph target attributes.
     * Key: attribute name (e.g., "position", "normal")
     * Value: List of BufferAttributes, one for each morph target
     */
    val morphAttributes: MutableMap<String, List<BufferAttribute>>

    /**
     * Whether morph targets are relative to the base geometry.
     * If true, morph target values are added to base values.
     * If false, morph target values replace base values.
     */
    var morphTargetsRelative: Boolean

    /**
     * Named morph targets for easy reference.
     * Key: morph target name (e.g., "smile", "frown")
     * Value: Index in the morphAttributes lists
     */
    val morphTargetDictionary: MutableMap<String, Int>

    /**
     * Add a morph target with position and optional normal attributes.
     *
     * @param name Name of the morph target
     * @param position Position attribute for this morph target
     * @param normal Optional normal attribute for this morph target
     * @return Index of the added morph target
     */
    fun addMorphTarget(
        name: String,
        position: BufferAttribute,
        normal: BufferAttribute? = null
    ): Int {
        val index = morphAttributes["position"]?.size ?: 0

        // Add position morph target
        val positionTargets =
            morphAttributes.getOrPut("position") { mutableListOf() }.toMutableList()
        positionTargets.add(position)
        morphAttributes["position"] = positionTargets

        // Add normal morph target if provided
        if (normal != null) {
            val normalTargets =
                morphAttributes.getOrPut("normal") { mutableListOf() }.toMutableList()
            normalTargets.add(normal)
            morphAttributes["normal"] = normalTargets
        }

        // Add to dictionary
        morphTargetDictionary[name] = index

        return index
    }

    /**
     * Remove a morph target by name.
     *
     * @param name Name of the morph target to remove
     */
    fun removeMorphTarget(name: String) {
        val index = morphTargetDictionary[name] ?: return

        // Remove from all attribute lists
        morphAttributes.forEach { (key, targets) ->
            if (index < targets.size) {
                val mutableTargets = targets.toMutableList()
                mutableTargets.removeAt(index)
                morphAttributes[key] = mutableTargets
            }
        }

        // Remove from dictionary and update indices
        morphTargetDictionary.remove(name)
        morphTargetDictionary.forEach { (key, value) ->
            if (value > index) {
                morphTargetDictionary[key] = value - 1
            }
        }
    }

    /**
     * Get the index of a named morph target.
     *
     * @param name Name of the morph target
     * @return Index of the morph target, or null if not found
     */
    fun getMorphTargetIndex(name: String): Int? = morphTargetDictionary[name]

    /**
     * Get the total number of morph targets.
     *
     * @return Number of morph targets
     */
    fun getMorphTargetCount(): Int = morphAttributes["position"]?.size ?: 0

    /**
     * Clear all morph targets.
     */
    fun clearMorphTargets() {
        morphAttributes.clear()
        morphTargetDictionary.clear()
    }

    /**
     * Compute bounding box including morph targets.
     * Should consider all morph target positions when computing bounds.
     */
    fun computeMorphedBoundingBox()

    /**
     * Compute bounding sphere including morph targets.
     * Should consider all morph target positions when computing bounds.
     */
    fun computeMorphedBoundingSphere()
}

/**
 * Data class representing morph target influences.
 * Used to control the blending of morph targets.
 */
data class MorphTargetInfluences(
    /**
     * Array of influence values, one for each morph target.
     * Values typically range from 0.0 (no influence) to 1.0 (full influence).
     */
    val influences: FloatArray = FloatArray(0),

    /**
     * Whether influences need to be updated in the GPU buffer.
     */
    var needsUpdate: Boolean = true
) {
    /**
     * Set the influence for a specific morph target.
     *
     * @param index Index of the morph target
     * @param value Influence value (typically 0.0 to 1.0)
     */
    fun setInfluence(index: Int, value: Float) {
        if (index >= 0 && index < influences.size) {
            influences[index] = value
            needsUpdate = true
        }
    }

    /**
     * Get the influence for a specific morph target.
     *
     * @param index Index of the morph target
     * @return Influence value, or 0.0 if index is out of bounds
     */
    fun getInfluence(index: Int): Float =
        if (index >= 0 && index < influences.size) influences[index] else 0f

    /**
     * Reset all influences to 0.
     */
    fun reset() {
        influences.fill(0f)
        needsUpdate = true
    }

    /**
     * Normalize influences so they sum to 1.0.
     * Useful for ensuring proper blending.
     */
    fun normalize() {
        val sum = influences.sum()
        if (sum > 0) {
            for (i in influences.indices) {
                influences[i] /= sum
            }
            needsUpdate = true
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MorphTargetInfluences) return false

        if (!influences.contentEquals(other.influences)) return false
        if (needsUpdate != other.needsUpdate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = influences.contentHashCode()
        result = 31 * result + needsUpdate.hashCode()
        return result
    }
}