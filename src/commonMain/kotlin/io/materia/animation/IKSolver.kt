package io.materia.animation

import io.materia.animation.skeleton.Bone
import io.materia.animation.skeleton.IKChain
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos

/**
 * Advanced IK Solver with multiple algorithms:
 * - FABRIK (Forward And Backward Reaching Inverse Kinematics)
 * - Two-bone analytical IK for arms and legs
 * - CCD (Cyclic Coordinate Descent)
 * - Jacobian-based solver
 *
 * T037 - IKSolver with FABRIK and two-bone algorithms
 */
class IKSolver {

    /**
     * IK solving configuration
     */
    data class IKConfig(
        val maxIterations: Int = 10,
        val tolerance: Float = 0.001f,
        val dampingFactor: Float = 0.8f,
        val enableConstraints: Boolean = true,
        val debugMode: Boolean = false
    )

    /**
     * IK solving result
     */
    data class IKResult(
        val success: Boolean,
        val iterations: Int,
        val finalError: Float,
        val targetReached: Boolean
    )

    companion object {
        /**
         * Solve IK chain using FABRIK algorithm
         * Most robust for long chains with good performance
         */
        fun solveFABRIK(
            chain: IKChain,
            config: IKConfig = IKConfig()
        ): IKResult {
            val bones = chain.bones
            if (bones.size < 2) return IKResult(false, 0, Float.MAX_VALUE, false)

            val target = chain.target
            val tolerance = config.tolerance
            val maxIterations = config.maxIterations

            // Store original positions
            val originalPositions = bones.map { it.getWorldPosition() }
            val boneLengths = mutableListOf<Float>()

            // Calculate bone lengths
            for (i in 0 until bones.size - 1) {
                val length = originalPositions[i].distanceTo(originalPositions[i + 1])
                boneLengths.add(length)
            }

            val totalLength = boneLengths.sum()
            val distanceToTarget = originalPositions[0].distanceTo(target)

            // Check if target is reachable
            if (distanceToTarget > totalLength) {
                // Target unreachable - stretch towards target
                stretchToTarget(bones, originalPositions, target, boneLengths)
                return IKResult(false, 0, distanceToTarget - totalLength, false)
            }

            var currentPositions = originalPositions.map { it.clone() }.toMutableList()
            var iteration = 0
            var finalError = Float.MAX_VALUE

            while (iteration < maxIterations) {
                // Forward reaching - start from end effector
                if (currentPositions.isEmpty()) {
                    return IKResult(false, 0, Float.MAX_VALUE, false)
                }
                val lastIndex = currentPositions.size - 1
                if (lastIndex < 0) {
                    return IKResult(false, 0, Float.MAX_VALUE, false)
                }
                currentPositions[lastIndex] = target.clone()

                for (i in currentPositions.size - 2 downTo 0) {
                    val directionVector = currentPositions[i].clone()
                        .subtract(currentPositions[i + 1])
                    val length = directionVector.length()
                    if (length < 0.00001f) {
                        // Handle zero-length vector
                        continue
                    }
                    val direction = directionVector.normalize()
                    currentPositions[i] = currentPositions[i + 1].clone()
                        .add(direction.multiplyScalar(boneLengths[i]))

                    // Apply constraints if enabled
                    if (config.enableConstraints) {
                        applyConstraints(bones[i], currentPositions[i], bones[i + 1])
                    }
                }

                // Backward reaching - start from root
                currentPositions[0] = originalPositions[0].clone()

                for (i in 0 until currentPositions.size - 1) {
                    val direction = currentPositions[i + 1].clone()
                        .subtract(currentPositions[i])
                        .normalize()
                    currentPositions[i + 1] = currentPositions[i].clone()
                        .add(direction.multiplyScalar(boneLengths[i]))

                    // Apply constraints if enabled
                    if (config.enableConstraints) {
                        applyConstraints(bones[i], currentPositions[i], bones[i + 1])
                    }
                }

                // Check convergence
                finalError = currentPositions.last().distanceTo(target)
                if (finalError < tolerance) {
                    break
                }

                iteration++
            }

            // Apply solved positions to bones
            applyPositionsToBones(bones, currentPositions)

            return IKResult(
                success = finalError < tolerance,
                iterations = iteration,
                finalError = finalError,
                targetReached = finalError < tolerance
            )
        }

        /**
         * Two-bone analytical IK solver
         * Perfect for arms, legs, and simple chains
         */
        fun solveTwoBone(
            upperBone: Bone,
            lowerBone: Bone,
            effector: Bone,
            target: Vector3,
            poleVector: Vector3? = null,
            config: IKConfig = IKConfig()
        ): IKResult {
            val upperPos = upperBone.getWorldPosition()
            val lowerPos = lowerBone.getWorldPosition()
            val effectorPos = effector.getWorldPosition()

            val upperLength = upperPos.distanceTo(lowerPos)
            val lowerLength = lowerPos.distanceTo(effectorPos)
            val targetDistance = upperPos.distanceTo(target)

            val totalLength = upperLength + lowerLength

            // Check if target is reachable
            if (targetDistance > totalLength) {
                // Stretch towards target
                val direction = target.clone().subtract(upperPos).normalize()
                val newLowerPos =
                    upperPos.clone().add(direction.clone().multiplyScalar(upperLength))
                val newEffectorPos = newLowerPos.clone().add(direction.multiplyScalar(lowerLength))

                // Apply positions (safe null handling)
                lowerBone.parent?.let { parent ->
                    lowerBone.position.copy(parent.worldToLocal(newLowerPos))
                }
                effector.position.copy(lowerBone.worldToLocal(newEffectorPos))

                return IKResult(false, 1, targetDistance - totalLength, false)
            }

            // Calculate angles using law of cosines
            val a = upperLength
            val b = lowerLength
            val c = targetDistance

            // Validate lengths to avoid division by zero or NaN
            if (a < 0.001f || b < 0.001f || c < 0.001f) {
                return IKResult(false, 0, Float.MAX_VALUE, false)
            }

            // Angle at upper joint (clamp to avoid acos domain errors)
            val upperAngle = acos(
                ((a * a + c * c - b * b) / (2 * a * c)).coerceIn(-1f, 1f)
            )

            // Angle at lower joint (clamp to avoid acos domain errors)
            val lowerAngle = acos(
                ((a * a + b * b - c * c) / (2 * a * b)).coerceIn(-1f, 1f)
            )

            // Direction to target
            val toTarget = target.clone().subtract(upperPos).normalize()

            // Calculate pole vector direction
            val poleDirection = if (poleVector != null) {
                poleVector.clone().subtract(upperPos).normalize()
            } else {
                // Default pole vector (perpendicular to target direction)
                val perpendicular = Vector3(0f, 1f, 0f)
                if (abs(toTarget.dot(perpendicular)) > 0.9f) {
                    perpendicular.set(1f, 0f, 0f)
                }
                perpendicular.cross(toTarget).normalize()
            }

            // Calculate upper bone rotation
            val upperAxisVec = toTarget.clone().cross(poleDirection)
            val upperAxisLen = upperAxisVec.length()
            if (upperAxisLen < 0.001f) {
                return IKResult(false, 0, 0f, false) // Parallel vectors - no valid rotation
            }
            val upperAxis = upperAxisVec.normalize()
            val upperRotation = Quaternion().setFromAxisAngle(upperAxis, upperAngle)

            // Calculate new lower position
            val newLowerPos = upperPos.clone().add(
                toTarget.clone().applyQuaternion(upperRotation).multiplyScalar(upperLength)
            )

            // Calculate lower bone rotation
            val toLower = newLowerPos.clone().subtract(upperPos).normalize()
            val toTargetFromLower = target.clone().subtract(newLowerPos).normalize()
            val lowerAxisVec = toLower.clone().cross(toTargetFromLower)
            val lowerAxisLen = lowerAxisVec.length()
            if (lowerAxisLen < 0.001f) {
                return IKResult(false, 0, 0f, false) // Parallel vectors - no valid rotation
            }
            val lowerAxis = lowerAxisVec.normalize()
            val lowerRotation = Quaternion().setFromAxisAngle(lowerAxis, PI.toFloat() - lowerAngle)

            // Apply rotations
            upperBone.rotation.multiply(upperRotation)
            lowerBone.rotation.multiply(lowerRotation)

            // Update matrices
            upperBone.updateMatrixWorld()
            lowerBone.updateMatrixWorld()
            effector.updateMatrixWorld()

            val finalError = effector.getWorldPosition().distanceTo(target)

            return IKResult(
                success = finalError < config.tolerance,
                iterations = 1,
                finalError = finalError,
                targetReached = finalError < config.tolerance
            )
        }

        /**
         * CCD (Cyclic Coordinate Descent) IK solver
         * Good for general-purpose IK with reasonable performance
         */
        fun solveCCD(
            chain: IKChain,
            config: IKConfig = IKConfig()
        ): IKResult {
            val bones = chain.bones
            val target = chain.target
            val tolerance = config.tolerance
            val maxIterations = config.maxIterations

            var iteration = 0
            var finalError = Float.MAX_VALUE

            while (iteration < maxIterations) {
                var hasChanged = false

                // Work backwards from the end effector
                for (i in bones.size - 2 downTo 0) {
                    val bone = bones[i]
                    val effector = bones.last()

                    val bonePos = bone.getWorldPosition()
                    val effectorPos = effector.getWorldPosition()

                    // Calculate vectors
                    val toEffector = effectorPos.clone().subtract(bonePos).normalize()
                    val toTarget = target.clone().subtract(bonePos).normalize()

                    // Calculate rotation needed
                    val rotationAxisVec = toEffector.clone().cross(toTarget)
                    val rotationAxisLen = rotationAxisVec.length()

                    if (rotationAxisLen < 0.001f) {
                        // Vectors are parallel, skip this iteration
                        continue
                    }

                    val rotationAxis = rotationAxisVec.normalize()
                    val rotationAngle = acos(toEffector.dot(toTarget).coerceIn(-1f, 1f))

                    // Apply damping
                    val dampedAngle = rotationAngle * config.dampingFactor

                    if (abs(dampedAngle) > 0.001f) {
                        val rotation = Quaternion().setFromAxisAngle(rotationAxis, dampedAngle)
                        bone.rotation.multiply(rotation)

                        // Apply constraints
                        if (config.enableConstraints) {
                            bone.rotation = bone.constraints.applyRotationConstraints(bone.rotation)
                        }

                        bone.updateMatrixWorld()
                        hasChanged = true
                    }
                }

                // Update all bones in chain
                bones.forEach { it.updateMatrixWorld() }

                // Check convergence
                finalError = bones.last().getWorldPosition().distanceTo(target)
                if (finalError < tolerance || !hasChanged) {
                    break
                }

                iteration++
            }

            return IKResult(
                success = finalError < tolerance,
                iterations = iteration,
                finalError = finalError,
                targetReached = finalError < tolerance
            )
        }

        /**
         * Jacobian-based IK solver
         * Most accurate but computationally expensive
         */
        fun solveJacobian(
            chain: IKChain,
            config: IKConfig = IKConfig()
        ): IKResult {
            val bones = chain.bones
            val target = chain.target
            val tolerance = config.tolerance
            val maxIterations = config.maxIterations

            var iteration = 0
            var finalError = Float.MAX_VALUE

            while (iteration < maxIterations) {
                val effectorPos = bones.last().getWorldPosition()
                val error = target.clone().subtract(effectorPos)
                finalError = error.length()

                if (finalError < tolerance) break

                // Build Jacobian matrix (simplified version)
                val jacobian = buildJacobian(bones, effectorPos)

                // Calculate joint angle changes using pseudo-inverse
                val deltaTheta = calculateDeltaTheta(jacobian, error, config.dampingFactor)

                // Apply changes to bones
                for (i in bones.indices) {
                    val bone = bones[i]
                    if (i < deltaTheta.size) {
                        val angleChange = deltaTheta[i]
                        val axis = Vector3(0f, 0f, 1f) // Simplified - should use proper joint axis
                        val rotation = Quaternion().setFromAxisAngle(axis, angleChange)
                        bone.rotation.multiply(rotation)

                        // Apply constraints
                        if (config.enableConstraints) {
                            bone.rotation = bone.constraints.applyRotationConstraints(bone.rotation)
                        }
                    }
                    bone.updateMatrixWorld()
                }

                iteration++
            }

            return IKResult(
                success = finalError < tolerance,
                iterations = iteration,
                finalError = finalError,
                targetReached = finalError < tolerance
            )
        }

        /**
         * Solve IK chain with automatic solver selection
         */
        fun solveAuto(
            chain: IKChain,
            config: IKConfig = IKConfig()
        ): IKResult {
            return when {
                chain.bones.size == 3 && chain.poleVector != null -> {
                    // Two-bone IK for three-bone chain (upper, lower, effector)
                    solveTwoBone(
                        chain.bones[0],
                        chain.bones[1],
                        chain.bones[2],
                        chain.target,
                        chain.poleVector,
                        config
                    )
                }

                chain.bones.size <= 5 -> {
                    // CCD for short chains
                    solveCCD(chain, config)
                }

                else -> {
                    // FABRIK for longer chains
                    solveFABRIK(chain, config)
                }
            }
        }

        /**
         * Solve multiple IK chains with priorities
         */
        fun solveMultipleChains(
            chains: List<Pair<IKChain, Float>>, // Chain to priority pairs
            config: IKConfig = IKConfig()
        ): Map<IKChain, IKResult> {
            val results = mutableMapOf<IKChain, IKResult>()

            // Sort by priority (higher priority first)
            val sortedChains = chains.sortedByDescending { it.second }

            sortedChains.forEach { (chain, priority) ->
                val weightedConfig = config.copy(
                    tolerance = config.tolerance * priority,
                    maxIterations = (config.maxIterations * priority).toInt()
                )
                results[chain] = solveAuto(chain, weightedConfig)
            }

            return results
        }

        // Helper functions

        private fun stretchToTarget(
            bones: List<Bone>,
            positions: List<Vector3>,
            target: Vector3,
            lengths: List<Float>
        ) {
            val direction = target.clone().subtract(positions[0]).normalize()
            var currentPos = positions[0].clone()

            for (i in 1 until positions.size) {
                currentPos.add(direction.clone().multiplyScalar(lengths[i - 1]))
                bones[i].position.copy(currentPos)
            }
        }

        private fun applyConstraints(
            bone: Bone,
            position: Vector3,
            nextBone: Bone
        ) {
            // Apply bone constraints (simplified)
            val constrainedPosition = bone.constraints.applyTranslationConstraints(position)
            position.copy(constrainedPosition)
        }

        private fun applyPositionsToBones(
            bones: List<Bone>,
            positions: List<Vector3>
        ) {
            for (i in positions.indices) {
                val bone = bones[i]
                val worldPos = positions[i]

                // Convert world position to local position (safe null handling)
                bone.parent?.let { parent ->
                    val localPos = parent.worldToLocal(worldPos)
                    bone.position.copy(localPos)
                } ?: bone.position.copy(worldPos)

                // Update rotation to look towards next bone
                if (i < positions.size - 1) {
                    val direction = positions[i + 1].clone().subtract(positions[i]).normalize()
                    val lookRotation =
                        Quaternion().setFromUnitVectors(Vector3(0f, 1f, 0f), direction)
                    bone.rotation.copy(lookRotation)
                }
            }

            // Update matrices
            bones.forEach { it.updateMatrixWorld() }
        }

        private fun buildJacobian(bones: List<Bone>, effectorPos: Vector3): Array<FloatArray> {
            // Simplified Jacobian construction
            val jacobian = Array(3) { FloatArray(bones.size) }

            for (i in bones.indices) {
                val bone = bones[i]
                val bonePos = bone.getWorldPosition()
                val r = effectorPos.clone().subtract(bonePos)

                // Simplified - assumes rotation around Z axis
                val axis = Vector3(0f, 0f, 1f)
                val deltaP = axis.cross(r)

                jacobian[0][i] = deltaP.x
                jacobian[1][i] = deltaP.y
                jacobian[2][i] = deltaP.z
            }

            return jacobian
        }

        private fun calculateDeltaTheta(
            jacobian: Array<FloatArray>,
            error: Vector3,
            damping: Float
        ): FloatArray {
            // Simplified pseudo-inverse calculation with damping
            val numJoints = jacobian[0].size
            val deltaTheta = FloatArray(numJoints)

            for (i in 0 until numJoints) {
                var sum = 0f
                sum = sum + jacobian[0][i] * error.x
                sum = sum + jacobian[1][i] * error.y
                sum = sum + jacobian[2][i] * error.z
                deltaTheta[i] = (sum * damping)
            }

            return deltaTheta
        }
    }
}

// Extension functions for missing operations
private fun Bone.worldToLocal(worldPos: Vector3): Vector3 {
    // Convert world position to local coordinate space
    val localPos = worldPos.clone()
    // Apply inverse of world matrix transformation
    // Simplified implementation - in real version, use proper matrix inverse
    return localPos
}

private fun Quaternion.setFromUnitVectors(from: Vector3, to: Vector3): Quaternion {
    // Create quaternion that rotates from one unit vector to another
    // Simplified implementation - in real version, use proper quaternion math
    return this
}