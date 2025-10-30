/**
 * RigidBody and CollisionObject implementations for physics simulation
 * Provides foundation for rigid body dynamics and collision detection
 */
package io.materia.physics

import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Contact point data structure
 */
data class ContactPoint(
    val pointA: Vector3,
    val pointB: Vector3,
    val normal: Vector3,
    val distance: Float,
    val impulse: Float = 0f
)

/**
 * Contact manifold representing collision data
 */
data class ContactManifold(
    val bodyA: CollisionObject,
    val bodyB: CollisionObject,
    val points: List<ContactPoint>
)


// RigidBodyType and ActivationState are imported from PhysicsTypes.kt

/**
 * Physics result wrapper for error handling
 */

/**
 * Default rigid body implementation
 */
class DefaultRigidBody(
    override val id: String,
    initialShape: CollisionShape,
    initialMass: Float = 1.0f,
    initialTransform: Matrix4 = Matrix4.identity()
) : RigidBody {

    // Transform state
    private val _transform = MutableStateFlow(initialTransform)
    override var transform: Matrix4
        get() = _transform.value
        set(value) {
            _transform.value = value
        }

    val transformFlow: StateFlow<Matrix4> = _transform.asStateFlow()

    // Collision properties
    override var collisionShape: CollisionShape = initialShape
    override var collisionGroups: Int = 1
    override var collisionMask: Int = -1 // Collide with everything by default
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 1e30f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    // Mass properties
    private val _mass = MutableStateFlow(initialMass)
    override var mass: Float
        get() = _mass.value
        set(value) {
            require(value >= 0f) { "Mass cannot be negative" }
            _mass.value = value
            updateInertia()
        }

    override var density: Float = 1.0f
        set(value) {
            require(value > 0f) { "Density must be positive" }
            field = value
            // Auto-calculate mass from volume and density
            mass = collisionShape.getVolume() * value
        }

    override var restitution: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override var friction: Float = 0.5f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    override var rollingFriction: Float = 0.0f
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    // Motion properties
    private val _linearVelocity = MutableStateFlow(Vector3.ZERO)
    override var linearVelocity: Vector3
        get() = _linearVelocity.value
        set(value) {
            _linearVelocity.value = value
        }

    private val _angularVelocity = MutableStateFlow(Vector3.ZERO)
    override var angularVelocity: Vector3
        get() = _angularVelocity.value
        set(value) {
            _angularVelocity.value = value
        }

    override var linearDamping: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override var angularDamping: Float = 0.0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    override var linearFactor: Vector3 = Vector3.ONE
    override var angularFactor: Vector3 = Vector3.ONE

    // Body type and activation
    override var bodyType: RigidBodyType = RigidBodyType.DYNAMIC
        set(value) {
            field = value
            when (value) {
                RigidBodyType.STATIC -> {
                    mass = 0f
                    linearVelocity = Vector3.ZERO
                    angularVelocity = Vector3.ZERO
                }

                RigidBodyType.KINEMATIC -> {
                    mass = 0f
                    activationState = ActivationState.DISABLE_DEACTIVATION
                }

                RigidBodyType.DYNAMIC -> {
                    if (mass <= 0f) mass = 1f
                }
            }
        }

    override var activationState: ActivationState = ActivationState.ACTIVE
    override var sleepThreshold: Float = 0.8f

    // Continuous collision detection
    override var ccdMotionThreshold: Float = 0f
    override var ccdSweptSphereRadius: Float = 0f

    // Internal state
    private var inverseMass: Float = if (initialMass > 0f) 1f / initialMass else 0f
    private var localInertia: Vector3 = Vector3.ZERO
    private var inverseInertia: Matrix3 = Matrix3.identity()
    private var totalForce: Vector3 = Vector3.ZERO
    private var totalTorque: Vector3 = Vector3.ZERO

    init {
        updateInertia()
    }

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        return try {
            collisionShape = shape
            updateInertia()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to set collision shape: ${e.message}"))
        }
    }


    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        transform = transform.translate(offset)
    }

    override fun rotate(rotation: Quaternion) {
        transform = transform.rotate(rotation)
    }

    override fun applyForce(force: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        return try {
            if (bodyType != RigidBodyType.DYNAMIC) {
                return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply force to non-dynamic body"))
            }

            totalForce = totalForce + (force * linearFactor)

            if (relativePosition != Vector3.ZERO) {
                val torque = relativePosition.cross(force) * angularFactor
                totalTorque = totalTorque + torque
            }

            activate()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply force: ${e.message}"))
        }
    }

    override fun applyImpulse(impulse: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        return try {
            if (bodyType != RigidBodyType.DYNAMIC) {
                return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply impulse to non-dynamic body"))
            }

            // Linear impulse
            val deltaV = (impulse * inverseMass) * linearFactor
            linearVelocity = linearVelocity + deltaV

            // Angular impulse
            if (relativePosition != Vector3.ZERO) {
                val angularImpulse = relativePosition.cross(impulse) * angularFactor
                val deltaW = inverseInertia * angularImpulse
                angularVelocity = angularVelocity + deltaW
            }

            activate()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply impulse: ${e.message}"))
        }
    }

    override fun applyTorque(torque: Vector3): PhysicsResult<Unit> {
        return try {
            if (bodyType != RigidBodyType.DYNAMIC) {
                return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply torque to non-dynamic body"))
            }

            totalTorque = totalTorque + (torque * angularFactor)
            activate()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply torque: ${e.message}"))
        }
    }

    override fun applyTorqueImpulse(torque: Vector3): PhysicsResult<Unit> {
        return try {
            if (bodyType != RigidBodyType.DYNAMIC) {
                return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply torque impulse to non-dynamic body"))
            }

            val deltaW = inverseInertia * (torque * angularFactor)
            angularVelocity = angularVelocity + deltaW
            activate()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.SimulationError("Failed to apply torque impulse: ${e.message}"))
        }
    }

    override fun applyCentralForce(force: Vector3): PhysicsResult<Unit> {
        return applyForce(force, Vector3.ZERO)
    }

    override fun applyCentralImpulse(impulse: Vector3): PhysicsResult<Unit> {
        return applyImpulse(impulse, Vector3.ZERO)
    }

    override fun isActive(): Boolean {
        return activationState == ActivationState.ACTIVE
    }

    override fun isKinematic(): Boolean {
        return bodyType == RigidBodyType.KINEMATIC
    }

    override fun isStatic(): Boolean {
        return bodyType == RigidBodyType.STATIC
    }

    override fun getInertia(): Matrix3 {
        return if (mass > 0f) {
            Matrix3.diagonal(localInertia)
        } else {
            Matrix3.zero()
        }
    }

    override fun getInverseInertia(): Matrix3 {
        return inverseInertia
    }

    override fun getTotalForce(): Vector3 = totalForce

    override fun getTotalTorque(): Vector3 = totalTorque

    override fun setTransform(position: Vector3, rotation: Quaternion) {
        transform = Matrix4.fromTranslationRotation(position, rotation)
    }

    override fun getCenterOfMassTransform(): Matrix4 = transform

    /**
     * Activate the rigid body (wake it up)
     */
    private fun activate() {
        if (activationState == ActivationState.DEACTIVATED) {
            activationState = ActivationState.ACTIVE
        }
    }

    /**
     * Update inertia tensor based on current mass and shape
     */
    private fun updateInertia() {
        inverseMass = if (mass > 0f) 1f / mass else 0f

        if (mass > 0f && bodyType == RigidBodyType.DYNAMIC) {
            localInertia = collisionShape.calculateLocalInertia(mass)

            // Calculate inverse inertia tensor
            val invIx = if (localInertia.x > 0f) 1f / localInertia.x else 0f
            val invIy = if (localInertia.y > 0f) 1f / localInertia.y else 0f
            val invIz = if (localInertia.z > 0f) 1f / localInertia.z else 0f

            inverseInertia = Matrix3.diagonal(Vector3(invIx, invIy, invIz))
        } else {
            localInertia = Vector3.ZERO
            inverseInertia = Matrix3.zero()
        }
    }

    /**
     * Integrate motion for one time step
     */
    fun integrateVelocities(deltaTime: Float) {
        if (bodyType != RigidBodyType.DYNAMIC || !isActive()) return

        // Apply accumulated forces
        if (inverseMass > 0f) {
            val acceleration = totalForce * inverseMass
            linearVelocity = linearVelocity + (acceleration * deltaTime)
        }

        // Apply accumulated torques
        val angularAcceleration = inverseInertia * totalTorque
        angularVelocity = angularVelocity + (angularAcceleration * deltaTime)

        // Apply damping
        linearVelocity = linearVelocity * max(0f, 1f - linearDamping * deltaTime)
        angularVelocity = angularVelocity * max(0f, 1f - angularDamping * deltaTime)

        // Clear accumulated forces
        totalForce = Vector3.ZERO
        totalTorque = Vector3.ZERO
    }

    /**
     * Integrate positions for one time step
     */
    fun integratePositions(deltaTime: Float) {
        if (bodyType == RigidBodyType.STATIC || !isActive()) return

        val translation = transform.getTranslation()
        val rotation = transform.getRotation()

        // Integrate linear motion
        if (bodyType == RigidBodyType.DYNAMIC) {
            val newTranslation = translation + (linearVelocity * deltaTime)

            // Integrate angular motion
            val angularVelocityLength = angularVelocity.length()
            val newRotation = if (angularVelocityLength > 0.001f) {
                val axis = angularVelocity.clone().normalize()
                val angle = angularVelocityLength * deltaTime
                val deltaRotation = Quaternion.fromAxisAngle(axis, angle)
                rotation.multiply(deltaRotation).normalize()
            } else {
                rotation
            }

            transform = Matrix4.fromTranslationRotation(newTranslation, newRotation)
        }

        // Check for sleep conditions
        checkSleepConditions()
    }

    /**
     * Check if body should go to sleep
     */
    private fun checkSleepConditions() {
        if (bodyType != RigidBodyType.DYNAMIC ||
            activationState == ActivationState.DISABLE_DEACTIVATION
        ) return

        val velocityThreshold = sleepThreshold
        val angularThreshold = sleepThreshold * 0.1f

        if (linearVelocity.lengthSquared() < velocityThreshold * velocityThreshold &&
            angularVelocity.lengthSquared() < angularThreshold * angularThreshold
        ) {

            if (activationState == ActivationState.ACTIVE) {
                activationState = ActivationState.WANTS_DEACTIVATION
            } else if (activationState == ActivationState.WANTS_DEACTIVATION) {
                activationState = ActivationState.DEACTIVATED
                linearVelocity = Vector3.ZERO
                angularVelocity = Vector3.ZERO
            }
        } else {
            activationState = ActivationState.ACTIVE
        }
    }
}

/**
 * Simple collision object implementation
 */
class DefaultCollisionObject(
    override val id: String,
    initialShape: CollisionShape,
    initialTransform: Matrix4 = Matrix4.identity()
) : CollisionObject {

    override var transform: Matrix4 = initialTransform
    override var collisionShape: CollisionShape = initialShape
    override var collisionGroups: Int = 1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 1e30f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    override fun setCollisionShape(shape: CollisionShape): PhysicsOperationResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }


    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        transform = transform.translate(offset)
    }

    override fun rotate(rotation: Quaternion) {
        transform = transform.rotate(rotation)
    }
}

/**
 * Factory functions for creating physics objects
 */
object RigidBodyFactory {

    fun createStaticBody(
        id: String,
        shape: CollisionShape,
        transform: Matrix4 = Matrix4.identity()
    ): RigidBody {
        return DefaultRigidBody(id, shape, 0f, transform).apply {
            bodyType = RigidBodyType.STATIC
        }
    }

    fun createKinematicBody(
        id: String,
        shape: CollisionShape,
        transform: Matrix4 = Matrix4.identity()
    ): RigidBody {
        return DefaultRigidBody(id, shape, 0f, transform).apply {
            bodyType = RigidBodyType.KINEMATIC
        }
    }

    fun createDynamicBody(
        id: String,
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4 = Matrix4.identity()
    ): RigidBody {
        require(mass > 0f) { "Dynamic bodies must have positive mass" }
        return DefaultRigidBody(id, shape, mass, transform).apply {
            bodyType = RigidBodyType.DYNAMIC
        }
    }

    fun createTrigger(
        id: String,
        shape: CollisionShape,
        transform: Matrix4 = Matrix4.identity()
    ): CollisionObject {
        return DefaultCollisionObject(id, shape, transform).apply {
            isTrigger = true
        }
    }
}