/**
 * Bullet collision shapes implementation for JVM platform
 * Provides shape primitives for Bullet physics engine
 */
package io.materia.physics.bullet.shapes

import io.materia.core.math.*
import io.materia.physics.*

// ==================== Box Shape ====================

internal class BulletBoxShape(override val halfExtents: Vector3) : BoxShape {
    override val shapeType = ShapeType.BOX
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val boundingBox = Box3(halfExtents.negate(), halfExtents)

    override fun getHalfExtentsWithMargin() = halfExtents.add(Vector3(margin, margin, margin))
    override fun getHalfExtentsWithoutMargin() = halfExtents

    override fun calculateInertia(mass: Float): Matrix3 {
        val x = halfExtents.x * 2f
        val y = halfExtents.y * 2f
        val z = halfExtents.z * 2f
        val factor = mass / 12f
        return Matrix3(
            floatArrayOf(
                factor * (y * y + z * z), 0f, 0f,
                0f, factor * (x * x + z * z), 0f,
                0f, 0f, factor * (x * x + y * y)
            )
        )
    }

    override fun getVolume() = 8f * halfExtents.x * halfExtents.y * halfExtents.z
    override fun getSurfaceArea() = 8f * (halfExtents.x * halfExtents.y +
            halfExtents.y * halfExtents.z + halfExtents.x * halfExtents.z)

    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) = Vector3(
        if (direction.x > 0) halfExtents.x else -halfExtents.x,
        if (direction.y > 0) halfExtents.y else -halfExtents.y,
        if (direction.z > 0) halfExtents.z else -halfExtents.z
    ).add(direction.normalize().multiply(margin))

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3(
        if (direction.x > 0) halfExtents.x else -halfExtents.x,
        if (direction.y > 0) halfExtents.y else -halfExtents.y,
        if (direction.z > 0) halfExtents.z else -halfExtents.z
    )

    override fun calculateLocalInertia(mass: Float) = Vector3(
        mass * (halfExtents.y * halfExtents.y + halfExtents.z * halfExtents.z) / 3f,
        mass * (halfExtents.x * halfExtents.x + halfExtents.z * halfExtents.z) / 3f,
        mass * (halfExtents.x * halfExtents.x + halfExtents.y * halfExtents.y) / 3f
    )

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletBoxShape(halfExtents)
}

// ==================== Sphere Shape ====================

internal class BulletSphereShape(override val radius: Float) : SphereShape {
    override val shapeType = ShapeType.SPHERE
    override val margin = 0f
    override val localScaling = Vector3.ONE
    override val boundingBox = Box3(
        Vector3(-radius, -radius, -radius),
        Vector3(radius, radius, radius)
    )

    override fun getRadiusWithMargin() = radius + margin
    override fun getRadiusWithoutMargin() = radius

    override fun calculateInertia(mass: Float): Matrix3 {
        val inertia = 0.4f * mass * radius * radius
        return Matrix3(
            floatArrayOf(
                inertia, 0f, 0f,
                0f, inertia, 0f,
                0f, 0f, inertia
            )
        )
    }

    override fun getVolume() = (4f / 3f) * kotlin.math.PI.toFloat() * radius * radius * radius
    override fun getSurfaceArea() = 4f * kotlin.math.PI.toFloat() * radius * radius
    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) =
        direction.normalize().multiply(radius + margin)

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) =
        direction.normalize().multiply(radius)

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val inertia = 0.4f * mass * radius * radius
        return Vector3(inertia, inertia, inertia)
    }

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletSphereShape(radius)
}

// ==================== Capsule Shape ====================

internal class BulletCapsuleShape(
    override val radius: Float,
    override val height: Float
) : CapsuleShape {
    override val shapeType = ShapeType.CAPSULE
    override val margin = 0f
    override val localScaling = Vector3.ONE
    override val upAxis = 1
    override val boundingBox = Box3(
        Vector3(-radius, -height / 2f - radius, -radius),
        Vector3(radius, height / 2f + radius, radius)
    )

    override fun getHalfHeight() = height / 2f

    override fun calculateInertia(mass: Float): Matrix3 {
        val cylinderMass = mass * height / (height + 2f * radius)
        val hemisphereMass = mass - cylinderMass

        val cylinderInertiaX = cylinderMass * (3f * radius * radius + height * height) / 12f
        val cylinderInertiaY = cylinderMass * radius * radius / 2f

        val hemisphereInertiaX = hemisphereMass * (2f * radius * radius / 5f + height * height / 4f)
        val hemisphereInertiaY = hemisphereMass * 2f * radius * radius / 5f

        return Matrix3(
            floatArrayOf(
                cylinderInertiaX + hemisphereInertiaX, 0f, 0f,
                0f, cylinderInertiaY + hemisphereInertiaY, 0f,
                0f, 0f, cylinderInertiaX + hemisphereInertiaX
            )
        )
    }

    override fun getVolume() = kotlin.math.PI.toFloat() * radius * radius *
            (height + 4f * radius / 3f)

    override fun getSurfaceArea() = 2f * kotlin.math.PI.toFloat() * radius *
            (2f * radius + height)

    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val dir = direction.normalize()
        if (kotlin.math.abs(dir.y) > 0.7071f) {
            return Vector3(0f, if (dir.y > 0) height / 2f + radius else -height / 2f - radius, 0f)
        }
        val horizontal = Vector3(dir.x, 0f, dir.z).normalize()
        val y = if (dir.y > 0) height / 2f else -height / 2f
        return horizontal.multiply(radius).add(Vector3(0f, y, 0f))
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) =
        localGetSupportingVertex(direction)

    override fun calculateLocalInertia(mass: Float) =
        calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletCapsuleShape(radius, height)
}

// ==================== Cylinder Shape ====================

internal class BulletCylinderShape(override val halfExtents: Vector3) : CylinderShape {
    override val shapeType = ShapeType.CYLINDER
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val upAxis = 1
    override val boundingBox = Box3(halfExtents.negate(), halfExtents)

    override fun getRadius() = halfExtents.x
    override fun getHalfHeight() = halfExtents.y

    override fun calculateInertia(mass: Float): Matrix3 {
        val radius = halfExtents.x
        val height = halfExtents.y * 2f
        val lateral = mass * (3f * radius * radius + height * height) / 12f
        val vertical = mass * radius * radius / 2f
        return Matrix3(
            floatArrayOf(
                lateral, 0f, 0f,
                0f, vertical, 0f,
                0f, 0f, lateral
            )
        )
    }

    override fun getVolume() =
        kotlin.math.PI.toFloat() * halfExtents.x * halfExtents.x * halfExtents.y * 2f

    override fun getSurfaceArea(): Float {
        val radius = halfExtents.x
        val height = halfExtents.y * 2f
        return 2f * kotlin.math.PI.toFloat() * radius * (radius + height)
    }

    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletCylinderShape(halfExtents)
}

// ==================== Cone Shape ====================

internal class BulletConeShape(
    override val radius: Float,
    override val height: Float
) : ConeShape {
    override val shapeType = ShapeType.CONE
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val upAxis = 1
    override val boundingBox = Box3(
        Vector3(-radius, -height / 2f, -radius),
        Vector3(radius, height / 2f, radius)
    )

    override fun getConeRadius() = radius
    override fun getConeHeight() = height

    override fun calculateInertia(mass: Float): Matrix3 {
        val lateral = mass * (3f * radius * radius / 20f + 3f * height * height / 80f)
        val vertical = mass * 3f * radius * radius / 10f
        return Matrix3(
            floatArrayOf(
                lateral, 0f, 0f,
                0f, vertical, 0f,
                0f, 0f, lateral
            )
        )
    }

    override fun getVolume() = kotlin.math.PI.toFloat() * radius * radius * height / 3f
    override fun getSurfaceArea(): Float {
        val slant = kotlin.math.sqrt((radius * radius + height * height).toDouble()).toFloat()
        return kotlin.math.PI.toFloat() * radius * (radius + slant)
    }

    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletConeShape(radius, height)
}

// Extension function for Matrix3
internal fun Matrix3.getDiagonal() = Vector3(m00, m11, m22)
internal fun Matrix3.add(other: Matrix3) = Matrix3(
    floatArrayOf(
        m00 + other.m00, m01 + other.m01, m02 + other.m02,
        m10 + other.m10, m11 + other.m11, m12 + other.m12,
        m20 + other.m20, m21 + other.m21, m22 + other.m22
    )
)
