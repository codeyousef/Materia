package io.materia.profiling

/**
 * Profiling utilities for physics systems.
 * Instruments collision detection, rigid body simulation, and constraint solving.
 */
object PhysicsProfiler {

    /**
     * Profile physics step
     */
    fun <T> profilePhysicsStep(deltaTime: Float, bodyCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.bodyCount", bodyCount.toLong())
        PerformanceProfiler.recordCounter("physics.deltaTimeMs", (deltaTime * 1000).toLong())
        return PerformanceProfiler.measure("physics.step", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile broad phase collision detection
     */
    fun <T> profileBroadPhase(candidatePairs: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.broadPhase.pairs", candidatePairs.toLong())
        return PerformanceProfiler.measure("physics.broadPhase", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile narrow phase collision detection
     */
    fun <T> profileNarrowPhase(collisionTests: Int, actualCollisions: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.narrowPhase.tests", collisionTests.toLong())
        PerformanceProfiler.recordCounter(
            "physics.narrowPhase.collisions",
            actualCollisions.toLong()
        )
        return PerformanceProfiler.measure("physics.narrowPhase", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile constraint solver
     */
    fun <T> profileConstraintSolver(constraintCount: Int, iterations: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.constraints.count", constraintCount.toLong())
        PerformanceProfiler.recordCounter("physics.constraints.iterations", iterations.toLong())
        return PerformanceProfiler.measure(
            "physics.constraintSolver",
            ProfileCategory.PHYSICS,
            block
        )
    }

    /**
     * Profile integration step
     */
    fun <T> profileIntegration(block: () -> T): T {
        return PerformanceProfiler.measure("physics.integration", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile character controller update
     */
    fun <T> profileCharacterController(block: () -> T): T {
        return PerformanceProfiler.measure(
            "physics.characterController",
            ProfileCategory.PHYSICS,
            block
        )
    }

    /**
     * Profile raycast operation
     */
    fun <T> profileRaycast(bodyCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.raycast.bodies", bodyCount.toLong())
        return PerformanceProfiler.measure("physics.raycast", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile overlap test
     */
    fun <T> profileOverlapTest(block: () -> T): T {
        return PerformanceProfiler.measure("physics.overlapTest", ProfileCategory.PHYSICS, block)
    }

    /**
     * Profile collision response
     */
    fun <T> profileCollisionResponse(contactCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("physics.contacts", contactCount.toLong())
        return PerformanceProfiler.measure(
            "physics.collisionResponse",
            ProfileCategory.PHYSICS,
            block
        )
    }

    /**
     * Analyze physics complexity
     */
    fun analyzePhysicsComplexity(
        bodyCount: Int,
        constraintCount: Int,
        contactCount: Int,
        islandCount: Int = 1
    ): PhysicsComplexity {
        return PerformanceProfiler.measure("physics.analyzeComplexity", ProfileCategory.PHYSICS) {
            val averageContactsPerBody =
                if (bodyCount > 0) contactCount.toFloat() / bodyCount else 0f
            val averageConstraintsPerBody =
                if (bodyCount > 0) constraintCount.toFloat() / bodyCount else 0f

            PhysicsComplexity(
                bodyCount = bodyCount,
                constraintCount = constraintCount,
                contactCount = contactCount,
                islandCount = islandCount,
                averageContactsPerBody = averageContactsPerBody,
                averageConstraintsPerBody = averageConstraintsPerBody
            )
        }
    }
}

/**
 * Physics complexity analysis result
 */
data class PhysicsComplexity(
    val bodyCount: Int,
    val constraintCount: Int,
    val contactCount: Int,
    val islandCount: Int,
    val averageContactsPerBody: Float,
    val averageConstraintsPerBody: Float
) {
    /**
     * Check if physics simulation is considered complex
     */
    fun isComplex(): Boolean {
        return bodyCount > 500 || constraintCount > 1000 || contactCount > 500
    }

    /**
     * Get complexity score (0-10)
     */
    fun getComplexityScore(): Float {
        val bodyScore = (bodyCount / 100f).coerceAtMost(10f)
        val constraintScore = (constraintCount / 200f).coerceAtMost(10f)
        val contactScore = (contactCount / 100f).coerceAtMost(10f)

        return (bodyScore + constraintScore + contactScore) / 3f
    }

    /**
     * Get optimization recommendations
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (bodyCount > 1000) {
            recommendations.add("Consider spatial partitioning or sleeping bodies (current: $bodyCount bodies)")
        }

        if (constraintCount > 2000) {
            recommendations.add("High constraint count ($constraintCount) may impact performance")
        }

        if (contactCount > 500) {
            recommendations.add("Many contacts ($contactCount) - consider simplifying collision shapes")
        }

        if (averageContactsPerBody > 10) {
            recommendations.add(
                "High average contacts per body (${
                    io.materia.core.platform.formatFloat(
                        averageContactsPerBody,
                        1
                    )
                }) - check geometry complexity"
            )
        }

        if (islandCount > 50) {
            recommendations.add("Many physics islands ($islandCount) - consider merging static geometry")
        }

        return recommendations
    }

    /**
     * Estimate CPU usage percentage
     */
    fun estimateCPUUsage(): Float {
        // Rough estimate based on complexity
        val baselineUsage = 5f // 5% baseline for physics
        val bodyUsage = (bodyCount / 100f) * 2f
        val constraintUsage = (constraintCount / 200f) * 3f
        val contactUsage = (contactCount / 100f) * 2f

        return (baselineUsage + bodyUsage + constraintUsage + contactUsage).coerceAtMost(100f)
    }
}
