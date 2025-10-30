/**
 * Physics interfaces for the Materia physics system
 * Defines all the core interfaces for physics simulation
 */
package io.materia.physics

import io.materia.core.math.*

/**
 * Physics world management interface
 * Compatible with modern physics engines (Rapier, Bullet)
 */
interface PhysicsWorld {
    /**
     * World properties
     */
    var gravity: Vector3
    var timeStep: Float
    var maxSubSteps: Int
    var solverIterations: Int
    var broadphase: BroadphaseType

    /**
     * Rigid body management
     */
    fun addRigidBody(body: RigidBody): PhysicsResult<Unit>
    fun removeRigidBody(body: RigidBody): PhysicsResult<Unit>
    fun getRigidBodies(): List<RigidBody>
    fun getRigidBody(id: String): RigidBody?

    /**
     * Constraint management
     */
    fun addConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit>
    fun removeConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit>
    fun getConstraints(): List<PhysicsConstraint>

    /**
     * Collision detection
     */
    fun addCollisionObject(obj: CollisionObject): PhysicsResult<Unit>
    fun removeCollisionObject(obj: CollisionObject): PhysicsResult<Unit>
    fun setCollisionCallback(callback: CollisionCallback)

    /**
     * Simulation control
     */
    fun step(deltaTime: Float): PhysicsResult<Unit>
    fun pause()
    fun resume()
    fun reset()

    /**
     * Queries
     */
    fun raycast(from: Vector3, to: Vector3, groups: Int = -1): RaycastResult?
    fun sphereCast(center: Vector3, radius: Float, groups: Int = -1): List<CollisionObject>
    fun boxCast(
        center: Vector3,
        halfExtents: Vector3,
        rotation: Quaternion,
        groups: Int = -1
    ): List<CollisionObject>

    fun overlaps(shape: CollisionShape, transform: Matrix4, groups: Int = -1): List<CollisionObject>
}

/**
 * Collision object base interface
 */
interface CollisionObject {
    val id: String
    var transform: Matrix4
    var collisionShape: CollisionShape
    var collisionGroups: Int
    var collisionMask: Int
    var userData: Any?

    /**
     * Collision properties
     */
    var contactProcessingThreshold: Float
    var collisionFlags: Int
    var isTrigger: Boolean

    /**
     * Shape management
     */
    fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit>

    /**
     * Transform operations
     */
    fun setWorldTransform(transform: Matrix4)
    fun getWorldTransform(): Matrix4
    fun translate(offset: Vector3)
    fun rotate(rotation: Quaternion)
}

/**
 * Rigid body interface
 */
interface RigidBody : CollisionObject {
    /**
     * Physical properties
     */
    var mass: Float
    var density: Float
    var restitution: Float
    var friction: Float
    var rollingFriction: Float
    var linearDamping: Float
    var angularDamping: Float

    /**
     * Motion state
     */
    var linearVelocity: Vector3
    var angularVelocity: Vector3
    var linearFactor: Vector3
    var angularFactor: Vector3

    /**
     * Body type and state
     */
    var bodyType: RigidBodyType
    var activationState: ActivationState
    var sleepThreshold: Float
    var ccdMotionThreshold: Float
    var ccdSweptSphereRadius: Float

    /**
     * Force and impulse application
     */
    fun applyForce(force: Vector3, relativePosition: Vector3 = Vector3.ZERO): PhysicsResult<Unit>
    fun applyImpulse(
        impulse: Vector3,
        relativePosition: Vector3 = Vector3.ZERO
    ): PhysicsResult<Unit>

    fun applyTorque(torque: Vector3): PhysicsResult<Unit>
    fun applyTorqueImpulse(torque: Vector3): PhysicsResult<Unit>
    fun applyCentralForce(force: Vector3): PhysicsResult<Unit>
    fun applyCentralImpulse(impulse: Vector3): PhysicsResult<Unit>

    /**
     * State queries
     */
    fun isActive(): Boolean
    fun isKinematic(): Boolean
    fun isStatic(): Boolean
    fun getInertia(): Matrix3
    fun getInverseInertia(): Matrix3
    fun getTotalForce(): Vector3
    fun getTotalTorque(): Vector3

    /**
     * Transformation
     */
    fun setTransform(position: Vector3, rotation: Quaternion)
    override fun getWorldTransform(): Matrix4
    fun getCenterOfMassTransform(): Matrix4
}

/**
 * Collision shape interface
 */
interface CollisionShape {
    val shapeType: ShapeType
    val margin: Float
    val localScaling: Vector3
    val boundingBox: Box3

    /**
     * Shape properties
     */
    fun calculateInertia(mass: Float): Matrix3
    fun getVolume(): Float
    fun getSurfaceArea(): Float
    fun isConvex(): Boolean
    fun isCompound(): Boolean

    /**
     * Geometric queries
     */
    fun localGetSupportingVertex(direction: Vector3): Vector3
    fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3
    fun calculateLocalInertia(mass: Float): Vector3

    /**
     * Serialization
     */
    fun serialize(): ByteArray
    fun clone(): CollisionShape
}

/**
 * Specific collision shapes
 */
interface PrimitiveShape : CollisionShape

interface BoxShape : PrimitiveShape {
    val halfExtents: Vector3

    fun getHalfExtentsWithMargin(): Vector3
    fun getHalfExtentsWithoutMargin(): Vector3
}

interface SphereShape : PrimitiveShape {
    val radius: Float

    fun getRadiusWithMargin(): Float
    fun getRadiusWithoutMargin(): Float
}

interface CapsuleShape : PrimitiveShape {
    val radius: Float
    val height: Float
    val upAxis: Int

    fun getHalfHeight(): Float
}

interface CylinderShape : PrimitiveShape {
    val halfExtents: Vector3
    val upAxis: Int

    fun getRadius(): Float
    fun getHalfHeight(): Float
}

interface ConeShape : PrimitiveShape {
    val radius: Float
    val height: Float
    val upAxis: Int

    fun getConeRadius(): Float
    fun getConeHeight(): Float
}

interface ConvexHullShape : CollisionShape {
    val vertices: FloatArray
    val numVertices: Int

    fun addPoint(point: Vector3, recalculateLocalAABB: Boolean = true)
    fun getScaledPoint(index: Int): Vector3
    fun getUnscaledPoints(): List<Vector3>
    fun optimizeConvexHull()
}

interface TriangleMeshShape : CollisionShape {
    val vertices: FloatArray
    val indices: IntArray
    val triangleCount: Int

    fun getTriangle(index: Int): Triangle
    fun processAllTriangles(callback: TriangleCallback, aabbMin: Vector3, aabbMax: Vector3)
    fun buildBVH(): MeshBVH
}

interface HeightfieldShape : CollisionShape {
    val width: Int
    val height: Int
    val heightData: FloatArray
    val maxHeight: Float
    val minHeight: Float
    val upAxis: Int

    fun getHeightAtPoint(x: Float, z: Float): Float
    fun setHeightValue(x: Int, z: Int, height: Float)
}

interface CompoundShape : CollisionShape {
    val childShapes: List<ChildShape>

    fun addChildShape(transform: Matrix4, shape: CollisionShape): PhysicsResult<Unit>
    fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit>
    fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit>
    fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit>
    fun recalculateLocalAabb()
}

/**
 * Physics constraints interface
 */
interface PhysicsConstraint {
    val id: String
    val bodyA: RigidBody
    val bodyB: RigidBody?
    var enabled: Boolean
    var breakingThreshold: Float

    /**
     * Constraint parameters
     */
    fun setParam(param: ConstraintParam, value: Float, axis: Int = -1)
    fun getParam(param: ConstraintParam, axis: Int = -1): Float

    /**
     * Constraint state
     */
    fun getAppliedImpulse(): Float
    fun isEnabled(): Boolean
    fun getInfo(info: ConstraintInfo)
}

/**
 * Specific constraint types
 */
interface PointToPointConstraint : PhysicsConstraint {
    val pivotA: Vector3
    val pivotB: Vector3

    fun setPivotA(pivot: Vector3)
    fun setPivotB(pivot: Vector3)
    fun updateRHS(timeStep: Float)
}

interface HingeConstraint : PhysicsConstraint {
    val axisA: Vector3
    val axisB: Vector3
    val pivotA: Vector3
    val pivotB: Vector3

    /**
     * Limits
     */
    var lowerLimit: Float
    var upperLimit: Float
    var enableAngularMotor: Boolean
    var targetVelocity: Float
    var maxMotorImpulse: Float

    fun setLimit(
        low: Float,
        high: Float,
        softness: Float = 0.9f,
        biasFactor: Float = 0.3f,
        relaxationFactor: Float = 1f
    )

    fun enableMotor(enable: Boolean)
    fun setMotorTarget(targetAngle: Float, deltaTime: Float)
    fun getHingeAngle(): Float
}

interface SliderConstraint : PhysicsConstraint {
    val frameA: Matrix4
    val frameB: Matrix4

    /**
     * Linear limits
     */
    var lowerLinearLimit: Float
    var upperLinearLimit: Float
    var lowerAngularLimit: Float
    var upperAngularLimit: Float

    /**
     * Motors
     */
    var poweredLinearMotor: Boolean
    var targetLinearMotorVelocity: Float
    var maxLinearMotorForce: Float
    var poweredAngularMotor: Boolean
    var targetAngularMotorVelocity: Float
    var maxAngularMotorForce: Float

    fun getLinearPos(): Float
    fun getAngularPos(): Float
}

interface ConeTwistConstraint : PhysicsConstraint {
    val frameA: Matrix4
    val frameB: Matrix4

    var swingSpan1: Float
    var swingSpan2: Float
    var twistSpan: Float
    var damping: Float

    fun setLimit(
        swingSpan1: Float,
        swingSpan2: Float,
        twistSpan: Float,
        softness: Float = 1f,
        biasFactor: Float = 0.3f,
        relaxationFactor: Float = 1f
    )

    fun enableMotor(enable: Boolean)
    fun setMaxMotorImpulse(maxMotorImpulse: Float)
    fun setMotorTarget(q: Quaternion)
}

interface Generic6DofConstraint : PhysicsConstraint {
    val frameA: Matrix4
    val frameB: Matrix4

    /**
     * Linear limits (X, Y, Z)
     */
    fun setLinearLowerLimit(linearLower: Vector3)
    fun setLinearUpperLimit(linearUpper: Vector3)
    fun getLinearLowerLimit(): Vector3
    fun getLinearUpperLimit(): Vector3

    /**
     * Angular limits (X, Y, Z)
     */
    fun setAngularLowerLimit(angularLower: Vector3)
    fun setAngularUpperLimit(angularUpper: Vector3)
    fun getAngularLowerLimit(): Vector3
    fun getAngularUpperLimit(): Vector3

    /**
     * Motors
     */
    fun enableMotor(index: Int, enable: Boolean)
    fun setMotorTargetVelocity(index: Int, velocity: Float)
    fun setMotorMaxForce(index: Int, force: Float)
}

/**
 * Character controller interface
 */
interface CharacterController : CollisionObject {
    /**
     * Movement properties
     */
    var stepHeight: Float
    var maxSlope: Float
    var jumpSpeed: Float
    var fallSpeed: Float
    var walkDirection: Vector3
    var velocityForTimeInterval: Vector3

    /**
     * State queries
     */
    fun onGround(): Boolean
    fun canJump(): Boolean
    fun jump(direction: Vector3 = Vector3.UNIT_Y)

    /**
     * Movement
     */
    fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float)
    fun warp(origin: Vector3)
    fun preStep(world: PhysicsWorld)
    fun playerStep(world: PhysicsWorld, deltaTime: Float)
}

/**
 * Physics engine abstraction
 */
interface PhysicsEngine {
    val name: String
    val version: String

    /**
     * World management
     */
    fun createWorld(gravity: Vector3 = Vector3(0f, -9.81f, 0f)): PhysicsWorld
    fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit>

    /**
     * Shape creation
     */
    fun createBoxShape(halfExtents: Vector3): BoxShape
    fun createSphereShape(radius: Float): SphereShape
    fun createCapsuleShape(radius: Float, height: Float): CapsuleShape
    fun createCylinderShape(halfExtents: Vector3): CylinderShape
    fun createConeShape(radius: Float, height: Float): ConeShape
    fun createConvexHullShape(vertices: FloatArray): ConvexHullShape
    fun createTriangleMeshShape(vertices: FloatArray, indices: IntArray): TriangleMeshShape
    fun createHeightfieldShape(width: Int, height: Int, heightData: FloatArray): HeightfieldShape
    fun createCompoundShape(): CompoundShape

    /**
     * Body creation
     */
    fun createRigidBody(shape: CollisionShape, mass: Float, transform: Matrix4): RigidBody
    fun createCharacterController(shape: CollisionShape, stepHeight: Float): CharacterController

    /**
     * Constraint creation
     */
    fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint

    fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint

    fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint
}

/**
 * Platform-specific physics engine factory
 */
expect fun createDefaultPhysicsEngine(): PhysicsEngine