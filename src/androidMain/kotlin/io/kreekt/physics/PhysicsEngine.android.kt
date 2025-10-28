package io.kreekt.physics

import io.kreekt.core.math.*
import java.util.UUID

/**
 * JS implementation of physics engine
 * Note: This is a basic implementation. For production use, integrate Rapier WASM
 * or another JavaScript physics library like Cannon.js or Ammo.js.
 */
actual fun createDefaultPhysicsEngine(): PhysicsEngine = AndroidPhysicsEngine()

/**
 * Basic JavaScript physics engine implementation
 */
class AndroidPhysicsEngine : PhysicsEngine {
    override val name: String = "JS Basic Physics"
    override val version: String = "1.0.0"

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        return AndroidBasicPhysicsWorld(gravity)
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit> {
        return PhysicsOperationResult.Success(Unit)
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape {
        return AndroidBasicBoxShape(halfExtents)
    }

    override fun createSphereShape(radius: Float): SphereShape {
        return AndroidBasicSphereShape(radius)
    }

    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape {
        return AndroidBasicCapsuleShape(radius, height)
    }

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return AndroidBasicCylinderShape(halfExtents)
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return AndroidBasicConeShape(radius, height)
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return AndroidBasicConvexHullShape(vertices)
    }

    override fun createTriangleMeshShape(vertices: FloatArray, indices: IntArray): TriangleMeshShape {
        return AndroidBasicTriangleMeshShape(vertices, indices)
    }

    override fun createHeightfieldShape(width: Int, height: Int, heightData: FloatArray): HeightfieldShape {
        return AndroidBasicHeightfieldShape(width, height, heightData)
    }

    override fun createCompoundShape(): CompoundShape {
        return AndroidBasicCompoundShape()
    }

    override fun createRigidBody(shape: CollisionShape, mass: Float, transform: Matrix4): RigidBody {
        return AndroidBasicRigidBody(shape, mass, transform)
    }

    override fun createCharacterController(shape: CollisionShape, stepHeight: Float): CharacterController {
        return AndroidBasicCharacterController(shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        return AndroidBasicPointToPointConstraint(bodyA, bodyB, pivotA, pivotB)
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        return AndroidBasicHingeConstraint(bodyA, bodyB, pivotA, pivotB, axisA, axisB)
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        return AndroidBasicSliderConstraint(bodyA, bodyB, frameA, frameB)
    }
}

/**
 * Basic physics world for JavaScript
 */
class AndroidBasicPhysicsWorld(override var gravity: Vector3) : PhysicsWorld {
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
                body.linearVelocity = body.linearVelocity.times(1f - body.linearDamping * deltaTime)
                body.angularVelocity = body.angularVelocity.times(1f - body.angularDamping * deltaTime)
            }
        }

        return PhysicsOperationResult.Success(Unit)
    }

    override fun pause() {
        // Implementation would pause simulation
    }

    override fun resume() {
        // Implementation would resume simulation
    }

    override fun reset() {
        rigidBodies.clear()
        constraints.clear()
        collisionObjects.clear()
    }

    override fun raycast(from: Vector3, to: Vector3, groups: Int): RaycastResult? {
        // Basic ray casting - would need proper implementation
        return null
    }

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
        // Simplified implementation
        return emptyList()
    }

    override fun overlaps(shape: CollisionShape, transform: Matrix4, groups: Int): List<CollisionObject> {
        // Simplified implementation
        return emptyList()
    }
}

// Basic shape implementations for JS
abstract class AndroidBasicCollisionShape : CollisionShape {
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

class AndroidBasicBoxShape(override val halfExtents: Vector3) : AndroidBasicCollisionShape(), BoxShape {
    override val shapeType: ShapeType = ShapeType.BOX

    override fun getHalfExtentsWithMargin(): Vector3 = halfExtents + Vector3(margin, margin, margin)
    override fun getHalfExtentsWithoutMargin(): Vector3 = halfExtents
    override fun calculateInertia(mass: Float): Matrix3 = PhysicsUtils.calculateBoxInertia(mass, halfExtents * 2f)
}

class AndroidBasicSphereShape(override val radius: Float) : AndroidBasicCollisionShape(), SphereShape {
    override val shapeType: ShapeType = ShapeType.SPHERE

    override fun getRadiusWithMargin(): Float = radius + margin
    override fun getRadiusWithoutMargin(): Float = radius
    override fun calculateInertia(mass: Float): Matrix3 = PhysicsUtils.calculateSphereInertia(mass, radius)
}

class AndroidBasicCapsuleShape(
    override val radius: Float,
    override val height: Float
) : AndroidBasicCollisionShape(), CapsuleShape {
    override val shapeType: ShapeType = ShapeType.CAPSULE
    override val upAxis: Int = 1

    override fun getHalfHeight(): Float = height / 2f
    override fun calculateInertia(mass: Float): Matrix3 = PhysicsUtils.calculateCylinderInertia(mass, radius, height)
}

class AndroidBasicCylinderShape(override val halfExtents: Vector3) : AndroidBasicCollisionShape(), CylinderShape {
    override val shapeType: ShapeType = ShapeType.CYLINDER
    override val upAxis: Int = 1

    override fun getRadius(): Float = maxOf(halfExtents.x, halfExtents.z)
    override fun getHalfHeight(): Float = halfExtents.y
    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateCylinderInertia(mass, getRadius(), halfExtents.y * 2f)
}

class AndroidBasicConeShape(
    override val radius: Float,
    override val height: Float
) : AndroidBasicCollisionShape(), ConeShape {
    override val shapeType: ShapeType = ShapeType.CONE
    override val upAxis: Int = 1

    override fun getConeRadius(): Float = radius
    override fun getConeHeight(): Float = height
    override fun calculateInertia(mass: Float): Matrix3 = Matrix3.IDENTITY
}

class AndroidBasicConvexHullShape(override val vertices: FloatArray) : AndroidBasicCollisionShape(), ConvexHullShape {
    override val shapeType: ShapeType = ShapeType.CONVEX_HULL
    override val numVertices: Int = vertices.size / 3

    override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {}
    override fun getScaledPoint(index: Int): Vector3 = Vector3.ZERO
    override fun getUnscaledPoints(): List<Vector3> = emptyList()
    override fun optimizeConvexHull() {}

    override fun calculateInertia(mass: Float): Matrix3 {
        // Simplified inertia for convex hull - treat as sphere
        val radius = boundingBox.getSize().length() / 2f
        val i = (2f / 5f) * mass * radius * radius
        return Matrix3.diagonal(Vector3(i, i, i))
    }
}

class AndroidBasicTriangleMeshShape(
    override val vertices: FloatArray,
    override val indices: IntArray
) : AndroidBasicCollisionShape(), TriangleMeshShape {
    override val shapeType: ShapeType = ShapeType.TRIANGLE_MESH
    override val triangleCount: Int = indices.size / 3

    override fun getTriangle(index: Int): Triangle = Triangle(Vector3.ZERO, Vector3.ZERO, Vector3.ZERO)
    override fun processAllTriangles(callback: TriangleCallback, aabbMin: Vector3, aabbMax: Vector3) {}
    override fun buildBVH(): MeshBVH = MeshBVH(emptyList(), emptyList())

    override fun calculateInertia(mass: Float): Matrix3 {
        // Simplified inertia for triangle mesh - treat as box
        val size = boundingBox.getSize()
        val ix = (mass / 12f) * (size.y * size.y + size.z * size.z)
        val iy = (mass / 12f) * (size.x * size.x + size.z * size.z)
        val iz = (mass / 12f) * (size.x * size.x + size.y * size.y)
        return Matrix3.diagonal(Vector3(ix, iy, iz))
    }
}

class AndroidBasicHeightfieldShape(
    override val width: Int,
    override val height: Int,
    override val heightData: FloatArray
) : AndroidBasicCollisionShape(), HeightfieldShape {
    override val shapeType: ShapeType = ShapeType.HEIGHTFIELD
    override val maxHeight: Float = heightData.maxOrNull() ?: 0f
    override val minHeight: Float = heightData.minOrNull() ?: 0f
    override val upAxis: Int = 1

    override fun getHeightAtPoint(x: Float, z: Float): Float = 0f
    override fun setHeightValue(x: Int, z: Int, height: Float) {}

    override fun calculateInertia(mass: Float): Matrix3 {
        // Simplified inertia for heightfield - treat as box
        val size = boundingBox.getSize()
        val ix = (mass / 12f) * (size.y * size.y + size.z * size.z)
        val iy = (mass / 12f) * (size.x * size.x + size.z * size.z)
        val iz = (mass / 12f) * (size.x * size.x + size.y * size.y)
        return Matrix3.diagonal(Vector3(ix, iy, iz))
    }
}

class AndroidBasicCompoundShape : AndroidBasicCollisionShape(), CompoundShape {
    override val shapeType: ShapeType = ShapeType.COMPOUND
    override val childShapes: MutableList<ChildShape> = mutableListOf()

    override fun addChildShape(transform: Matrix4, shape: CollisionShape): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit> =
        PhysicsOperationResult.Success(Unit)

    override fun recalculateLocalAabb() {}

    override fun calculateInertia(mass: Float): Matrix3 {
        // Calculate combined inertia for compound shape
        var totalInertia = Matrix3.zero()
        val massPerChild = mass / childShapes.size.coerceAtLeast(1)

        childShapes.forEach { child ->
            val childInertia = child.shape.calculateInertia(massPerChild)
            totalInertia = totalInertia + childInertia
        }

        return if (totalInertia == Matrix3.zero()) {
            // Fallback to sphere inertia
            val radius = boundingBox.getSize().length() / 2f
            val i = (2f / 5f) * mass * radius * radius
            Matrix3.diagonal(Vector3(i, i, i))
        } else {
            totalInertia
        }
    }
}

// Basic rigid body for JS
class AndroidBasicRigidBody(
    override var collisionShape: CollisionShape,
    override var mass: Float,
    override var transform: Matrix4
) : RigidBody {
    override val id: String = UUID.randomUUID().toString()
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

    override var bodyType: RigidBodyType = if (mass > 0f) RigidBodyType.DYNAMIC else RigidBodyType.STATIC
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
        val newPos = currentPos.plus(offset)
        transform = Matrix4().makeTranslation(newPos.x, newPos.y, newPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(transform.getRotation())
        )
    }

    override fun rotate(rotation: Quaternion) {
        val currentPos = transform.getTranslation()
        val currentRot = transform.getRotation()
        val newRot = currentRot.multiply(rotation)
        transform = Matrix4().makeTranslation(currentPos.x, currentPos.y, currentPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(newRot)
        )
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
        transform = Matrix4().makeTranslation(position.x, position.y, position.z).multiply(
            Matrix4().makeRotationFromQuaternion(rotation)
        )
    }

    override fun getCenterOfMassTransform(): Matrix4 = transform
}

// Basic character controller for JS
class AndroidBasicCharacterController(
    override var collisionShape: CollisionShape,
    override var stepHeight: Float
) : CharacterController {
    override val id: String = UUID.randomUUID().toString()
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
        val newPos = currentPos.plus(offset)
        transform = Matrix4().makeTranslation(newPos.x, newPos.y, newPos.z)
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

// Basic constraints for JS
class AndroidBasicPointToPointConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3
) : PointToPointConstraint {
    override val id: String = UUID.randomUUID().toString()
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

class AndroidBasicHingeConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3,
    override val axisA: Vector3,
    override val axisB: Vector3
) : HingeConstraint {
    override val id: String = UUID.randomUUID().toString()
    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE
    override var lowerLimit: Float = -kotlin.math.PI.toFloat()
    override var upperLimit: Float = kotlin.math.PI.toFloat()
    override var enableAngularMotor: Boolean = false
    override var targetVelocity: Float = 0f
    override var maxMotorImpulse: Float = 0f

    override fun setLimit(low: Float, high: Float, softness: Float, biasFactor: Float, relaxationFactor: Float) {}
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

class AndroidBasicSliderConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : SliderConstraint {
    override val id: String = UUID.randomUUID().toString()
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
