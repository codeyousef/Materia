package io.materia.physics

import io.materia.core.math.*

/**
 * Native implementation of physics engine
 * Note: This is a basic implementation. For production use, integrate native
 * physics libraries like Bullet Physics or Box2D through C interop.
 */
actual fun createDefaultPhysicsEngine(): PhysicsEngine = NativePhysicsEngine()

/**
 * Basic native physics engine implementation
 */
class NativePhysicsEngine : PhysicsEngine {
    override val name: String = "Native Basic Physics"
    override val version: String = "1.0.0"

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        return NativeBasicPhysicsWorld(gravity)
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit> {
        return PhysicsOperationResult.Success(Unit)
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape {
        return NativeBasicBoxShape(halfExtents)
    }

    override fun createSphereShape(radius: Float): SphereShape {
        return NativeBasicSphereShape(radius)
    }

    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape {
        return NativeBasicCapsuleShape(radius, height)
    }

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return NativeBasicCylinderShape(halfExtents)
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return NativeBasicConeShape(radius, height)
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return NativeBasicConvexHullShape(vertices)
    }

    override fun createTriangleMeshShape(
        vertices: FloatArray,
        indices: IntArray
    ): TriangleMeshShape {
        return NativeBasicTriangleMeshShape(vertices, indices)
    }

    override fun createHeightfieldShape(
        width: Int,
        height: Int,
        heightData: FloatArray
    ): HeightfieldShape {
        return NativeBasicHeightfieldShape(width, height, heightData)
    }

    override fun createCompoundShape(): CompoundShape {
        return NativeBasicCompoundShape()
    }

    override fun createRigidBody(
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4
    ): RigidBody {
        return NativeBasicRigidBody(shape, mass, transform)
    }

    override fun createCharacterController(
        shape: CollisionShape,
        stepHeight: Float
    ): CharacterController {
        return NativeBasicCharacterController(shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        return NativeBasicPointToPointConstraint(bodyA, bodyB, pivotA, pivotB)
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        return NativeBasicHingeConstraint(bodyA, bodyB, pivotA, pivotB, axisA, axisB)
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        return NativeBasicSliderConstraint(bodyA, bodyB, frameA, frameB)
    }
}

/**
 * Basic physics world for native platforms
 */
class NativeBasicPhysicsWorld(override var gravity: Vector3) : PhysicsWorld {
    private val rigidBodies = mutableListOf<RigidBody>()
    private val constraints = mutableListOf<PhysicsConstraint>()
    private val collisionObjects = mutableListOf<CollisionObject>()
    private var collisionCallback: CollisionCallback? = null

    override var timeStep: Float = 1f / 60f
    override var maxSubSteps: Int = 10
    override var solverIterations: Int = 10
    override var broadphase: BroadphaseType = BroadphaseType.DBVT

    override fun addRigidBody(body: RigidBody): PhysicsResult<Unit> {
        rigidBodies.add(body)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeRigidBody(body: RigidBody): PhysicsResult<Unit> {
        rigidBodies.remove(body)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun getRigidBodies(): List<RigidBody> = rigidBodies

    override fun getRigidBody(id: String): RigidBody? = rigidBodies.find { it.id == id }

    override fun addConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        constraints.add(constraint)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeConstraint(constraint: PhysicsConstraint): PhysicsResult<Unit> {
        constraints.remove(constraint)
        return PhysicsOperationResult.Success(Unit)
    }

    override fun getConstraints(): List<PhysicsConstraint> = constraints

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
        // Basic physics simulation
        rigidBodies.forEach { body ->
            if (body.bodyType == RigidBodyType.DYNAMIC) {
                // Apply gravity
                body.applyCentralForce(gravity * body.mass)

                // Simple Euler integration
                val position = body.transform.getTranslation()
                val newPosition = position + body.linearVelocity * deltaTime
                body.translate(newPosition - position)

                // Apply damping
                body.linearVelocity = body.linearVelocity * (1f - body.linearDamping * deltaTime)
                body.angularVelocity = body.angularVelocity * (1f - body.angularDamping * deltaTime)
            }
        }

        return PhysicsOperationResult.Success(Unit)
    }

    override fun pause() {}
    override fun resume() {}
    override fun reset() {
        rigidBodies.clear()
        constraints.clear()
        collisionObjects.clear()
    }

    override fun raycast(from: Vector3, to: Vector3, groups: Int): RaycastResult? = null

    override fun sphereCast(center: Vector3, radius: Float, groups: Int): List<CollisionObject> {
        return collisionObjects.filter { obj ->
            val objPos = obj.transform.getTranslation()
            (objPos - center).length() <= radius
        }
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
}

// Basic shape implementations for native
abstract class NativeBasicCollisionShape : CollisionShape {
    override val margin: Float = 0.04f
    override val localScaling: Vector3 = Vector3.ONE
    override val boundingBox: Box3 = Box3()

    override fun getVolume(): Float = 0f
    override fun getSurfaceArea(): Float = 0f
    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float): Vector3 = Vector3.ONE

    override fun serialize(): ByteArray = ByteArray(0)
    override fun clone(): CollisionShape = this
}

class NativeBasicBoxShape(override val halfExtents: Vector3) : NativeBasicCollisionShape(),
    BoxShape {
    override val shapeType: ShapeType = ShapeType.BOX

    override fun getHalfExtentsWithMargin(): Vector3 = halfExtents + Vector3(margin, margin, margin)
    override fun getHalfExtentsWithoutMargin(): Vector3 = halfExtents
    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateBoxInertia(mass, halfExtents * 2f)
}

class NativeBasicSphereShape(override val radius: Float) : NativeBasicCollisionShape(),
    SphereShape {
    override val shapeType: ShapeType = ShapeType.SPHERE

    override fun getRadiusWithMargin(): Float = radius + margin
    override fun getRadiusWithoutMargin(): Float = radius
    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateSphereInertia(mass, radius)
}

class NativeBasicCapsuleShape(
    override val radius: Float,
    override val height: Float
) : NativeBasicCollisionShape(), CapsuleShape {
    override val shapeType: ShapeType = ShapeType.CAPSULE
    override val upAxis: Int = 1

    override fun getHalfHeight(): Float = height / 2f
    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateCylinderInertia(mass, radius, height)
}

class NativeBasicCylinderShape(override val halfExtents: Vector3) : NativeBasicCollisionShape(),
    CylinderShape {
    override val shapeType: ShapeType = ShapeType.CYLINDER
    override val upAxis: Int = 1

    override fun getRadius(): Float = maxOf(halfExtents.x, halfExtents.z)
    override fun getHalfHeight(): Float = halfExtents.y
    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateCylinderInertia(mass, getRadius(), halfExtents.y * 2f)
}

class NativeBasicConeShape(
    override val radius: Float,
    override val height: Float
) : NativeBasicCollisionShape(), ConeShape {
    override val shapeType: ShapeType = ShapeType.CONE
    override val upAxis: Int = 1

    override fun getConeRadius(): Float = radius
    override fun getConeHeight(): Float = height
    override fun calculateInertia(mass: Float): Matrix3 = Matrix3.IDENTITY
}

class NativeBasicConvexHullShape(override val vertices: FloatArray) : NativeBasicCollisionShape(),
    ConvexHullShape {
    override val shapeType: ShapeType = ShapeType.CONVEX_HULL
    override val numVertices: Int = vertices.size / 3

    override fun calculateInertia(mass: Float): Matrix3 {
        // Approximate convex hull inertia using axis-aligned bounding box
        val aabb = boundingBox
        val size = Vector3(
            aabb.max.x - aabb.min.x,
            aabb.max.y - aabb.min.y,
            aabb.max.z - aabb.min.z
        )
        val x2 = size.x * size.x
        val y2 = size.y * size.y
        val z2 = size.z * size.z
        val m = mass / 12f
        return Matrix3(
            floatArrayOf(
                m * (y2 + z2), 0f, 0f,
                0f, m * (x2 + z2), 0f,
                0f, 0f, m * (x2 + y2)
            )
        )
    }

    override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {}
    override fun getScaledPoint(index: Int): Vector3 = Vector3.ZERO
    override fun getUnscaledPoints(): List<Vector3> = emptyList()
    override fun optimizeConvexHull() {}
}

class NativeBasicTriangleMeshShape(
    override val vertices: FloatArray,
    override val indices: IntArray
) : NativeBasicCollisionShape(), TriangleMeshShape {
    override val shapeType: ShapeType = ShapeType.TRIANGLE_MESH
    override val triangleCount: Int = indices.size / 3

    override fun calculateInertia(mass: Float): Matrix3 {
        // Triangle meshes are static/concave, return zero inertia
        return Matrix3.ZERO
    }

    override fun getTriangle(index: Int): Triangle =
        Triangle(Vector3.ZERO, Vector3.ZERO, Vector3.ZERO)

    override fun processAllTriangles(
        callback: TriangleCallback,
        aabbMin: Vector3,
        aabbMax: Vector3
    ) {
    }

    override fun buildBVH(): MeshBVH = MeshBVH(emptyList(), emptyList())
}

class NativeBasicHeightfieldShape(
    override val width: Int,
    override val height: Int,
    override val heightData: FloatArray
) : NativeBasicCollisionShape(), HeightfieldShape {
    override val shapeType: ShapeType = ShapeType.HEIGHTFIELD
    override val maxHeight: Float = heightData.maxOrNull() ?: 0f
    override val minHeight: Float = heightData.minOrNull() ?: 0f
    override val upAxis: Int = 1

    override fun calculateInertia(mass: Float): Matrix3 {
        // Heightfields are static/terrain, return zero inertia
        return Matrix3.ZERO
    }

    override fun getHeightAtPoint(x: Float, z: Float): Float = 0f
    override fun setHeightValue(x: Int, z: Int, height: Float) {}
}

class NativeBasicCompoundShape : NativeBasicCollisionShape(), CompoundShape {
    override val shapeType: ShapeType = ShapeType.COMPOUND
    override val childShapes: MutableList<ChildShape> = mutableListOf()

    override fun calculateInertia(mass: Float): Matrix3 {
        // Compound shape: sum inertias of all child shapes
        var totalInertia = Matrix3.ZERO
        for (child in childShapes) {
            val childMass = mass / childShapes.size.coerceAtLeast(1)
            val childInertia = child.shape.calculateInertia(childMass)
            totalInertia = totalInertia + childInertia
        }
        return totalInertia
    }

    override fun addChildShape(transform: Matrix4, shape: CollisionShape): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun recalculateLocalAabb() {}
}

// Basic rigid body for native
class NativeBasicRigidBody(
    override var collisionShape: CollisionShape,
    override var mass: Float,
    override var transform: Matrix4
) : RigidBody {
    override val id: String = "rb_${kotlin.random.Random.nextLong()}_${hashCode()}"
    override var collisionGroups: Int = -1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.01f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    override var density: Float = 1000f
    override var restitution: Float = 0f
    override var friction: Float = 0.5f
    override var rollingFriction: Float = 0f
    override var linearDamping: Float = 0f
    override var angularDamping: Float = 0f

    override var linearVelocity: Vector3 = Vector3.ZERO
    override var angularVelocity: Vector3 = Vector3.ZERO
    override var linearFactor: Vector3 = Vector3.ONE
    override var angularFactor: Vector3 = Vector3.ONE

    override var bodyType: RigidBodyType =
        if (mass > 0f) RigidBodyType.DYNAMIC else RigidBodyType.STATIC
    override var activationState: ActivationState = ActivationState.ACTIVE
    override var sleepThreshold: Float = 0.8f
    override var ccdMotionThreshold: Float = 0f
    override var ccdSweptSphereRadius: Float = 0f

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        val currentPos = transform.getTranslation()
        val newTransform = Matrix4()
        newTransform.compose(currentPos + offset, transform.getRotation(), Vector3.ONE)
        transform = newTransform
    }

    override fun rotate(rotation: Quaternion) {
        val currentPos = transform.getTranslation()
        val currentRot = transform.getRotation()
        val newRotation = currentRot * rotation
        val newTransform = Matrix4()
        newTransform.compose(currentPos, newRotation, Vector3.ONE)
        transform = newTransform
    }

    override fun applyForce(force: Vector3, relativePosition: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun applyImpulse(impulse: Vector3, relativePosition: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun applyTorque(torque: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun applyTorqueImpulse(torque: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun applyCentralForce(force: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun applyCentralImpulse(impulse: Vector3): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun isActive(): Boolean = true
    override fun isKinematic(): Boolean = bodyType == RigidBodyType.KINEMATIC
    override fun isStatic(): Boolean = bodyType == RigidBodyType.STATIC

    override fun getInertia(): Matrix3 = Matrix3.IDENTITY
    override fun getInverseInertia(): Matrix3 = Matrix3.IDENTITY
    override fun getTotalForce(): Vector3 = Vector3.ZERO
    override fun getTotalTorque(): Vector3 = Vector3.ZERO

    override fun setTransform(position: Vector3, rotation: Quaternion) {
        val newTransform = Matrix4()
        newTransform.compose(position, rotation, Vector3.ONE)
        transform = newTransform
    }

    override fun getCenterOfMassTransform(): Matrix4 = transform
}

// Basic character controller for native
class NativeBasicCharacterController(
    override var collisionShape: CollisionShape,
    override var stepHeight: Float
) : CharacterController {
    override val id: String = "cc_${kotlin.random.Random.nextLong()}_${hashCode()}"
    override var transform: Matrix4 = Matrix4.IDENTITY
    override var collisionGroups: Int = -1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.01f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    override var maxSlope: Float = 45f * (kotlin.math.PI / 180f).toFloat()
    override var jumpSpeed: Float = 10f
    override var fallSpeed: Float = 55f
    override var walkDirection: Vector3 = Vector3.ZERO
    override var velocityForTimeInterval: Vector3 = Vector3.ZERO

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform
    override fun translate(offset: Vector3) {
        val currentPos = transform.getTranslation()
        val newTransform = Matrix4()
        newTransform.compose(currentPos + offset, transform.getRotation(), Vector3.ONE)
        transform = newTransform
    }

    override fun rotate(rotation: Quaternion) {}

    override fun onGround(): Boolean = false
    override fun canJump(): Boolean = false
    override fun jump(direction: Vector3) {}
    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {}
    override fun warp(origin: Vector3) {}
    override fun preStep(world: PhysicsWorld) {}
    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {}
}

// Basic constraints for native
class NativeBasicPointToPointConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3
) : PointToPointConstraint {
    override val id: String = "p2p_${kotlin.random.Random.nextLong()}_${hashCode()}"
    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE

    override fun setPivotA(pivot: Vector3) {}
    override fun setPivotB(pivot: Vector3) {}
    override fun updateRHS(timeStep: Float) {}
    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {}
    override fun getParam(param: ConstraintParam, axis: Int): Float = 0f
    override fun getAppliedImpulse(): Float = 0f
    override fun isEnabled(): Boolean = enabled
    override fun getInfo(info: ConstraintInfo) {}
}

class NativeBasicHingeConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3,
    override val axisA: Vector3,
    override val axisB: Vector3
) : HingeConstraint {
    override val id: String = "hinge_${kotlin.random.Random.nextLong()}_${hashCode()}"
    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE
    override var lowerLimit: Float = -kotlin.math.PI.toFloat()
    override var upperLimit: Float = kotlin.math.PI.toFloat()
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
    }

    override fun enableMotor(enable: Boolean) {
        enableAngularMotor = enable
    }

    override fun setMotorTarget(targetAngle: Float, deltaTime: Float) {}
    override fun getHingeAngle(): Float = 0f
    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {}
    override fun getParam(param: ConstraintParam, axis: Int): Float = 0f
    override fun getAppliedImpulse(): Float = 0f
    override fun isEnabled(): Boolean = enabled
    override fun getInfo(info: ConstraintInfo) {}
}

class NativeBasicSliderConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : SliderConstraint {
    override val id: String = "slider_${kotlin.random.Random.nextLong()}_${hashCode()}"
    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE
    override var lowerLinearLimit: Float = -1f
    override var upperLinearLimit: Float = 1f
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
    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {}
    override fun getParam(param: ConstraintParam, axis: Int): Float = 0f
    override fun getAppliedImpulse(): Float = 0f
    override fun isEnabled(): Boolean = enabled
    override fun getInfo(info: ConstraintInfo) {}
}