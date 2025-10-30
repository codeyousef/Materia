/**
 * Rapier Physics World Implementation
 * Manages physics simulation, collision detection, and world stepping
 */
package io.materia.physics.rapier.world

import io.materia.core.math.*
import io.materia.physics.*
import io.materia.physics.rapier.body.RapierRigidBody
import io.materia.physics.rapier.constraints.RapierConstraint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/**
 * Rapier-based implementation of PhysicsWorld
 */
class RapierPhysicsWorld(
    initialGravity: Vector3 = Vector3(0f, -9.81f, 0f)
) : PhysicsWorld {
    private lateinit var world: RAPIER.World
    private val rigidBodies = mutableMapOf<String, RapierRigidBody>()
    private val colliders = mutableMapOf<String, RAPIER.Collider>()
    private val colliderUserData = mutableMapOf<Int, Any>()
    private val constraints = mutableMapOf<String, RapierConstraint>()
    private var collisionCallback: CollisionCallback? = null
    private val eventQueue = RAPIER.EventQueue()

    // Event callbacks
    private val collisionCallbacks = mutableListOf<(CollisionContact) -> Unit>()

    private var initialized = false

    /**
     * Add collision callback
     */
    fun onCollision(callback: (CollisionContact) -> Unit) {
        collisionCallbacks.add(callback)
    }

    override var gravity: Vector3 = initialGravity
        set(value) {
            field = value
            if (initialized) {
                world.gravity = toRapierVector3(value)
            }
        }

    override var timeStep = 1f / 60f
    override var maxSubSteps = 1
    override var solverIterations = 4
    override var broadphase = BroadphaseType.SAP

    init {
        GlobalScope.launch {
            initializeRapier()
        }
    }

    private suspend fun initializeRapier() {
        RAPIER.init().await()

        world = RAPIER.World(toRapierVector3(gravity))
        world.timestep = timeStep
        world.maxVelocityIterations = solverIterations
        world.maxPositionIterations = solverIterations * 2
        world.maxCcdSubsteps = maxSubSteps

        initialized = true
    }

    private fun ensureInitialized() {
        if (!initialized) {
            console.warn("RapierPhysicsEngine not yet initialized")
        }
    }

    override fun addRigidBody(body: RigidBody): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            val rapierBody = body as? RapierRigidBody
                ?: return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Body must be created through RapierPhysicsEngine"))

            rigidBodies[body.id] = rapierBody
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add rigid body"
                )
            )
        }
    }

    override fun removeRigidBody(body: RigidBody): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            val rapierBody = rigidBodies.remove(body.id) as? RapierRigidBody
            rapierBody?.let {
                world.removeRigidBody(it.rapierBody)
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to remove rigid body"
                )
            )
        }
    }

    override fun getRigidBodies(): List<RigidBody> = rigidBodies.values.toList()

    override fun getRigidBody(id: String): RigidBody? = rigidBodies[id]

    override fun addConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            val rapierConstraint = constraint as? RapierConstraint
                ?: return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Constraint must be created through RapierPhysicsEngine"))

            constraints[constraint.id] = rapierConstraint
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add constraint"
                )
            )
        }
    }

    override fun removeConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            val rapierConstraint = constraints.remove(constraint.id) as? RapierConstraint
            rapierConstraint?.let {
                world.removeImpulseJoint(it.joint, true)
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to remove constraint"
                )
            )
        }
    }

    override fun getConstraints(): List<PhysicsConstraint> = constraints.values.toList()

    override fun addCollisionObject(obj: CollisionObject): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            // For Rapier, collision objects without rigid bodies are static colliders
            val desc = createColliderDesc(obj.collisionShape)
            desc.setTranslation(obj.transform.m03, obj.transform.m13, obj.transform.m23)

            val collider = world.createCollider(desc, null)
            colliderUserData[collider.handle()] = obj
            collider.setCollisionGroups(obj.collisionGroups)
            colliders[obj.id] = collider

            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add collision object"
                )
            )
        }
    }

    override fun removeCollisionObject(obj: CollisionObject): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            val collider = colliders.remove(obj.id)
            collider?.let {
                world.removeCollider(it)
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to remove collision object"
                )
            )
        }
    }

    override fun setCollisionCallback(callback: CollisionCallback) {
        collisionCallback = callback
    }

    override fun step(deltaTime: Float): PhysicsResult<Unit> {
        ensureInitialized()

        return try {
            // Update timestep
            world.timestep = deltaTime

            // Step the simulation
            world.step()

            // Process collision events
            processCollisionEvents()

            // Update rigid body transforms
            world.forEachActiveRigidBody { rapierBody ->
                val body = rapierBody.userData() as? RapierRigidBody
                body?.updateFromRapier()
            }

            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Simulation step failed"
                )
            )
        }
    }

    private fun processCollisionEvents() {
        eventQueue.drainCollisionEvents { handle1, handle2, started ->
            // Find colliders by handle
            val collider1 = colliders.values.find { it.handle() == handle1 }
            val collider2 = colliders.values.find { it.handle() == handle2 }

            if (collider1 != null && collider2 != null) {
                val obj1 = collider1.userData() as? CollisionObject
                val obj2 = collider2.userData() as? CollisionObject

                if (obj1 != null && obj2 != null && obj1 is RigidBody && obj2 is RigidBody) {
                    val contact = CollisionContact(
                        bodyA = obj1,
                        bodyB = obj2,
                        point = extractContactNormal(collider1, collider2),
                        normal = extractContactNormal(collider1, collider2),
                        impulse = 0f
                    )

                    when {
                        started -> collisionCallbacks.forEach { callback -> callback(contact) }
                    }
                }
            }
        }
    }

    private fun extractContactPoints(c1: RAPIER.Collider, c2: RAPIER.Collider): List<Vector3> {
        val manifold = world.contactPair(c1, c2) ?: return emptyList()
        val points = mutableListOf<Vector3>()

        for (i in 0 until manifold.numContacts()) {
            val localPoint = manifold.contactLocalPoint1(i)
            points.add(fromRapierVector3(localPoint))
        }

        return points
    }

    private fun extractContactNormal(c1: RAPIER.Collider, c2: RAPIER.Collider): Vector3 {
        val manifold = world.contactPair(c1, c2) ?: return Vector3.ZERO
        return fromRapierVector3(manifold.normal())
    }

    override fun pause() {}

    override fun resume() {}

    override fun reset() {
        ensureInitialized()

        rigidBodies.clear()
        constraints.clear()
        colliders.clear()

        world = RAPIER.World(toRapierVector3(gravity))
        world.timestep = timeStep
        world.maxVelocityIterations = solverIterations
        world.maxPositionIterations = solverIterations * 2
    }

    override fun raycast(from: Vector3, to: Vector3, groups: Int): RaycastResult? {
        ensureInitialized()

        val direction = to.subtract(from).normalize()
        val maxDistance = from.distanceTo(to)

        val ray = RAPIER.Ray(
            toRapierVector3(from),
            toRapierVector3(direction)
        )

        val hit = world.castRay(ray, maxDistance, true, groups)

        return hit?.let { result ->
            val collider = result.collider as RAPIER.Collider
            val hitPoint = from + (direction * (result.toi as Float))
            val normal = fromRapierVector3(result.normal)

            object : RaycastResult {
                override val hasHit = true
                override val hitObject = collider.userData() as? CollisionObject
                override val hitPoint = hitPoint
                override val hitNormal = normal
                override val hitFraction = result.toi as Float
                override val distance = result.toi as Float
            }
        }
    }

    override fun sphereCast(center: Vector3, radius: Float, groups: Int): List<CollisionObject> {
        ensureInitialized()

        val results = mutableListOf<CollisionObject>()
        val sphereShape = RAPIER.ColliderDesc.ball(radius)

        world.intersectionsWithShape(
            toRapierVector3(center),
            RAPIER.Quaternion(0f, 0f, 0f, 1f),
            sphereShape,
            { collider ->
                val obj = collider.userData() as? CollisionObject
                obj?.let { results.add(it) }
                true
            },
            groups
        )

        return results
    }

    override fun boxCast(
        center: Vector3,
        halfExtents: Vector3,
        rotation: Quaternion,
        groups: Int
    ): List<CollisionObject> {
        ensureInitialized()

        val results = mutableListOf<CollisionObject>()
        val boxShape = RAPIER.ColliderDesc.cuboid(halfExtents.x, halfExtents.y, halfExtents.z)

        world.intersectionsWithShape(
            toRapierVector3(center),
            toRapierQuaternion(rotation),
            boxShape,
            { collider ->
                val obj = collider.userData() as? CollisionObject
                obj?.let { results.add(it) }
                true
            },
            groups
        )

        return results
    }

    override fun overlaps(
        shape: CollisionShape,
        transform: Matrix4,
        groups: Int
    ): List<CollisionObject> {
        ensureInitialized()

        val results = mutableListOf<CollisionObject>()
        val position = Vector3(transform.m03, transform.m13, transform.m23)
        val rotation = transform.extractRotation()

        val rapierShape = createColliderDesc(shape)

        world.intersectionsWithShape(
            toRapierVector3(position),
            toRapierQuaternion(rotation),
            rapierShape,
            { collider ->
                val obj = collider.userData() as? CollisionObject
                obj?.let { results.add(it) }
                true
            },
            groups
        )

        return results
    }

    private fun createColliderDesc(shape: CollisionShape): RAPIER.ColliderDesc {
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
                ?: RAPIER.ColliderDesc.ball(1f)

            is TriangleMeshShape -> RAPIER.ColliderDesc.trimesh(shape.vertices, shape.indices)
                ?: RAPIER.ColliderDesc.ball(1f)

            is HeightfieldShape -> RAPIER.ColliderDesc.heightfield(
                shape.height,
                shape.width,
                shape.heightData,
                RAPIER.Vector3(1f, 1f, 1f)
            )

            else -> RAPIER.ColliderDesc.ball(1f)
        }
    }
}

// Type conversion helper functions
private fun toRapierVector3(v: Vector3): RAPIER.Vector3 = RAPIER.Vector3(v.x, v.y, v.z)

private fun fromRapierVector3(v: dynamic): Vector3 =
    Vector3(v.x as Float, v.y as Float, v.z as Float)

private fun toRapierQuaternion(q: Quaternion): RAPIER.Quaternion =
    RAPIER.Quaternion(q.x, q.y, q.z, q.w)

private fun fromRapierQuaternion(q: dynamic): Quaternion =
    Quaternion(q.x as Float, q.y as Float, q.z as Float, q.w as Float)

// Extension function for Matrix4
private fun Matrix4.extractRotation(): Quaternion {
    val trace = m00 + m11 + m22

    return when {
        trace > 0 -> {
            val s = 0.5f / kotlin.math.sqrt(trace + 1f)
            Quaternion(
                (m21 - m12) * s,
                (m02 - m20) * s,
                (m10 - m01) * s,
                0.25f / s
            )
        }

        m00 > m11 && m00 > m22 -> {
            val s = 2f * kotlin.math.sqrt(1f + m00 - m11 - m22)
            Quaternion(
                0.25f * s,
                (m01 + m10) / s,
                (m02 + m20) / s,
                (m21 - m12) / s
            )
        }

        m11 > m22 -> {
            val s = 2f * kotlin.math.sqrt(1f + m11 - m00 - m22)
            Quaternion(
                (m01 + m10) / s,
                0.25f * s,
                (m12 + m21) / s,
                (m02 - m20) / s
            )
        }

        else -> {
            val s = 2f * kotlin.math.sqrt(1f + m22 - m00 - m11)
            Quaternion(
                (m02 + m20) / s,
                (m12 + m21) / s,
                0.25f * s,
                (m10 - m01) / s
            )
        }
    }
}
