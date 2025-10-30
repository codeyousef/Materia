/**
 * Bullet Physics Engine Integration for JVM Platform
 *
 * This file serves as a compatibility layer, re-exporting the modular Bullet implementation.
 * All implementation has been extracted into focused modules:
 *
 * - bullet/BulletPhysicsEngine.kt - Main factory and engine
 * - bullet/world/BulletPhysicsWorld.kt - World simulation
 * - bullet/body/BulletRigidBody.kt - Rigid body dynamics
 * - bullet/shapes/BulletShapes.kt - Basic collision shapes
 * - bullet/shapes/BulletComplexShapes.kt - Complex collision shapes
 * - bullet/constraints/BulletConstraints.kt - Physics constraints
 * - bullet/character/BulletCharacterController.kt - Character movement
 */
package io.materia.physics

// Re-export main engine for backward compatibility
typealias BulletPhysicsEngine = io.materia.physics.bullet.BulletPhysicsEngine
