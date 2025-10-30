/**
 * Physics types and data structures for the physics system
 * Contains enums, data classes, and interfaces referenced by physics implementations
 */
package io.materia.physics

import io.materia.core.math.Box3
import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import kotlin.math.PI

/**
 * Enums for physics system
 */
enum class RigidBodyType {
    DYNAMIC, KINEMATIC, STATIC
}

enum class ActivationState {
    ACTIVE, DEACTIVATED, WANTS_DEACTIVATION, DISABLE_DEACTIVATION, DISABLE_SIMULATION
}

enum class ShapeType {
    BOX, SPHERE, CAPSULE, CYLINDER, CONE,
    CONVEX_HULL, TRIANGLE_MESH, HEIGHTFIELD,
    COMPOUND, PLANE, EMPTY
}

enum class BroadphaseType {
    SIMPLE, AXIS_SWEEP_3, DBVT, SAP,
    DYNAMIC_AABB_TREE,
    SORT_AND_SWEEP,
    HASH_GRID,
    SPATIAL_HASH
}

enum class ConstraintParam {
    ERP, STOP_ERP, CFM, STOP_CFM,
    LINEAR_LOWER_LIMIT, LINEAR_UPPER_LIMIT,
    ANGULAR_LOWER_LIMIT, ANGULAR_UPPER_LIMIT,
    TARGET_VELOCITY, MAX_MOTOR_FORCE
}

enum class CombineMode {
    AVERAGE, MINIMUM, MAXIMUM, MULTIPLY
}

/**
 * Data structures for physics system
 */
data class ChildShape(
    val transform: Matrix4,
    val shape: CollisionShape
)

data class Triangle(
    val vertex0: Vector3,
    val vertex1: Vector3,
    val vertex2: Vector3
)

data class MeshBVH(
    val nodes: List<BVHNode>,
    val triangles: List<Triangle>
)

data class BVHNode(
    val bounds: Box3,
    val leftChild: Int,
    val rightChild: Int,
    val triangleOffset: Int,
    val triangleCount: Int
)

data class ConstraintInfo(
    var m_numIterations: Int,
    var m_tau: Float,
    var m_damping: Float,
    var m_impulseClamp: Float
)

/**
 * Callback interfaces
 */
interface TriangleCallback {
    fun processTriangle(triangle: Triangle, partId: Int, triangleIndex: Int)
}

interface CollisionCallback {
    fun onContactAdded(contact: ContactInfo): Boolean
    fun onContactProcessed(contact: ContactInfo): Boolean
    fun onContactDestroyed(contact: ContactInfo)
}

interface ContactInfo {
    val objectA: CollisionObject
    val objectB: CollisionObject
    val worldPosA: Vector3
    val worldPosB: Vector3
    val normalWorldOnB: Vector3
    val distance: Float
    val impulse: Float
    val friction: Float
    val restitution: Float
}

interface RaycastResult {
    val hasHit: Boolean
    val hitObject: CollisionObject?
    val hitPoint: Vector3
    val hitNormal: Vector3
    val hitFraction: Float
    val distance: Float
}

/**
 * Collision contact information
 */
data class CollisionContact(
    val bodyA: RigidBody,
    val bodyB: RigidBody,
    val point: Vector3,
    val normal: Vector3,
    val distance: Float = 0f,
    val impulse: Float = 0f
)

/**
 * Physics material data class
 */
data class PhysicsMaterial(
    val friction: Float = 0.5f,
    val restitution: Float = 0.0f,
    val rollingFriction: Float = 0.0f,
    val spinningFriction: Float = 0.0f,
    val frictionCombineMode: CombineMode = CombineMode.AVERAGE,
    val restitutionCombineMode: CombineMode = CombineMode.AVERAGE
)

/**
 * Result types for physics operations
 */
sealed class PhysicsOperationResult<T> {
    data class Success<T>(val value: T) : PhysicsOperationResult<T>()
    data class Error<T>(val exception: PhysicsException) : PhysicsOperationResult<T>()
}

// Typealias for backward compatibility
typealias PhysicsResult<T> = PhysicsOperationResult<T>

/**
 * Exception types for physics system
 */
sealed class PhysicsException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class WorldCreationFailed(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)

    class EngineError(message: String, cause: Throwable? = null) : PhysicsException(message, cause)
    class BodyCreationFailed(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)

    class ShapeCreationFailed(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)

    class ConstraintCreationFailed(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)

    class SimulationFailed(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)

    class InvalidParameters(message: String) : PhysicsException(message)
    class UnsupportedOperation(operation: String) :
        PhysicsException("Unsupported operation: $operation")

    class EngineNotInitialized() : PhysicsException("Physics engine not initialized")
    class InvalidOperation(message: String) : PhysicsException(message)
    class SimulationError(message: String, cause: Throwable? = null) :
        PhysicsException(message, cause)
}


/**
 * Physics utility functions
 */
object PhysicsUtils {

    /**
     * Calculate moment of inertia for common shapes
     */
    fun calculateBoxInertia(mass: Float, dimensions: Vector3): Matrix3 {
        val factor = mass / 12f
        return Matrix3(
            floatArrayOf(
                factor * (dimensions.y * dimensions.y + dimensions.z * dimensions.z), 0f, 0f,
                0f, factor * (dimensions.x * dimensions.x + dimensions.z * dimensions.z), 0f,
                0f, 0f, factor * (dimensions.x * dimensions.x + dimensions.y * dimensions.y)
            )
        )
    }

    fun calculateSphereInertia(mass: Float, radius: Float): Matrix3 {
        val inertia = 0.4f * mass * radius * radius
        return Matrix3(
            floatArrayOf(
                inertia, 0f, 0f,
                0f, inertia, 0f,
                0f, 0f, inertia
            )
        )
    }

    fun calculateCylinderInertia(mass: Float, radius: Float, height: Float): Matrix3 {
        val radiusSquared = radius * radius
        val heightSquared = height * height

        return Matrix3(
            floatArrayOf(
                mass * (radiusSquared * 0.25f + heightSquared / 12f), 0f, 0f,
                0f, mass * radiusSquared * 0.5f, 0f,
                0f, 0f, mass * (radiusSquared * 0.25f + heightSquared / 12f)
            )
        )
    }

    /**
     * Clamp angle to [-PI, PI] range
     */
    fun normalizeAngle(angle: Float): Float {
        var result = angle
        while (result > PI) result = result - 2f * PI.toFloat()
        while (result < -PI) result = result + 2f * PI.toFloat()
        return result
    }

    /**
     * Convert degrees to radians
     */
    fun toRadians(degrees: Float): Float = degrees * PI.toFloat() / 180f

    /**
     * Convert radians to degrees
     */
    fun toDegrees(radians: Float): Float = radians * 180f / PI.toFloat()
}