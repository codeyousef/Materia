/**
 * Factory for creating Rapier ColliderDesc from Materia CollisionShapes
 */
package io.materia.physics.rapier.utils

import io.materia.physics.*

/**
 * Create Rapier ColliderDesc from Materia CollisionShape
 */
fun createRapierColliderDesc(shape: CollisionShape): RAPIER.ColliderDesc {
    return when (shape) {
        is BoxShape -> RAPIER.ColliderDesc.cuboid(
            shape.halfExtents.x,
            shape.halfExtents.y,
            shape.halfExtents.z
        )

        is SphereShape -> RAPIER.ColliderDesc.ball(shape.radius)
        is CapsuleShape -> RAPIER.ColliderDesc.capsule(
            shape.height / 2f,
            shape.radius
        )

        is CylinderShape -> RAPIER.ColliderDesc.cylinder(
            shape.halfExtents.y,
            shape.halfExtents.x
        )

        is ConeShape -> RAPIER.ColliderDesc.cone(
            shape.height / 2f,
            shape.radius
        )

        is ConvexHullShape -> RAPIER.ColliderDesc.convexHull(shape.vertices)
            ?: RAPIER.ColliderDesc.ball(1f) // Fallback if convex hull generation fails
        is TriangleMeshShape -> RAPIER.ColliderDesc.trimesh(shape.vertices, shape.indices)
            ?: RAPIER.ColliderDesc.ball(1f) // Fallback
        is HeightfieldShape -> RAPIER.ColliderDesc.heightfield(
            shape.height,
            shape.width,
            shape.heightData,
            RAPIER.Vector3(1f, 1f, 1f)
        )

        else -> RAPIER.ColliderDesc.ball(1f) // Default fallback
    }
}
