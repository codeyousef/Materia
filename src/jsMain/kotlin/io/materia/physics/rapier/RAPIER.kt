/**
 * External Rapier WASM module declarations
 * JavaScript interop bindings for @dimforge/rapier3d
 */
package io.materia.physics

import kotlin.js.Promise

/**
 * External Rapier WASM module declarations
 */
@JsModule("@dimforge/rapier3d")
@JsNonModule
external object RAPIER {
    fun init(): Promise<Unit>

    class World(gravity: dynamic) {
        var gravity: dynamic
        var timestep: Float
        var maxVelocityIterations: Int
        var maxPositionIterations: Int
        var maxCcdSubsteps: Int

        fun step()
        fun createRigidBody(desc: RigidBodyDesc): RigidBody
        fun createCollider(desc: ColliderDesc, body: RigidBody?): Collider
        fun removeRigidBody(body: RigidBody)
        fun removeCollider(collider: Collider)
        fun castRay(ray: Ray, maxToi: Float, solid: Boolean, groups: Int): dynamic
        fun castShape(
            shapePos: dynamic,
            shapeRot: dynamic,
            shapeVel: dynamic,
            shape: dynamic,
            maxToi: Float,
            groups: Int
        ): dynamic

        fun intersectionsWithShape(
            shapePos: dynamic,
            shapeRot: dynamic,
            shape: dynamic,
            callback: (Collider) -> Boolean,
            groups: Int
        )

        fun contactPair(collider1: Collider, collider2: Collider): ContactManifold?
        fun intersectionPair(collider1: Collider, collider2: Collider): Boolean
        fun createImpulseJoint(desc: dynamic, body1: RigidBody, body2: RigidBody?): ImpulseJoint
        fun removeImpulseJoint(joint: ImpulseJoint, wakeUp: Boolean)
        fun forEachActiveRigidBody(callback: (RigidBody) -> Unit)
        fun forEachCollider(callback: (Collider) -> Unit)
    }

    class RigidBodyDesc {
        companion object {
            fun dynamic(): RigidBodyDesc
            fun kinematicPositionBased(): RigidBodyDesc
            fun kinematicVelocityBased(): RigidBodyDesc
            fun fixed(): RigidBodyDesc
        }

        fun setTranslation(x: Float, y: Float, z: Float): RigidBodyDesc
        fun setRotation(quaternion: dynamic): RigidBodyDesc
        fun setLinvel(x: Float, y: Float, z: Float): RigidBodyDesc
        fun setAngvel(x: Float, y: Float, z: Float): RigidBodyDesc
        fun setGravityScale(scale: Float): RigidBodyDesc
        fun setCanSleep(canSleep: Boolean): RigidBodyDesc
        fun setCcdEnabled(enabled: Boolean): RigidBodyDesc
        fun setLinearDamping(damping: Float): RigidBodyDesc
        fun setAngularDamping(damping: Float): RigidBodyDesc
        fun setAdditionalMass(mass: Float): RigidBodyDesc
        fun setAdditionalMassProperties(
            mass: Float,
            centerOfMass: dynamic,
            principalAngularInertia: dynamic,
            angularInertiaFrameRotation: dynamic
        ): RigidBodyDesc

        fun restrictRotations(x: Boolean, y: Boolean, z: Boolean): RigidBodyDesc
        fun lockTranslations(): RigidBodyDesc
        fun lockRotations(): RigidBodyDesc
    }

    class RigidBody {
        fun translation(): dynamic
        fun rotation(): dynamic
        fun linvel(): dynamic
        fun angvel(): dynamic
        fun mass(): Float
        fun effectiveMass(): Float
        fun centerOfMass(): dynamic
        fun setTranslation(vector: dynamic, wakeUp: Boolean)
        fun setRotation(quaternion: dynamic, wakeUp: Boolean)
        fun setLinvel(vector: dynamic, wakeUp: Boolean)
        fun setAngvel(vector: dynamic, wakeUp: Boolean)
        fun applyImpulse(impulse: dynamic, wakeUp: Boolean)
        fun applyTorqueImpulse(torque: dynamic, wakeUp: Boolean)
        fun applyImpulseAtPoint(impulse: dynamic, point: dynamic, wakeUp: Boolean)
        fun applyForce(force: dynamic, wakeUp: Boolean)
        fun applyTorque(torque: dynamic, wakeUp: Boolean)
        fun applyForceAtPoint(force: dynamic, point: dynamic, wakeUp: Boolean)
        fun resetForces(wakeUp: Boolean)
        fun resetTorques(wakeUp: Boolean)
        fun addForce(force: dynamic, wakeUp: Boolean)
        fun addTorque(torque: dynamic, wakeUp: Boolean)
        fun addForceAtPoint(force: dynamic, point: dynamic, wakeUp: Boolean)
        fun wakeUp()
        fun sleep()
        fun isSleeping(): Boolean
        fun isKinematic(): Boolean
        fun isFixed(): Boolean
        fun isDynamic(): Boolean
        fun isEnabled(): Boolean
        fun setEnabled(enabled: Boolean)
        fun setBodyType(type: RigidBodyType, wakeUp: Boolean)
        fun setGravityScale(scale: Float, wakeUp: Boolean)
        fun gravityScale(): Float
        fun userData(): Any?
        fun handle(): Int
    }

    class ColliderDesc {
        companion object {
            fun cuboid(hx: Float, hy: Float, hz: Float): ColliderDesc
            fun ball(radius: Float): ColliderDesc
            fun capsule(halfHeight: Float, radius: Float): ColliderDesc
            fun cylinder(halfHeight: Float, radius: Float): ColliderDesc
            fun cone(halfHeight: Float, radius: Float): ColliderDesc
            fun convexHull(vertices: FloatArray): ColliderDesc?
            fun convexMesh(vertices: FloatArray, indices: IntArray): ColliderDesc?
            fun trimesh(vertices: FloatArray, indices: IntArray): ColliderDesc?
            fun heightfield(
                nrows: Int,
                ncols: Int,
                heights: FloatArray,
                scale: dynamic
            ): ColliderDesc
        }

        fun setTranslation(x: Float, y: Float, z: Float): ColliderDesc
        fun setRotation(quaternion: dynamic): ColliderDesc
        fun setMass(mass: Float): ColliderDesc
        fun setDensity(density: Float): ColliderDesc
        fun setFriction(friction: Float): ColliderDesc
        fun setFrictionCombineRule(rule: CoefficientCombineRule): ColliderDesc
        fun setRestitution(restitution: Float): ColliderDesc
        fun setRestitutionCombineRule(rule: CoefficientCombineRule): ColliderDesc
        fun setSensor(isSensor: Boolean): ColliderDesc
        fun setCollisionGroups(groups: Int): ColliderDesc
        fun setSolverGroups(groups: Int): ColliderDesc
        fun setActiveCollisionTypes(types: Int): ColliderDesc
        fun setActiveEvents(events: Int): ColliderDesc
        fun setActiveHooks(hooks: Int): ColliderDesc
    }

    class Collider {
        fun shape(): ColliderShape
        fun setShape(shape: ColliderShape)
        fun setTranslation(vector: dynamic)
        fun setRotation(quaternion: dynamic)
        fun translation(): dynamic
        fun rotation(): dynamic
        fun setSensor(isSensor: Boolean)
        fun isSensor(): Boolean
        fun setFriction(friction: Float)
        fun friction(): Float
        fun setFrictionCombineRule(rule: CoefficientCombineRule)
        fun frictionCombineRule(): CoefficientCombineRule
        fun setRestitution(restitution: Float)
        fun restitution(): Float
        fun setRestitutionCombineRule(rule: CoefficientCombineRule)
        fun restitutionCombineRule(): CoefficientCombineRule
        fun setCollisionGroups(groups: Int)
        fun collisionGroups(): Int
        fun setSolverGroups(groups: Int)
        fun solverGroups(): Int
        fun setDensity(density: Float)
        fun density(): Float
        fun setMass(mass: Float)
        fun mass(): Float
        fun volume(): Float
        fun parent(): RigidBody?
        fun setEnabled(enabled: Boolean)
        fun isEnabled(): Boolean
        fun userData(): Any?
        fun handle(): Int
    }

    class ColliderShape

    class Ray(origin: dynamic, dir: dynamic)

    class ContactManifold {
        fun normal(): dynamic
        fun localNormal1(): dynamic
        fun localNormal2(): dynamic
        fun numContacts(): Int
        fun contactLocalPoint1(i: Int): dynamic
        fun contactLocalPoint2(i: Int): dynamic
        fun contactDist(i: Int): Float
        fun contactImpulse(i: Int): Float
        fun contactTangentImpulse(i: Int): dynamic
    }

    class ImpulseJoint {
        fun bodyHandle1(): Int
        fun bodyHandle2(): Int?
        fun setContacts(enabled: Boolean)
        fun contactsEnabled(): Boolean
    }

    enum class RigidBodyType {
        Dynamic,
        Fixed,
        KinematicPositionBased,
        KinematicVelocityBased
    }

    enum class CoefficientCombineRule {
        Average,
        Min,
        Multiply,
        Max
    }

    enum class ActiveEvents {
        COLLISION_EVENTS,
        CONTACT_FORCE_EVENTS
    }

    enum class ActiveCollisionTypes {
        DYNAMIC_DYNAMIC,
        DYNAMIC_KINEMATIC,
        DYNAMIC_FIXED,
        KINEMATIC_KINEMATIC,
        KINEMATIC_FIXED,
        FIXED_FIXED
    }

    class Vector3(x: Float, y: Float, z: Float) {
        var x: Float
        var y: Float
        var z: Float
    }

    class Quaternion(x: Float, y: Float, z: Float, w: Float) {
        var x: Float
        var y: Float
        var z: Float
        var w: Float
    }

    class EventQueue {
        fun drainCollisionEvents(handler: (Int, Int, Boolean) -> Unit)
        fun drainContactForceEvents(handler: (ContactForceEvent) -> Unit)
        fun clear()
    }

    class ContactForceEvent {
        fun collider1(): Int
        fun collider2(): Int
        fun totalForce(): dynamic
        fun totalForceMagnitude(): Float
        fun maxForceDirection(): dynamic
        fun maxForceMagnitude(): Float
    }
}
