/**
 * Bullet Physics World Implementation
 * Manages the simulation environment and all physics objects
 */
package io.materia.physics.bullet.world

import io.materia.core.math.*
import io.materia.physics.*
import io.materia.physics.bullet.body.BulletRigidBody
import io.materia.physics.bullet.constraints.BulletConstraint

/**
 * Bullet-based implementation of PhysicsWorld
 */
class BulletPhysicsWorld(
    initialGravity: Vector3 = Vector3(0f, -9.81f, 0f)
) : PhysicsWorld {

    private val rigidBodies = mutableMapOf<String, BulletRigidBody>()
    private val constraints = mutableMapOf<String, BulletConstraint>()
    private val collisionObjects = mutableMapOf<String, CollisionObject>()
    private var collisionCallback: CollisionCallback? = null

    override var gravity: Vector3 = initialGravity
    override var timeStep = 1f / 60f
    override var maxSubSteps = 10
    override var solverIterations = 10
    override var broadphase = BroadphaseType.DBVT

    override fun addRigidBody(body: RigidBody): PhysicsOperationResult<Unit> {
        return try {
            val bulletBody = body as? BulletRigidBody
                ?: return PhysicsOperationResult.Error(
                    PhysicsException.InvalidOperation("Body must be created through BulletPhysicsEngine")
                )
            rigidBodies[body.id] = bulletBody
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add rigid body"
                )
            )
        }
    }

    override fun removeRigidBody(body: RigidBody): PhysicsOperationResult<Unit> {
        return try {
            rigidBodies.remove(body.id)
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

    override fun addConstraint(constraint: PhysicsConstraint): PhysicsOperationResult<Unit> {
        return try {
            val bulletConstraint = constraint as? BulletConstraint
                ?: return PhysicsOperationResult.Error(
                    PhysicsException.InvalidOperation("Constraint must be created through BulletPhysicsEngine")
                )
            constraints[constraint.id] = bulletConstraint
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add constraint"
                )
            )
        }
    }

    override fun removeConstraint(constraint: PhysicsConstraint): PhysicsOperationResult<Unit> {
        return try {
            constraints.remove(constraint.id)
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

    override fun addCollisionObject(obj: CollisionObject): PhysicsOperationResult<Unit> {
        return try {
            collisionObjects[obj.id] = obj
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.SimulationError(
                    e.message ?: "Failed to add collision object"
                )
            )
        }
    }

    override fun removeCollisionObject(obj: CollisionObject): PhysicsOperationResult<Unit> {
        return try {
            collisionObjects.remove(obj.id)
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

    override fun step(deltaTime: Float): PhysicsOperationResult<Unit> {
        return try {
            // Simulate physics step
            rigidBodies.values.forEach { body ->
                body.updateFromSimulation()
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Simulation step failed: ${e.message}"))
        }
    }

    override fun pause() {}
    override fun resume() {}

    override fun reset() {
        rigidBodies.clear()
        constraints.clear()
        collisionObjects.clear()
    }

    override fun raycast(from: Vector3, to: Vector3, groups: Int): RaycastResult? {
        // Simplified raycast implementation
        return null
    }

    override fun sphereCast(center: Vector3, radius: Float, groups: Int): List<CollisionObject> {
        return emptyList()
    }

    override fun boxCast(
        center: Vector3,
        halfExtents: Vector3,
        rotation: Quaternion,
        groups: Int
    ): List<CollisionObject> {
        return emptyList()
    }

    override fun overlaps(
        shape: CollisionShape,
        transform: Matrix4,
        groups: Int
    ): List<CollisionObject> {
        return emptyList()
    }

    fun dispose() {
        reset()
    }
}
