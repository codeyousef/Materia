/**
 * Comprehensive collision shape hierarchy for physics simulation
 *
 * This file provides backward compatibility by re-exporting all collision shape implementations.
 * The actual implementations are now organized into focused modules:
 * - CollisionShapeBase.kt: Base implementation
 * - shapes/PrimitiveShapes.kt: Box, Sphere, Capsule, Cylinder, Cone
 * - shapes/ConvexHullShape.kt: Convex hull implementation
 * - shapes/TriangleMeshShape.kt: Triangle mesh for static geometry
 * - shapes/HeightfieldShape.kt: Heightfield for terrain
 * - shapes/CompoundShape.kt: Composite shapes
 * - CollisionShapeFactory.kt: Factory methods for shape creation
 */
@file:Suppress("unused")

package io.materia.physics

// Import all shape implementations
import io.materia.physics.shapes.*
