/**
 * Rapier Physics Engine Integration for Web Platform
 * Provides high-performance physics simulation using Rapier's WASM bindings
 *
 * This file serves as a public API facade, re-exporting modularized components
 */
package io.materia.physics

// Re-export RAPIER external declarations
// (RAPIER external object is defined in rapier/RAPIER.kt)

// Re-export core components
import io.materia.physics.rapier.RapierPhysicsEngine
import io.materia.physics.rapier.body.RapierRigidBody
import io.materia.physics.rapier.world.RapierPhysicsWorld
import io.materia.physics.rapier.constraints.RapierConstraint

// Re-export shape implementations
import io.materia.physics.rapier.shapes.*

// Platform-specific factory function
fun createRapierPhysicsEngine(): PhysicsEngine = RapierPhysicsEngine()
