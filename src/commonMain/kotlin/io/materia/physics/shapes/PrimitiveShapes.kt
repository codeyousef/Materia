/**
 * Primitive collision shapes (Box, Sphere, Capsule, Cylinder, Cone)
 */
package io.materia.physics.shapes

import io.materia.core.math.*
import io.materia.physics.*
import kotlin.math.*

/**
 * Box collision shape implementation
 */
class BoxShapeImpl(
    override val halfExtents: Vector3
) : CollisionShapeImpl(), BoxShape {

    override val shapeType: ShapeType = ShapeType.BOX

    init {
        require(halfExtents.x > 0f && halfExtents.y > 0f && halfExtents.z > 0f) {
            "Box half-extents must be positive"
        }
        calculateBoundingBox()
    }

    override fun getHalfExtentsWithMargin(): Vector3 {
        return Vector3(
            halfExtents.x + margin,
            halfExtents.y + margin,
            halfExtents.z + margin
        ) * localScaling
    }

    override fun getHalfExtentsWithoutMargin(): Vector3 {
        return (halfExtents * localScaling)
    }

    override fun getVolume(): Float {
        val scaledExtents = halfExtents * localScaling
        return 8f * scaledExtents.x * scaledExtents.y * scaledExtents.z
    }

    override fun getSurfaceArea(): Float {
        val scaledExtents = halfExtents * localScaling
        return 8f * (scaledExtents.x * scaledExtents.y +
                scaledExtents.y * scaledExtents.z +
                scaledExtents.z * scaledExtents.x)
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val scaledExtents = getHalfExtentsWithMargin()
        return Vector3(
            if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
            if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
            if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
        )
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        val scaledExtents = getHalfExtentsWithoutMargin()
        return Vector3(
            if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
            if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
            if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
        )
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val scaledExtents = halfExtents * localScaling
        val factor = mass / 3f
        return Vector3(
            factor * (scaledExtents.y * scaledExtents.y + scaledExtents.z * scaledExtents.z),
            factor * (scaledExtents.x * scaledExtents.x + scaledExtents.z * scaledExtents.z),
            factor * (scaledExtents.x * scaledExtents.x + scaledExtents.y * scaledExtents.y)
        )
    }

    override fun calculateBoundingBox() {
        val extentsWithMargin = getHalfExtentsWithMargin()
        _boundingBox = Box3(-extentsWithMargin, extentsWithMargin)
    }

    override fun clone(): CollisionShape = BoxShapeImpl(halfExtents).apply {
        margin = this@BoxShapeImpl.margin
        localScaling = this@BoxShapeImpl.localScaling
    }
}

/**
 * Sphere collision shape implementation
 */
class SphereShapeImpl(
    override val radius: Float
) : CollisionShapeImpl(), SphereShape {

    override val shapeType: ShapeType = ShapeType.SPHERE

    init {
        require(radius > 0f) { "Sphere radius must be positive" }
        calculateBoundingBox()
    }

    override fun getRadiusWithMargin(): Float {
        return (radius + margin) * localScaling.maxComponent()
    }

    override fun getRadiusWithoutMargin(): Float {
        return radius * localScaling.maxComponent()
    }

    override fun getVolume(): Float {
        val scaledRadius = getRadiusWithoutMargin()
        return (4f / 3f) * PI.toFloat() * scaledRadius * (scaledRadius * scaledRadius)
    }

    override fun getSurfaceArea(): Float {
        val scaledRadius = getRadiusWithoutMargin()
        return 4f * PI.toFloat() * (scaledRadius * scaledRadius)
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val normalizedDirection = direction.normalize()
        return normalizedDirection * getRadiusWithMargin()
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        val normalizedDirection = direction.normalize()
        return normalizedDirection * getRadiusWithoutMargin()
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val scaledRadius = getRadiusWithoutMargin()
        val inertia = 0.4f * mass * scaledRadius * scaledRadius
        return Vector3(inertia, inertia, inertia)
    }

    override fun calculateBoundingBox() {
        val radiusWithMargin = getRadiusWithMargin()
        val extent = Vector3(radiusWithMargin, radiusWithMargin, radiusWithMargin)
        _boundingBox = Box3(-extent, extent)
    }

    override fun clone(): CollisionShape = SphereShapeImpl(radius).apply {
        margin = this@SphereShapeImpl.margin
        localScaling = this@SphereShapeImpl.localScaling
    }
}

/**
 * Capsule collision shape implementation (pill shape)
 */
class CapsuleShapeImpl(
    override val radius: Float,
    override val height: Float,
    override val upAxis: Int = 1 // Y-axis by default
) : CollisionShapeImpl(), CapsuleShape {

    override val shapeType: ShapeType = ShapeType.CAPSULE

    init {
        require(radius > 0f) { "Capsule radius must be positive" }
        require(height > 0f) { "Capsule height must be positive" }
        require(upAxis in 0..2) { "Up axis must be 0 (X), 1 (Y), or 2 (Z)" }
        calculateBoundingBox()
    }

    override fun getHalfHeight(): Float = height * 0.5f

    override fun getVolume(): Float {
        val scaledRadius = radius * localScaling.maxComponent()
        val scaledHeight = height * when (upAxis) {
            0 -> localScaling.x
            1 -> localScaling.y
            2 -> localScaling.z
            else -> localScaling.y
        }

        // Volume = cylinder + two hemispheres
        val cylinderVolume = PI.toFloat() * scaledRadius * scaledRadius * scaledHeight
        val sphereVolume = (4f / 3f) * PI.toFloat() * scaledRadius * scaledRadius * scaledRadius
        return cylinderVolume + sphereVolume
    }

    override fun getSurfaceArea(): Float {
        val scaledRadius = radius * localScaling.maxComponent()
        val scaledHeight = height * when (upAxis) {
            0 -> localScaling.x
            1 -> localScaling.y
            2 -> localScaling.z
            else -> localScaling.y
        }

        // Surface area = cylinder side + two hemispheres
        val cylinderSide = 2f * PI.toFloat() * scaledRadius * scaledHeight
        val sphereSurface = 4f * PI.toFloat() * scaledRadius * scaledRadius
        return cylinderSide + sphereSurface
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val scaledRadius = (radius + margin) * localScaling.maxComponent()
        val halfHeight = getHalfHeight()

        val upVector = when (upAxis) {
            0 -> Vector3.UNIT_X
            1 -> Vector3.UNIT_Y
            2 -> Vector3.UNIT_Z
            else -> Vector3.UNIT_Y
        }

        val upComponent = direction.dot(upVector)
        val center = if (upComponent > 0f) upVector * halfHeight else -upVector * halfHeight

        val directionInPlane = direction - upVector * upComponent
        val normalizedPlane = if (directionInPlane.length() > 0f) {
            directionInPlane.normalize()
        } else {
            Vector3.ZERO
        }

        return center + (normalizedPlane * scaledRadius)
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        val scaledRadius = radius * localScaling.maxComponent()
        val halfHeight = getHalfHeight()

        val upVector = when (upAxis) {
            0 -> Vector3.UNIT_X
            1 -> Vector3.UNIT_Y
            2 -> Vector3.UNIT_Z
            else -> Vector3.UNIT_Y
        }

        val upComponent = direction.dot(upVector)
        val center = if (upComponent > 0f) upVector * halfHeight else -upVector * halfHeight

        val directionInPlane = direction - upVector * upComponent
        val normalizedPlane = if (directionInPlane.length() > 0f) {
            directionInPlane.normalize()
        } else {
            Vector3.ZERO
        }

        return center + (normalizedPlane * scaledRadius)
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val scaledRadius = radius * localScaling.maxComponent()
        val scaledHeight = height * when (upAxis) {
            0 -> localScaling.x
            1 -> localScaling.y
            2 -> localScaling.z
            else -> localScaling.y
        }

        // Approximate inertia for capsule
        val cylinderMass = mass * (scaledHeight / (scaledHeight + 4f * scaledRadius / 3f))
        val sphereMass = mass - cylinderMass

        val radiusSquared = scaledRadius * scaledRadius
        val heightSquared = scaledHeight * scaledHeight

        return when (upAxis) {
            0 -> Vector3(
                cylinderMass * radiusSquared * 0.5f + sphereMass * radiusSquared * 0.4f,
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f,
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f
            )

            2 -> Vector3(
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f,
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f,
                cylinderMass * radiusSquared * 0.5f + sphereMass * radiusSquared * 0.4f
            )

            else -> Vector3( // Y-axis default
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f,
                cylinderMass * radiusSquared * 0.5f + sphereMass * radiusSquared * 0.4f,
                cylinderMass * (radiusSquared * 0.25f + heightSquared / 12f) + sphereMass * radiusSquared * 0.4f
            )
        }
    }

    override fun calculateBoundingBox() {
        val scaledRadius = (radius + margin) * localScaling.maxComponent()
        val halfHeight = getHalfHeight()

        val extent = when (upAxis) {
            0 -> Vector3(halfHeight + scaledRadius, scaledRadius, scaledRadius)
            1 -> Vector3(scaledRadius, halfHeight + scaledRadius, scaledRadius)
            2 -> Vector3(scaledRadius, scaledRadius, halfHeight + scaledRadius)
            else -> Vector3(scaledRadius, halfHeight + scaledRadius, scaledRadius)
        }

        _boundingBox = Box3(-extent, extent)
    }

    override fun clone(): CollisionShape = CapsuleShapeImpl(radius, height, upAxis).apply {
        margin = this@CapsuleShapeImpl.margin
        localScaling = this@CapsuleShapeImpl.localScaling
    }
}

/**
 * Cylinder collision shape implementation
 */
class CylinderShapeImpl(
    override val halfExtents: Vector3,
    override val upAxis: Int = 1
) : CollisionShapeImpl(), CylinderShape {

    override val shapeType: ShapeType = ShapeType.CYLINDER

    init {
        require(halfExtents.x > 0f && halfExtents.y > 0f && halfExtents.z > 0f) {
            "Cylinder half-extents must be positive"
        }
        require(upAxis in 0..2) { "Up axis must be 0 (X), 1 (Y), or 2 (Z)" }
        calculateBoundingBox()
    }

    override fun getRadius(): Float {
        return when (upAxis) {
            0 -> maxOf(halfExtents.y, halfExtents.z)
            1 -> maxOf(halfExtents.x, halfExtents.z)
            2 -> maxOf(halfExtents.x, halfExtents.y)
            else -> maxOf(halfExtents.x, halfExtents.z)
        } * localScaling.maxComponent()
    }

    override fun getHalfHeight(): Float {
        return when (upAxis) {
            0 -> halfExtents.x
            1 -> halfExtents.y
            2 -> halfExtents.z
            else -> halfExtents.y
        } * localScaling.componentAt(upAxis)
    }

    override fun getVolume(): Float {
        val scaledRadius = getRadius()
        val scaledHeight = getHalfHeight() * 2f
        return PI.toFloat() * scaledRadius * (scaledRadius * scaledHeight)
    }

    override fun getSurfaceArea(): Float {
        val scaledRadius = getRadius()
        val scaledHeight = getHalfHeight() * 2f
        // Surface area = 2 * base area + side area
        return 2f * PI.toFloat() * scaledRadius * scaledRadius + 2f * PI.toFloat() * (scaledRadius * scaledHeight)
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val scaledExtents = (halfExtents + Vector3(margin, margin, margin)) * localScaling

        return when (upAxis) {
            0 -> Vector3(
                if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
                if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
                if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
            )

            2 -> Vector3(
                if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
                if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
                if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
            )

            else -> Vector3( // Y-axis default
                if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
                if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
                if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
            )
        }
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        val scaledExtents = halfExtents * localScaling

        return Vector3(
            if (direction.x >= 0f) scaledExtents.x else -scaledExtents.x,
            if (direction.y >= 0f) scaledExtents.y else -scaledExtents.y,
            if (direction.z >= 0f) scaledExtents.z else -scaledExtents.z
        )
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val scaledRadius = getRadius()
        val scaledHeight = getHalfHeight()

        val radiusSquared = scaledRadius * scaledRadius
        val heightSquared = ((scaledHeight * 2f)) * ((scaledHeight * 2f))

        return when (upAxis) {
            0 -> Vector3(
                mass * radiusSquared * 0.5f,
                mass * (radiusSquared * 0.25f + heightSquared / 12f),
                mass * (radiusSquared * 0.25f + heightSquared / 12f)
            )

            2 -> Vector3(
                mass * (radiusSquared * 0.25f + heightSquared / 12f),
                mass * (radiusSquared * 0.25f + heightSquared / 12f),
                mass * radiusSquared * 0.5f
            )

            else -> Vector3( // Y-axis default
                mass * (radiusSquared * 0.25f + heightSquared / 12f),
                mass * radiusSquared * 0.5f,
                mass * (radiusSquared * 0.25f + heightSquared / 12f)
            )
        }
    }

    override fun calculateBoundingBox() {
        val scaledExtents = (halfExtents + Vector3(margin, margin, margin)) * localScaling
        _boundingBox = Box3(-scaledExtents, scaledExtents)
    }

    override fun clone(): CollisionShape = CylinderShapeImpl(halfExtents, upAxis).apply {
        margin = this@CylinderShapeImpl.margin
        localScaling = this@CylinderShapeImpl.localScaling
    }
}

/**
 * Cone collision shape implementation
 */
class ConeShapeImpl(
    override val radius: Float,
    override val height: Float,
    override val upAxis: Int = 1
) : CollisionShapeImpl(), ConeShape {

    override val shapeType: ShapeType = ShapeType.CONE

    init {
        require(radius > 0f) { "Cone radius must be positive" }
        require(height > 0f) { "Cone height must be positive" }
        require(upAxis in 0..2) { "Up axis must be 0 (X), 1 (Y), or 2 (Z)" }
        calculateBoundingBox()
    }

    override fun getConeRadius(): Float = radius * localScaling.maxComponent()
    override fun getConeHeight(): Float = height * localScaling.componentAt(upAxis)

    override fun getVolume(): Float {
        val scaledRadius = getConeRadius()
        val scaledHeight = getConeHeight()
        return (1f / 3f) * PI.toFloat() * scaledRadius * (scaledRadius * scaledHeight)
    }

    override fun getSurfaceArea(): Float {
        val scaledRadius = getConeRadius()
        val scaledHeight = getConeHeight()
        val slantHeight = sqrt(scaledRadius * scaledRadius + (scaledHeight * scaledHeight))
        // Surface area = base area + lateral area
        return PI.toFloat() * scaledRadius * scaledRadius + PI.toFloat() * (scaledRadius * slantHeight)
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val scaledRadius = getConeRadius() + margin
        val scaledHeight = getConeHeight()

        val upVector = when (upAxis) {
            0 -> Vector3.UNIT_X
            1 -> Vector3.UNIT_Y
            2 -> Vector3.UNIT_Z
            else -> Vector3.UNIT_Y
        }

        val upComponent = direction.dot(upVector)

        // If direction points toward the tip
        if (upComponent > cos(atan2(scaledRadius, scaledHeight))) {
            return upVector * scaledHeight * 0.5f
        }

        // Otherwise, support is on the base
        val directionInPlane = direction - upVector * upComponent
        val normalizedPlane = if (directionInPlane.length() > 0f) {
            directionInPlane.normalize()
        } else {
            Vector3.ZERO
        }

        return -upVector * scaledHeight * 0.5f + (normalizedPlane * scaledRadius)
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        val scaledRadius = getConeRadius()
        val scaledHeight = getConeHeight()

        val upVector = when (upAxis) {
            0 -> Vector3.UNIT_X
            1 -> Vector3.UNIT_Y
            2 -> Vector3.UNIT_Z
            else -> Vector3.UNIT_Y
        }

        val upComponent = direction.dot(upVector)

        // If direction points toward the tip
        if (upComponent > cos(atan2(scaledRadius, scaledHeight))) {
            return upVector * scaledHeight * 0.5f
        }

        // Otherwise, support is on the base
        val directionInPlane = direction - upVector * upComponent
        val normalizedPlane = if (directionInPlane.length() > 0f) {
            directionInPlane.normalize()
        } else {
            Vector3.ZERO
        }

        return -upVector * scaledHeight * 0.5f + (normalizedPlane * scaledRadius)
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val scaledRadius = getConeRadius()
        val scaledHeight = getConeHeight()

        val radiusSquared = scaledRadius * scaledRadius
        val heightSquared = scaledHeight * scaledHeight

        return when (upAxis) {
            0 -> Vector3(
                mass * radiusSquared * 0.3f,
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f),
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f)
            )

            2 -> Vector3(
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f),
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f),
                mass * radiusSquared * 0.3f
            )

            else -> Vector3( // Y-axis default
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f),
                mass * radiusSquared * 0.3f,
                mass * (radiusSquared * 0.15f + heightSquared * 0.2f)
            )
        }
    }

    override fun calculateBoundingBox() {
        val scaledRadius = getConeRadius() + margin
        val scaledHeight = getConeHeight()

        val extent = when (upAxis) {
            0 -> Vector3(scaledHeight * 0.5f, scaledRadius, scaledRadius)
            1 -> Vector3(scaledRadius, scaledHeight * 0.5f, scaledRadius)
            2 -> Vector3(scaledRadius, scaledRadius, scaledHeight * 0.5f)
            else -> Vector3(scaledRadius, scaledHeight * 0.5f, scaledRadius)
        }

        _boundingBox = Box3(-extent, extent)
    }

    override fun clone(): CollisionShape = ConeShapeImpl(radius, height, upAxis).apply {
        margin = this@ConeShapeImpl.margin
        localScaling = this@ConeShapeImpl.localScaling
    }
}
