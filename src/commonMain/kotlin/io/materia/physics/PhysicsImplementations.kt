package io.materia.physics

import io.materia.core.math.*
import io.materia.physics.PhysicsOperationResult.Success
import kotlin.math.PI

/**
 * Default implementations of physics components
 */

// Collision Shapes
abstract class BaseCollisionShape : CollisionShape {
    override val margin: Float = 0.01f
    override val localScaling: Vector3 = Vector3.ONE
    override val boundingBox: Box3 by lazy { calculateBoundingBox() }

    override fun calculateInertia(mass: Float): Matrix3 {
        val inertia = calculateLocalInertia(mass)
        return Matrix3(
            floatArrayOf(
                inertia.x, 0f, 0f,
                0f, inertia.y, 0f,
                0f, 0f, inertia.z
            )
        )
    }

    override fun getSurfaceArea(): Float = 0f
    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 =
        localGetSupportingVertex(direction)

    override fun serialize(): ByteArray = byteArrayOf()
    override fun clone(): CollisionShape = this

    protected abstract fun calculateBoundingBox(): Box3
}

class DefaultBoxShape(override val halfExtents: Vector3) : BaseCollisionShape(), BoxShape {
    override val shapeType: ShapeType = ShapeType.BOX

    override fun getVolume(): Float = halfExtents.x * halfExtents.y * halfExtents.z * 8f

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val extents = halfExtents * 2f
        val xx = mass / 12f * (extents.y * extents.y + extents.z * extents.z)
        val yy = mass / 12f * (extents.x * extents.x + extents.z * extents.z)
        val zz = mass / 12f * (extents.x * extents.x + extents.y * extents.y)
        return Vector3(xx, yy, zz)
    }

    override fun getHalfExtentsWithMargin(): Vector3 = halfExtents + Vector3(margin, margin, margin)
    override fun getHalfExtentsWithoutMargin(): Vector3 = halfExtents

    override fun calculateBoundingBox(): Box3 {
        val extents = getHalfExtentsWithMargin()
        return Box3(-extents, extents)
    }

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        val extents = getHalfExtentsWithMargin()
        return Vector3(
            if (direction.x >= 0f) extents.x else -extents.x,
            if (direction.y >= 0f) extents.y else -extents.y,
            if (direction.z >= 0f) extents.z else -extents.z
        )
    }
}

class DefaultSphereShape(override val radius: Float) : BaseCollisionShape(), SphereShape {
    override val shapeType: ShapeType = ShapeType.SPHERE

    override fun getVolume(): Float = (4f / 3f) * PI.toFloat() * radius * radius * radius

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val inertia = 0.4f * mass * radius * radius
        return Vector3(inertia, inertia, inertia)
    }

    override fun getRadiusWithMargin(): Float = radius + margin
    override fun getRadiusWithoutMargin(): Float = radius

    override fun calculateBoundingBox(): Box3 {
        val r = getRadiusWithMargin()
        val extents = Vector3(r, r, r)
        return Box3(-extents, extents)
    }

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        return direction.normalized() * getRadiusWithMargin()
    }
}

class DefaultCapsuleShape(
    override val radius: Float,
    override val height: Float,
    override val upAxis: Int = 1
) : BaseCollisionShape(), CapsuleShape {
    override val shapeType: ShapeType = ShapeType.CAPSULE

    override fun getVolume(): Float {
        val cylinderVolume = PI.toFloat() * radius * radius * height
        val sphereVolume = (4f / 3f) * PI.toFloat() * radius * radius * radius
        return cylinderVolume + sphereVolume
    }

    override fun getHalfHeight(): Float = height / 2f

    override fun calculateLocalInertia(mass: Float): Vector3 {
        val cylinderInertia = mass * radius * radius / 2f
        val heightInertia = mass * (radius * radius / 4f + height * height / 12f)
        return when (upAxis) {
            0 -> Vector3(heightInertia, cylinderInertia, cylinderInertia)
            1 -> Vector3(cylinderInertia, heightInertia, cylinderInertia)
            2 -> Vector3(cylinderInertia, cylinderInertia, heightInertia)
            else -> Vector3(cylinderInertia, heightInertia, cylinderInertia)
        }
    }

    override fun calculateBoundingBox(): Box3 {
        val r = radius + margin
        val h = getHalfHeight() + margin
        return when (upAxis) {
            0 -> Box3(Vector3(-h, -r, -r), Vector3(h, r, r))
            1 -> Box3(Vector3(-r, -h, -r), Vector3(r, h, r))
            2 -> Box3(Vector3(-r, -r, -h), Vector3(r, r, h))
            else -> Box3(Vector3(-r, -h, -r), Vector3(r, h, r))
        }
    }
}

// DefaultRigidBody is defined in RigidBody.kt

// Character Controller Implementation
class DefaultCharacterController(
    override val id: String,
    initialShape: CollisionShape,
    override var stepHeight: Float = 0.3f
) : CollisionObject, CharacterController {

    // Basic CollisionObject properties
    override var transform: Matrix4 = Matrix4.identity()
    override var collisionShape: CollisionShape = initialShape
    override var collisionGroups: Int = 1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 1e30f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    // Position property for easier access
    var position: Vector3
        get() = transform.getTranslation()
        set(value) {
            transform = Matrix4.translation(value.x, value.y, value.z)
        }

    // Velocity properties (simplified - not from RigidBody)
    var linearVelocity: Vector3 = Vector3.ZERO
    var angularVelocity: Vector3 = Vector3.ZERO

    override var maxSlope: Float = PI.toFloat() / 4f // 45 degrees
    override var jumpSpeed: Float = 10f
    override var fallSpeed: Float = 55f
    override var walkDirection: Vector3 = Vector3.ZERO
    override var velocityForTimeInterval: Vector3 = Vector3.ZERO

    private var onGroundFlag: Boolean = false
    private var canJumpFlag: Boolean = true

    override fun onGround(): Boolean = onGroundFlag

    override fun canJump(): Boolean = canJumpFlag && onGroundFlag

    override fun jump(direction: Vector3) {
        if (canJump()) {
            linearVelocity = linearVelocity + direction * jumpSpeed
            onGroundFlag = false
            canJumpFlag = false
        }
    }


    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {
        velocityForTimeInterval = velocity
        // Apply velocity immediately for simplified implementation
        linearVelocity = velocity
    }

    override fun warp(origin: Vector3) {
        position = origin
        linearVelocity = Vector3.ZERO
        angularVelocity = Vector3.ZERO
    }

    override fun preStep(world: PhysicsWorld) {
        // Update ground detection
        val result = world.raycast(
            from = position,
            to = position + Vector3(0f, -stepHeight - 0.1f, 0f)
        )
        onGroundFlag = result?.hasHit == true
        if (onGroundFlag) {
            canJumpFlag = true
        }
    }

    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {
        preStep(world)

        // Apply walk direction
        if (walkDirection.length() > 0f) {
            val moveVelocity = walkDirection.normalized() * 5f // Default walking speed
            linearVelocity = Vector3(moveVelocity.x, linearVelocity.y, moveVelocity.z)
        }

        // Apply gravity if not on ground
        if (!onGroundFlag) {
            linearVelocity = linearVelocity + Vector3(0f, -fallSpeed * deltaTime, 0f)
        }
    }

    // CollisionObject interface methods
    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        collisionShape = shape
        return Success(Unit)
    }


    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        position = position + offset
    }

    // Missing abstract members from parent interfaces


    override fun rotate(rotation: Quaternion) {
        transform = transform.rotate(rotation)
    }

    fun move(displacement: Vector3) {
        position = position + displacement
    }
}

// Constraint Implementations
abstract class BaseConstraint(
    override val id: String,
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?
) : PhysicsConstraint {

    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE

    private val parameters = mutableMapOf<Pair<ConstraintParam, Int>, Float>()

    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {
        parameters[Pair(param, axis)] = value
    }

    override fun getParam(param: ConstraintParam, axis: Int): Float {
        return parameters[Pair(param, axis)] ?: 0f
    }

    override fun getAppliedImpulse(): Float = 0f
    override fun isEnabled(): Boolean = enabled


    override fun getInfo(info: ConstraintInfo) {
        // Default constraint info
    }
}

class DefaultHingeConstraint(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3,
    override val axisA: Vector3,
    override val axisB: Vector3
) : BaseConstraint(id, bodyA, bodyB), HingeConstraint {

    override var lowerLimit: Float = 1f
    override var upperLimit: Float = -1f // Disabled when lower > upper
    override var enableAngularMotor: Boolean = false
    override var targetVelocity: Float = 0f
    override var maxMotorImpulse: Float = 0f

    override fun setLimit(
        low: Float,
        high: Float,
        softness: Float,
        biasFactor: Float,
        relaxationFactor: Float
    ) {
        lowerLimit = low
        upperLimit = high
    }

    override fun enableMotor(enable: Boolean) {
        enableAngularMotor = enable
    }

    override fun setMotorTarget(targetAngle: Float, deltaTime: Float) {
        targetVelocity = targetAngle / deltaTime
    }

    override fun getHingeAngle(): Float = 0f // Simplified implementation
}

/**
 * Default Physics Engine Implementation
 */
class DefaultPhysicsEngine : PhysicsEngine {
    override val name: String = "Default Materia Physics Engine"
    override val version: String = "1.0.0"

    private val worlds = mutableListOf<PhysicsWorld>()

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        val world = DefaultPhysicsWorld(gravity)
        worlds.add(world)
        return world
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit> {
        worlds.remove(world)
        if (world is DefaultPhysicsWorld) {
            world.dispose()
        }
        return Success(Unit)
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape = DefaultBoxShape(halfExtents)
    override fun createSphereShape(radius: Float): SphereShape = DefaultSphereShape(radius)
    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape =
        DefaultCapsuleShape(radius, height)

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return object : BaseCollisionShape(), CylinderShape {
            override val shapeType: ShapeType = ShapeType.CYLINDER
            override val halfExtents: Vector3 = halfExtents
            override val upAxis: Int = 1
            override fun getRadius(): Float = maxOf(halfExtents.x, halfExtents.z)
            override fun getHalfHeight(): Float = halfExtents.y
            override fun getVolume(): Float =
                PI.toFloat() * getRadius() * getRadius() * getHalfHeight() * 2f

            override fun calculateLocalInertia(mass: Float): Vector3 =
                Vector3(mass / 12f, mass / 2f, mass / 12f)

            override fun calculateBoundingBox(): Box3 = Box3(-halfExtents, halfExtents)
        }
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return object : BaseCollisionShape(), ConeShape {
            override val shapeType: ShapeType = ShapeType.CONE
            override val radius: Float = radius
            override val height: Float = height
            override val upAxis: Int = 1
            override fun getConeRadius(): Float = radius
            override fun getConeHeight(): Float = height
            override fun getVolume(): Float = PI.toFloat() * radius * radius * height / 3f
            override fun calculateLocalInertia(mass: Float): Vector3 =
                Vector3(mass / 20f, mass / 10f, mass / 20f)

            override fun calculateBoundingBox(): Box3 =
                Box3(Vector3(-radius, 0f, -radius), Vector3(radius, height, radius))
        }
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return object : BaseCollisionShape(), ConvexHullShape {
            override val shapeType: ShapeType = ShapeType.CONVEX_HULL
            override val vertices: FloatArray = vertices
            override val numVertices: Int = vertices.size / 3
            override fun getVolume(): Float = 1f
            override fun calculateLocalInertia(mass: Float): Vector3 =
                Vector3(mass / 6f, mass / 6f, mass / 6f)

            override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {}
            override fun getScaledPoint(index: Int): Vector3 = Vector3.ZERO
            override fun getUnscaledPoints(): List<Vector3> = emptyList()
            override fun optimizeConvexHull() {}
            override fun calculateBoundingBox(): Box3 = Box3.empty()
        }
    }

    override fun createTriangleMeshShape(
        vertices: FloatArray,
        indices: IntArray
    ): TriangleMeshShape {
        return object : BaseCollisionShape(), TriangleMeshShape {
            override val shapeType: ShapeType = ShapeType.TRIANGLE_MESH
            override val vertices: FloatArray = vertices
            override val indices: IntArray = indices
            override val triangleCount: Int = indices.size / 3
            override fun getVolume(): Float = 0f
            override fun isConvex(): Boolean = false
            override fun calculateLocalInertia(mass: Float): Vector3 = Vector3.ZERO
            override fun getTriangle(index: Int): Triangle =
                Triangle(Vector3.ZERO, Vector3.ZERO, Vector3.ZERO)

            override fun processAllTriangles(
                callback: TriangleCallback,
                aabbMin: Vector3,
                aabbMax: Vector3
            ) {
            }

            override fun buildBVH(): MeshBVH = MeshBVH(
                nodes = emptyList(),
                triangles = emptyList()
            )

            override fun calculateBoundingBox(): Box3 = Box3.empty()
        }
    }

    override fun createHeightfieldShape(
        width: Int,
        height: Int,
        heightData: FloatArray
    ): HeightfieldShape {
        return object : BaseCollisionShape(), HeightfieldShape {
            override val shapeType: ShapeType = ShapeType.HEIGHTFIELD
            override val width: Int = width
            override val height: Int = height
            override val heightData: FloatArray = heightData
            override val maxHeight: Float = heightData.maxOrNull() ?: 0f
            override val minHeight: Float = heightData.minOrNull() ?: 0f
            override val upAxis: Int = 1
            override fun getVolume(): Float = 0f
            override fun isConvex(): Boolean = false
            override fun calculateLocalInertia(mass: Float): Vector3 = Vector3.ZERO
            override fun getHeightAtPoint(x: Float, z: Float): Float = 0f
            override fun setHeightValue(x: Int, z: Int, height: Float) {}
            override fun calculateBoundingBox(): Box3 = Box3.empty()
        }
    }

    override fun createCompoundShape(): CompoundShape {
        return object : BaseCollisionShape(), CompoundShape {
            override val shapeType: ShapeType = ShapeType.COMPOUND
            override val childShapes: List<ChildShape> = mutableListOf()
            override fun isCompound(): Boolean = true
            override fun getVolume(): Float =
                childShapes.sumOf { it.shape.getVolume().toDouble() }.toFloat()

            override fun calculateLocalInertia(mass: Float): Vector3 =
                Vector3(mass / 6f, mass / 6f, mass / 6f)

            override fun addChildShape(
                transform: Matrix4,
                shape: CollisionShape
            ): PhysicsResult<Unit> = Success(Unit)

            override fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit> =
                Success(Unit)

            override fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit> = Success(Unit)
            override fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit> =
                Success(Unit)

            override fun recalculateLocalAabb() {}
            override fun calculateBoundingBox(): Box3 = Box3.empty()
        }
    }

    private var nextId = 0

    override fun createRigidBody(
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4
    ): RigidBody {
        return DefaultRigidBody("body_${++nextId}", shape, mass, transform)
    }

    override fun createCharacterController(
        shape: CollisionShape,
        stepHeight: Float
    ): CharacterController {
        return DefaultCharacterController("controller_${++nextId}", shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        return object : BaseConstraint("p2p_${++nextId}", bodyA, bodyB), PointToPointConstraint {
            override val pivotA: Vector3 = pivotA
            override val pivotB: Vector3 = pivotB
            override fun setPivotA(pivot: Vector3) {}
            override fun setPivotB(pivot: Vector3) {}
            override fun updateRHS(timeStep: Float) {}
        }
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        return DefaultHingeConstraint(
            "hinge_${++nextId}",
            bodyA,
            bodyB,
            pivotA,
            pivotB,
            axisA,
            axisB
        )
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        return object : BaseConstraint("slider_${++nextId}", bodyA, bodyB), SliderConstraint {
            override val frameA: Matrix4 = frameA
            override val frameB: Matrix4 = frameB
            override var lowerLinearLimit: Float = 1f
            override var upperLinearLimit: Float = -1f
            override var lowerAngularLimit: Float = 0f
            override var upperAngularLimit: Float = 0f
            override var poweredLinearMotor: Boolean = false
            override var targetLinearMotorVelocity: Float = 0f
            override var maxLinearMotorForce: Float = 0f
            override var poweredAngularMotor: Boolean = false
            override var targetAngularMotorVelocity: Float = 0f
            override var maxAngularMotorForce: Float = 0f
            override fun getLinearPos(): Float = 0f
            override fun getAngularPos(): Float = 0f
        }
    }
}