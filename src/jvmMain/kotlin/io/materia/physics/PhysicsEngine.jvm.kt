package io.materia.physics

import io.materia.core.math.Box3
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3

/**
 * JVM implementation of physics engine
 * Note: This is a basic implementation. For production use, integrate Bullet Physics via JNI
 * or use a Java physics library like ODE4J or jBullet.
 */
actual fun createDefaultPhysicsEngine(): PhysicsEngine = JvmPhysicsEngine()

/**
 * Basic JVM physics engine implementation
 */
class JvmPhysicsEngine : PhysicsEngine {
    override val name: String = "JVM Basic Physics"
    override val version: String = "1.0.0"

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        return JvmPhysicsWorld(gravity)
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit> {
        return if (world is JvmPhysicsWorld) {
            world.dispose()
            PhysicsOperationResult.Success(Unit)
        } else {
            PhysicsOperationResult.Error<Unit>(PhysicsException.InvalidOperation("Invalid world type"))
        }
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape {
        return JvmBoxShape(halfExtents)
    }

    override fun createSphereShape(radius: Float): SphereShape {
        return JvmSphereShape(radius)
    }

    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape {
        return JvmCapsuleShape(radius, height)
    }

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return JvmCylinderShape(halfExtents)
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return JvmConeShape(radius, height)
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return JvmConvexHullShape(vertices)
    }

    override fun createTriangleMeshShape(
        vertices: FloatArray,
        indices: IntArray
    ): TriangleMeshShape {
        return JvmTriangleMeshShape(vertices, indices)
    }

    override fun createHeightfieldShape(
        width: Int,
        height: Int,
        heightData: FloatArray
    ): HeightfieldShape {
        return JvmHeightfieldShape(width, height, heightData)
    }

    override fun createCompoundShape(): CompoundShape {
        return JvmCompoundShape()
    }

    override fun createRigidBody(
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4
    ): RigidBody {
        return JvmRigidBody(shape, mass, transform)
    }

    override fun createCharacterController(
        shape: CollisionShape,
        stepHeight: Float
    ): CharacterController {
        return JvmCharacterController(shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        return JvmPointToPointConstraint(bodyA, bodyB, pivotA, pivotB)
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        return JvmHingeConstraint(bodyA, bodyB, pivotA, pivotB, axisA, axisB)
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        return JvmSliderConstraint(bodyA, bodyB, frameA, frameB)
    }
}

/**
 * Basic physics world implementation for JVM
 */
class JvmPhysicsWorld(override var gravity: Vector3) : PhysicsWorld {
    private val rigidBodies = mutableListOf<RigidBody>()
    private val constraints = mutableListOf<PhysicsConstraint>()
    private val collisionObjects = mutableListOf<CollisionObject>()
    private var collisionCallback: CollisionCallback? = null
    private var isPaused = false

    override var timeStep: Float = 1f / 60f
    override var maxSubSteps: Int = 10
    override var solverIterations: Int = 10
    override var broadphase: BroadphaseType = BroadphaseType.DBVT

    override fun addRigidBody(body: RigidBody): PhysicsResult<Unit> {
        rigidBodies.add(body)
        collisionObjects.add(body)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeRigidBody(body: RigidBody): PhysicsResult<Unit> {
        rigidBodies.remove(body)
        collisionObjects.remove(body)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun getRigidBodies(): List<RigidBody> = rigidBodies.toList()

    override fun getRigidBody(id: String): RigidBody? = rigidBodies.find { it.id == id }

    override fun addConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        constraints.add(constraint)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        constraints.remove(constraint)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun getConstraints(): List<PhysicsConstraint> = constraints.toList()

    override fun addCollisionObject(obj: CollisionObject): PhysicsResult<Unit> {
        collisionObjects.add(obj)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeCollisionObject(obj: CollisionObject): PhysicsResult<Unit> {
        collisionObjects.remove(obj)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setCollisionCallback(callback: CollisionCallback) {
        collisionCallback = callback
    }

    override fun step(deltaTime: Float): PhysicsResult<Unit> {
        if (isPaused) return PhysicsOperationResult.Success(Unit)

        // Basic physics simulation
        val subSteps = minOf((deltaTime / timeStep).toInt() + 1, maxSubSteps)
        val subDeltaTime = deltaTime / subSteps

        for (i in 0 until subSteps) {
            // Apply gravity to dynamic bodies
            rigidBodies.forEach { body ->
                if (body.bodyType == RigidBodyType.DYNAMIC && body.isActive()) {
                    // Apply gravity
                    body.applyCentralForce(gravity.times(body.mass))

                    // Simple integration
                    val position = body.transform.getTranslation()
                    val newPosition = position.plus(body.linearVelocity.times(subDeltaTime))
                    body.translate(newPosition.minus(position))

                    // Apply damping
                    body.linearVelocity =
                        body.linearVelocity.times(1f - body.linearDamping * subDeltaTime)
                    body.angularVelocity =
                        body.angularVelocity.times(1f - body.angularDamping * subDeltaTime)
                }
            }

            // Process constraints
            constraints.forEach { constraint ->
                if (constraint.enabled) {
                    // Basic constraint solving would go here
                }
            }
        }

        return PhysicsOperationResult.Success(Unit)
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun reset() {
        rigidBodies.clear()
        constraints.clear()
        collisionObjects.clear()
        isPaused = false
    }

    override fun raycast(from: Vector3, to: Vector3, groups: Int): RaycastResult? {
        // Basic ray-sphere intersection for demonstration
        val direction = (to - from).normalize()
        val maxDistance = (to - from).length()

        var closestHit: RaycastResult? = null
        var closestDistance = Float.MAX_VALUE

        collisionObjects.forEach { obj ->
            if (obj.collisionGroups and groups != 0) {
                // Simplified ray-bounding box test
                val boundingBox = obj.collisionShape.boundingBox
                val hit = rayBoxIntersection(from, direction, boundingBox, obj.transform)

                hit?.let {
                    if (it.distance < closestDistance && it.distance <= maxDistance) {
                        closestDistance = it.distance
                        closestHit = JvmRaycastResult(
                            collisionObject = obj,
                            point = it.point,
                            normal = it.normal,
                            distanceValue = it.distance
                        )
                    }
                }
            }
        }

        return closestHit
    }

    override fun sphereCast(center: Vector3, radius: Float, groups: Int): List<CollisionObject> {
        return collisionObjects.filter { obj ->
            if (obj.collisionGroups and groups == 0) return@filter false

            val objPos = obj.transform.getTranslation()
            val distance = (objPos - center).length()

            // Simple sphere-bounding sphere test
            val objRadius = obj.collisionShape.boundingBox.getSize().length() * 0.5f
            distance <= radius + objRadius
        }
    }

    override fun boxCast(
        center: Vector3,
        halfExtents: Vector3,
        rotation: Quaternion,
        groups: Int
    ): List<CollisionObject> {
        // Simplified box overlap test
        return collisionObjects.filter { obj ->
            obj.collisionGroups and groups != 0
            // More complex box-box intersection would go here
        }
    }

    override fun overlaps(
        shape: CollisionShape,
        transform: Matrix4,
        groups: Int
    ): List<CollisionObject> {
        return collisionObjects.filter { obj ->
            obj.collisionGroups and groups != 0
            // Shape overlap tests would go here
        }
    }

    fun dispose() {
        reset()
    }

    private fun rayBoxIntersection(
        origin: Vector3,
        direction: Vector3,
        box: Box3,
        transform: Matrix4
    ): RayHit? {
        // Transform ray to box space
        val invTransform = transform.invert()
        val localOrigin = invTransform.multiplyPoint3(origin)
        val localEnd = invTransform.multiplyPoint3(origin + direction)
        val localDirection = (localEnd - localOrigin).normalize()

        // Ray-AABB intersection in local space
        val tMin = (box.min - localOrigin) / localDirection
        val tMax = (box.max - localOrigin) / localDirection

        val t1 = minOf(tMin.x, tMax.x)
        val t2 = maxOf(tMin.x, tMax.x)
        val t3 = minOf(tMin.y, tMax.y)
        val t4 = maxOf(tMin.y, tMax.y)
        val t5 = minOf(tMin.z, tMax.z)
        val t6 = maxOf(tMin.z, tMax.z)

        val tNear = maxOf(t1, t3, t5)
        val tFar = minOf(t2, t4, t6)

        if (tNear > tFar || tFar < 0) return null

        val t = if (tNear < 0) tFar else tNear
        val localHitPoint = localOrigin + localDirection * t
        val worldHitPoint = transform.multiplyPoint3(localHitPoint)

        // Calculate normal
        val normal = when {
            kotlin.math.abs(localHitPoint.x - box.min.x) < 0.001f -> Vector3(-1f, 0f, 0f)
            kotlin.math.abs(localHitPoint.x - box.max.x) < 0.001f -> Vector3(1f, 0f, 0f)
            kotlin.math.abs(localHitPoint.y - box.min.y) < 0.001f -> Vector3(0f, -1f, 0f)
            kotlin.math.abs(localHitPoint.y - box.max.y) < 0.001f -> Vector3(0f, 1f, 0f)
            kotlin.math.abs(localHitPoint.z - box.min.z) < 0.001f -> Vector3(0f, 0f, -1f)
            kotlin.math.abs(localHitPoint.z - box.max.z) < 0.001f -> Vector3(0f, 0f, 1f)
            else -> Vector3.UNIT_Y
        }

        return RayHit(worldHitPoint, transform.transformDirection(normal).normalize(), t)
    }

    private data class RayHit(
        val point: Vector3,
        val normal: Vector3,
        val distance: Float
    )
}