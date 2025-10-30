/**
 * Bullet Physics Engine Factory
 * Main entry point for creating Bullet physics objects
 */
package io.materia.physics.bullet

import io.materia.core.math.*
import io.materia.physics.*
import io.materia.physics.bullet.body.BulletRigidBody
import io.materia.physics.bullet.character.BulletCharacterController
import io.materia.physics.bullet.constraints.*
import io.materia.physics.bullet.shapes.*
import io.materia.physics.bullet.world.BulletPhysicsWorld

/**
 * Bullet physics engine implementation
 */
class BulletPhysicsEngine : PhysicsEngine {
    override val name = "Bullet"
    override val version = "3.24"

    override fun createWorld(gravity: Vector3): PhysicsWorld {
        return BulletPhysicsWorld(gravity)
    }

    override fun destroyWorld(world: PhysicsWorld): PhysicsOperationResult<Unit> {
        return try {
            (world as? BulletPhysicsWorld)?.dispose()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to destroy world"))
        }
    }

    override fun createBoxShape(halfExtents: Vector3): BoxShape {
        return BulletBoxShape(halfExtents)
    }

    override fun createSphereShape(radius: Float): SphereShape {
        return BulletSphereShape(radius)
    }

    override fun createCapsuleShape(radius: Float, height: Float): CapsuleShape {
        return BulletCapsuleShape(radius, height)
    }

    override fun createCylinderShape(halfExtents: Vector3): CylinderShape {
        return BulletCylinderShape(halfExtents)
    }

    override fun createConeShape(radius: Float, height: Float): ConeShape {
        return BulletConeShape(radius, height)
    }

    override fun createConvexHullShape(vertices: FloatArray): ConvexHullShape {
        return BulletConvexHullShape(vertices)
    }

    override fun createTriangleMeshShape(
        vertices: FloatArray,
        indices: IntArray
    ): TriangleMeshShape {
        return BulletTriangleMeshShape(vertices, indices)
    }

    override fun createHeightfieldShape(
        width: Int,
        height: Int,
        heightData: FloatArray
    ): HeightfieldShape {
        return BulletHeightfieldShape(width, height, heightData)
    }

    override fun createCompoundShape(): CompoundShape {
        return BulletCompoundShape()
    }

    override fun createRigidBody(
        shape: CollisionShape,
        mass: Float,
        transform: Matrix4
    ): RigidBody {
        return BulletRigidBody(
            id = "rb_${System.currentTimeMillis()}",
            initialShape = shape
        ).apply {
            this.mass = mass
            this.transform = transform
        }
    }

    override fun createCharacterController(
        shape: CollisionShape,
        stepHeight: Float
    ): CharacterController {
        return BulletCharacterController(shape, stepHeight)
    }

    override fun createPointToPointConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint {
        return BulletPointToPointConstraint(
            id = "p2p_${System.currentTimeMillis()}",
            bodyA = bodyA,
            bodyB = bodyB,
            pivotA = pivotA,
            pivotB = pivotB
        )
    }

    override fun createHingeConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint {
        return BulletHingeConstraint(bodyA, bodyB, pivotA, pivotB, axisA, axisB)
    }

    override fun createSliderConstraint(
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint {
        return BulletSliderConstraint(bodyA, bodyB, frameA, frameB)
    }
}
