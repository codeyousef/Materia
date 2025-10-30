/**
 * Rapier RigidBody Implementation
 * Provides rigid body physics simulation using Rapier's WASM bindings
 */
package io.materia.physics.rapier.body

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Rapier-based implementation of RigidBody
 */
class RapierRigidBody(
    override val id: String,
    internal val rapierBody: RAPIER.RigidBody,
    private val rapierCollider: RAPIER.Collider,
    initialShape: CollisionShape,
    private val world: RAPIER.World
) : RigidBody {

    override var transform: Matrix4 = Matrix4.identity()
        set(value) {
            field = value
            val position = Vector3(value.m03, value.m13, value.m23)
            val rotation = value.extractRotation()
            rapierBody.setTranslation(toRapierVector3(position), true)
            rapierBody.setRotation(toRapierQuaternion(rotation), true)
        }

    override var collisionShape: CollisionShape = initialShape
    override var collisionGroups: Int = -1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.01f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false
        set(value) {
            field = value
            rapierCollider.setSensor(value)
        }

    override var mass: Float = 1f
        set(value) {
            field = value
            rapierCollider.setMass(value)
        }

    override var density: Float = 1f
        set(value) {
            field = value
            rapierCollider.setDensity(value)
        }

    override var restitution: Float = 0f
        set(value) {
            field = value
            rapierCollider.setRestitution(value)
        }

    override var friction: Float = 0.5f
        set(value) {
            field = value
            rapierCollider.setFriction(value)
        }

    override var rollingFriction: Float = 0f

    override var linearDamping: Float = 0f
        set(value) {
            field = value
            // Rapier sets damping on body creation, need to recreate
        }

    override var angularDamping: Float = 0f
        set(value) {
            field = value
            // Rapier sets damping on body creation, need to recreate
        }

    override var linearVelocity: Vector3
        get() = fromRapierVector3(rapierBody.linvel())
        set(value) {
            rapierBody.setLinvel(toRapierVector3(value), true)
        }

    override var angularVelocity: Vector3
        get() = fromRapierVector3(rapierBody.angvel())
        set(value) {
            rapierBody.setAngvel(toRapierVector3(value), true)
        }

    override var linearFactor: Vector3 = Vector3.ONE
    override var angularFactor: Vector3 = Vector3.ONE

    override var bodyType: RigidBodyType = RigidBodyType.DYNAMIC
        set(value) {
            field = value
            val rapierType = when (value) {
                RigidBodyType.DYNAMIC -> RAPIER.RigidBodyType.Dynamic
                RigidBodyType.STATIC -> RAPIER.RigidBodyType.Fixed
                RigidBodyType.KINEMATIC -> RAPIER.RigidBodyType.KinematicPositionBased
            }
            rapierBody.setBodyType(rapierType, true)
        }

    override var activationState: ActivationState = ActivationState.ACTIVE
    override var sleepThreshold: Float = 0.1f
    override var ccdMotionThreshold: Float = 0f
    override var ccdSweptSphereRadius: Float = 0f

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        return try {
            collisionShape = shape
            // In Rapier, we need to recreate the collider with new shape
            // This is a limitation of the current implementation
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to set collision shape"))
        }
    }

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        val currentPos = fromRapierVector3(rapierBody.translation())
        val newPos = currentPos.add(offset)
        rapierBody.setTranslation(toRapierVector3(newPos), true)
        updateTransformFromRapier()
    }

    override fun rotate(rotation: Quaternion) {
        val currentRot = fromRapierQuaternion(rapierBody.rotation())
        val newRot = currentRot.multiply(rotation)
        rapierBody.setRotation(toRapierQuaternion(newRot), true)
        updateTransformFromRapier()
    }

    override fun applyForce(force: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        return try {
            if (relativePosition == Vector3.ZERO) {
                rapierBody.addForce(toRapierVector3(force), true)
            } else {
                val worldPos = transform.transformPoint(relativePosition)
                rapierBody.addForceAtPoint(toRapierVector3(force), toRapierVector3(worldPos), true)
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply force"))
        }
    }

    override fun applyImpulse(impulse: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        return try {
            if (relativePosition == Vector3.ZERO) {
                rapierBody.applyImpulse(toRapierVector3(impulse), true)
            } else {
                val worldPos = transform.transformPoint(relativePosition)
                rapierBody.applyImpulseAtPoint(
                    toRapierVector3(impulse),
                    toRapierVector3(worldPos),
                    true
                )
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply impulse"))
        }
    }

    override fun applyTorque(torque: Vector3): PhysicsResult<Unit> {
        return try {
            rapierBody.addTorque(toRapierVector3(torque), true)
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply torque"))
        }
    }

    override fun applyTorqueImpulse(torque: Vector3): PhysicsResult<Unit> {
        return try {
            rapierBody.applyTorqueImpulse(toRapierVector3(torque), true)
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply torque impulse"))
        }
    }

    override fun applyCentralForce(force: Vector3): PhysicsResult<Unit> {
        return applyForce(force, Vector3.ZERO)
    }

    override fun applyCentralImpulse(impulse: Vector3): PhysicsResult<Unit> {
        return applyImpulse(impulse, Vector3.ZERO)
    }

    override fun isActive(): Boolean = !rapierBody.isSleeping()
    override fun isKinematic(): Boolean = rapierBody.isKinematic()
    override fun isStatic(): Boolean = rapierBody.isFixed()

    override fun getInertia(): Matrix3 {
        // Rapier doesn't expose inertia tensor directly
        // We approximate based on mass and shape
        val m = rapierBody.mass()
        return collisionShape.calculateInertia(m)
    }

    override fun getInverseInertia(): Matrix3 = getInertia().inverse()

    override fun getTotalForce(): Vector3 {
        // Rapier doesn't expose accumulated forces
        return Vector3.ZERO
    }

    override fun getTotalTorque(): Vector3 {
        // Rapier doesn't expose accumulated torques
        return Vector3.ZERO
    }

    override fun setTransform(position: Vector3, rotation: Quaternion) {
        rapierBody.setTranslation(toRapierVector3(position), true)
        rapierBody.setRotation(toRapierQuaternion(rotation), true)
        updateTransformFromRapier()
    }

    override fun getCenterOfMassTransform(): Matrix4 {
        val com = fromRapierVector3(rapierBody.centerOfMass())
        return Matrix4.translation(com.x, com.y, com.z)
    }

    internal fun updateFromRapier() {
        updateTransformFromRapier()
    }

    private fun updateTransformFromRapier() {
        val position = fromRapierVector3(rapierBody.translation())
        val rotation = fromRapierQuaternion(rapierBody.rotation())

        // Create rotation matrix and set translation
        transform = Matrix4().makeRotationFromQuaternion(rotation)
        transform.elements[12] = position.x
        transform.elements[13] = position.y
        transform.elements[14] = position.z
    }
}

// Type conversion helper functions
private fun toRapierVector3(v: Vector3): RAPIER.Vector3 {
    return RAPIER.Vector3(v.x, v.y, v.z)
}

private fun fromRapierVector3(v: dynamic): Vector3 {
    return Vector3(v.x as Float, v.y as Float, v.z as Float)
}

private fun toRapierQuaternion(q: Quaternion): RAPIER.Quaternion {
    return RAPIER.Quaternion(q.x, q.y, q.z, q.w)
}

private fun fromRapierQuaternion(q: dynamic): Quaternion {
    return Quaternion(q.x as Float, q.y as Float, q.z as Float, q.w as Float)
}

// Extension function for Matrix4
private fun Matrix4.extractRotation(): Quaternion {
    // Simplified quaternion extraction from rotation matrix
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
