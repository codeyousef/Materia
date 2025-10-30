package io.materia.profiling

import io.materia.core.scene.Object3D
import io.materia.core.scene.Scene
import io.materia.core.math.Matrix4

/**
 * Profiling utilities for scene graph operations.
 * Instruments hierarchy traversal, matrix updates, and transform propagation.
 */
object SceneProfiler {

    /**
     * Profile scene traversal with detailed timing per node type
     */
    fun profileTraversal(scene: Scene, callback: (Object3D) -> Unit) {
        PerformanceProfiler.measure("scene.traverse", ProfileCategory.SCENE_GRAPH) {
            var nodeCount = 0
            var meshCount = 0
            var lightCount = 0
            var cameraCount = 0

            scene.traverse { obj ->
                nodeCount++

                // Count by type
                when {
                    obj.type.contains("Mesh") -> meshCount++
                    obj.type.contains("Light") -> lightCount++
                    obj.type.contains("Camera") -> cameraCount++
                }

                callback(obj)
            }

            // Record counters
            PerformanceProfiler.recordCounter("scene.nodeCount", nodeCount.toLong())
            PerformanceProfiler.recordCounter("scene.meshCount", meshCount.toLong())
            PerformanceProfiler.recordCounter("scene.lightCount", lightCount.toLong())
            PerformanceProfiler.recordCounter("scene.cameraCount", cameraCount.toLong())
        }
    }

    /**
     * Profile matrix world updates
     */
    fun profileMatrixWorldUpdate(obj: Object3D, force: Boolean = false) {
        PerformanceProfiler.measure("scene.updateMatrixWorld", ProfileCategory.MATRIX) {
            obj.updateMatrixWorld(force)
        }
    }

    /**
     * Profile local matrix update
     */
    fun profileMatrixUpdate(obj: Object3D) {
        PerformanceProfiler.measure("scene.updateMatrix", ProfileCategory.MATRIX) {
            obj.updateMatrix()
        }
    }

    /**
     * Profile hierarchy operations
     */
    fun profileAdd(parent: Object3D, children: Array<out Object3D>) {
        PerformanceProfiler.measure("scene.add", ProfileCategory.SCENE_GRAPH) {
            parent.add(*children)
        }
        PerformanceProfiler.incrementCounter("scene.addOperations")
    }

    fun profileRemove(parent: Object3D, children: Array<out Object3D>) {
        PerformanceProfiler.measure("scene.remove", ProfileCategory.SCENE_GRAPH) {
            parent.remove(*children)
        }
        PerformanceProfiler.incrementCounter("scene.removeOperations")
    }

    /**
     * Profile bounding box calculations
     */
    fun <T> profileBoundingBox(obj: Object3D, block: () -> T): T {
        return PerformanceProfiler.measure(
            "scene.getBoundingBox",
            ProfileCategory.SCENE_GRAPH,
            block
        )
    }

    /**
     * Profile world transformation extraction
     */
    fun profileWorldPosition(obj: Object3D) {
        PerformanceProfiler.measure("scene.getWorldPosition", ProfileCategory.MATRIX) {
            obj.getWorldPosition()
        }
    }

    fun profileWorldQuaternion(obj: Object3D) {
        PerformanceProfiler.measure("scene.getWorldQuaternion", ProfileCategory.MATRIX) {
            obj.getWorldQuaternion()
        }
    }

    fun profileWorldScale(obj: Object3D) {
        PerformanceProfiler.measure("scene.getWorldScale", ProfileCategory.MATRIX) {
            obj.getWorldScale()
        }
    }

    /**
     * Profile look-at operations
     */
    fun profileLookAt(obj: Object3D, block: () -> Unit) {
        PerformanceProfiler.measure("scene.lookAt", ProfileCategory.MATRIX, block)
    }

    /**
     * Analyze scene complexity
     */
    fun analyzeSceneComplexity(scene: Scene): SceneComplexity {
        var totalNodes = 0
        var maxDepth = 0
        var totalChildren = 0
        val depthCounts = mutableMapOf<Int, Int>()

        fun traverseWithDepth(obj: Object3D, depth: Int) {
            totalNodes++
            maxDepth = maxOf(maxDepth, depth)
            depthCounts[depth] = (depthCounts[depth] ?: 0) + 1
            totalChildren += obj.children.size

            obj.children.forEach { child ->
                traverseWithDepth(child, depth + 1)
            }
        }

        PerformanceProfiler.measure("scene.analyzeComplexity", ProfileCategory.SCENE_GRAPH) {
            traverseWithDepth(scene, 0)
        }

        val averageChildrenPerNode = if (totalNodes > 0) {
            totalChildren.toFloat() / totalNodes
        } else 0f

        return SceneComplexity(
            totalNodes = totalNodes,
            maxDepth = maxDepth,
            averageChildrenPerNode = averageChildrenPerNode,
            depthDistribution = depthCounts
        )
    }
}

/**
 * Scene complexity analysis result
 */
data class SceneComplexity(
    val totalNodes: Int,
    val maxDepth: Int,
    val averageChildrenPerNode: Float,
    val depthDistribution: Map<Int, Int>
) {
    fun isComplex(): Boolean {
        return totalNodes > 1000 || maxDepth > 10
    }

    fun getComplexityScore(): Float {
        // Simple complexity scoring
        val nodeScore = (totalNodes / 100f).coerceAtMost(10f)
        val depthScore = (maxDepth / 2f).coerceAtMost(10f)
        val childScore = (averageChildrenPerNode * 2f).coerceAtMost(10f)

        return (nodeScore + depthScore + childScore) / 3f
    }
}

/**
 * Extension functions for profiled scene operations
 */

/**
 * Traverse with profiling
 */
fun Scene.traverseProfiled(callback: (Object3D) -> Unit) {
    SceneProfiler.profileTraversal(this, callback)
}

/**
 * Update world matrix with profiling
 */
fun Object3D.updateMatrixWorldProfiled(force: Boolean = false) {
    SceneProfiler.profileMatrixWorldUpdate(this, force)
}

/**
 * Update matrix with profiling
 */
fun Object3D.updateMatrixProfiled() {
    SceneProfiler.profileMatrixUpdate(this)
}

/**
 * Get scene complexity
 */
fun Scene.getComplexity(): SceneComplexity {
    return SceneProfiler.analyzeSceneComplexity(this)
}
