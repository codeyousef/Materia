/**
 * Rapier Physics Engine Implementation
 * Main factory for creating Rapier physics objects
 */
package io.materia.physics.rapier

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.physics.*
import io.materia.physics.rapier.body.RapierRigidBody
import io.materia.physics.rapier.character.RapierCharacterController
import io.materia.physics.rapier.constraints.RapierHingeConstraint
import io.materia.physics.rapier.constraints.RapierPointToPointConstraint
import io.materia.physics.rapier.constraints.RapierSliderConstraint
import io.materia.physics.rapier.shapes.*
import io.materia.physics.rapier.world.RapierPhysicsWorld
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/**
 * Rapier physics engine implementation
 */
class RapierPhysicsEngine : PhysicsEngine {
    override val name = "Rapier"
    override val version = "0.20.0"

    private var initialized = false

    init {
        GlobalScope.launch {
            RAPIER.init().await()
            initialized = true
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            console.warn("RapierPhysicsEngine not yet initialized")
        }
    }

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        ensureInitialized()
        return RapierPhysicsWorld(gravity)
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsResult<Unit> {
        return try {
            (world as? RapierPhysicsWorld)?.reset()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to destroy world"))
        }
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape {
        return RapierBoxShape(halfExtents)
    }

    override fun createSphereShape(radius: Float): SphereShape {
        return RapierSphereShape(radius)
    }

    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape {
        return RapierCapsuleShape(radius, height)
    }

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return RapierCylinderShape(halfExtents)
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return RapierConeShape(radius, height)
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return RapierConvexHullShape(vertices)
    }

    override fun createTriangleMeshShape(
        vertices: FloatArray,
        indices: IntArray
    ): TriangleMeshShape {
        return RapierTriangleMeshShape(vertices, indices)
    }

    override fun createHeightfieldShape(
        width: Int,
        height: Int,
        heightData: FloatArray
    ): HeightfieldShape {
        return RapierHeightfieldShape(width, height, heightData)
    }

    override fun createCompoundShape(): CompoundShape {
        return RapierCompoundShape()
    }

    override fun createRigidBody(
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4
    ): RigidBody {
        ensureInitialized()

        // Create body description
        val desc = if (mass > 0f) {
            RAPIER.RigidBodyDesc.dynamic()
        } else {
            RAPIER.RigidBodyDesc.fixed()
        }

        val position = Vector3(transform.m03, transform.m13, transform.m23)
        val rotation = transform.extractRotation()

        desc.setTranslation(position.x, position.y, position.z)
        desc.setRotation(toRapierQuaternion(rotation))

        // Create world temporarily (this is a limitation of the current design)
        val tempWorld = RAPIER.World(RAPIER.Vector3(0f, -9.81f, 0f))
        val rapierBody = tempWorld.createRigidBody(desc)

        // Create collider
        val colliderDesc = createColliderDesc(shape)
        colliderDesc.setMass(mass)
        val rapierCollider = tempWorld.createCollider(colliderDesc, rapierBody)

        return RapierRigidBody(
            id = "rb_${kotlin.js.Date.now().toLong()}",
            rapierBody = rapierBody,
            rapierCollider = rapierCollider,
            initialShape = shape,
            world = tempWorld
        )
    }

    override fun createCharacterController(
        shape: CollisionShape,
        stepHeight: Float
    ): CharacterController {
        // Rapier doesn't have built-in character controller, use kinematic body approach
        ensureInitialized()
        return RapierCharacterController(shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        ensureInitialized()
        return RapierPointToPointConstraint(bodyA, bodyB, pivotA, pivotB)
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        ensureInitialized()
        return RapierHingeConstraint(bodyA, bodyB, pivotA, pivotB, axisA, axisB)
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        ensureInitialized()
        return RapierSliderConstraint(bodyA, bodyB, frameA, frameB)
    }

    private fun createColliderDesc(shape: CollisionShape): RAPIER.ColliderDesc {
        return when (shape) {
            is BoxShape -> RAPIER.ColliderDesc.cuboid(
                shape.halfExtents.x,
                shape.halfExtents.y,
                shape.halfExtents.z
            )

            is SphereShape -> RAPIER.ColliderDesc.ball(shape.radius)
            is CapsuleShape -> RAPIER.ColliderDesc.capsule(shape.height / 2f, shape.radius)
            is CylinderShape -> RAPIER.ColliderDesc.cylinder(
                shape.halfExtents.y,
                shape.halfExtents.x
            )

            is ConeShape -> RAPIER.ColliderDesc.cone(shape.height / 2f, shape.radius)
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

// Type conversion helpers
private fun toRapierQuaternion(q: Quaternion): RAPIER.Quaternion =
    RAPIER.Quaternion(q.x, q.y, q.z, q.w)

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

// Platform-specific actual implementations
fun createRapierPhysicsEngine(): PhysicsEngine = RapierPhysicsEngine()
