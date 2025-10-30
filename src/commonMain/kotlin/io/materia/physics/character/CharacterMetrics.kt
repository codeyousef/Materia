package io.materia.physics.character

import io.materia.physics.*

/**
 * Character physical dimensions calculator
 */
object CharacterMetrics {

    /**
     * Get character radius from collision shape
     */
    fun getRadius(shape: CollisionShape): Float {
        return when (shape) {
            is CapsuleShape -> shape.radius
            is SphereShape -> shape.radius
            is BoxShape -> minOf(shape.halfExtents.x, shape.halfExtents.z)
            else -> 0.5f
        }
    }

    /**
     * Get character height from collision shape
     */
    fun getHeight(shape: CollisionShape): Float {
        return when (shape) {
            is CapsuleShape -> shape.height + shape.radius * 2f
            is SphereShape -> shape.radius * 2f
            is BoxShape -> shape.halfExtents.y * 2f
            else -> 1.8f
        }
    }
}
