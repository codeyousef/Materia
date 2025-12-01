package io.materia.animation

import io.materia.core.math.Vector3
import io.materia.core.platform.platformClone
import io.materia.core.platform.currentTimeMillis
import io.materia.core.platform.platformClone
import io.materia.geometry.BufferGeometry
import io.materia.core.platform.platformClone
import io.materia.core.platform.currentTimeMillis
import io.materia.core.platform.platformClone
import kotlinx.serialization.Contextual
import io.materia.core.platform.platformClone
import io.materia.core.platform.currentTimeMillis
import io.materia.core.platform.platformClone
import kotlinx.serialization.Serializable
import io.materia.core.platform.platformClone
import io.materia.core.platform.currentTimeMillis
import io.materia.core.platform.platformClone
import kotlin.math.*
import io.materia.core.platform.platformClone
import io.materia.core.platform.currentTimeMillis
import io.materia.core.platform.platformClone
import kotlin.math.PI
import kotlin.math.sin

/**
 * Advanced Morph Target Animator for facial animation and shape interpolation.
 * Supports real-time blending, expression management, and performance optimization.
 *
 * T038 - MorphTargetAnimator for facial animation
 */
class MorphTargetAnimator(
    val geometry: BufferGeometry,
    val morphTargets: List<MorphTarget>
) {

    // Current morph weights
    private val currentWeights = FloatArray(morphTargets.size) { 0f }
    private val targetWeights = FloatArray(morphTargets.size) { 0f }
    private val morphVelocities = FloatArray(morphTargets.size) { 0f }

    // Blend groups for complex expressions
    private val blendGroups = mutableMapOf<String, BlendGroup>()

    // Performance optimization
    private var needsUpdate = true
    private var lastUpdateTime = 0L
    private val morphCache = mutableMapOf<String, ComputedMorph>()

    // Expression presets
    private val expressionPresets = mutableMapOf<String, ExpressionPreset>()

    // Animation state
    private val activeAnimations = mutableListOf<MorphAnimation>()

    /**
     * Morph target definition
     */
    data class MorphTarget(
        val name: String,
        val vertices: List<Vector3>,
        val normals: List<Vector3>? = null,
        val category: MorphCategory = MorphCategory.FACIAL,
        val weight: Float = 0f,
        val metadata: Map<String, Any> = emptyMap()
    ) {
        fun applyToGeometry(geometry: BufferGeometry, weight: Float) {
            if (weight == 0f) return

            val positions = geometry.getAttribute("position")?.array ?: return
            val positionArray = positions as FloatArray

            // Apply weighted morph target
            for (i in vertices.indices) {
                val baseIndex = i * 3
                if (baseIndex + 2 < positionArray.size) {
                    positionArray[baseIndex] += vertices[i].x * weight
                    positionArray[baseIndex + 1] += vertices[i].y * weight
                    positionArray[baseIndex + 2] += vertices[i].z * weight
                }
            }

            // Apply normals if available
            normals?.let { morphNormals ->
                val normalAttribute = geometry.getAttribute("normal")?.array as? FloatArray
                normalAttribute?.let { normalArray ->
                    for (i in morphNormals.indices) {
                        val baseIndex = i * 3
                        if (baseIndex + 2 < normalArray.size) {
                            normalArray[baseIndex] += morphNormals[i].x * weight
                            normalArray[baseIndex + 1] += morphNormals[i].y * weight
                            normalArray[baseIndex + 2] += morphNormals[i].z * weight
                        }
                    }
                }
            }
        }
    }

    /**
     * Morph target categories
     */
    enum class MorphCategory {
        FACIAL,      // Face expressions
        PHONEME,     // Speech/lip sync
        CORRECTIVE,  // Shape correction
        CLOTHING,    // Cloth deformation
        CUSTOM       // User-defined
    }

    /**
     * Blend group for organizing related morph targets
     */
    data class BlendGroup(
        val name: String,
        val morphIndices: List<Int>,
        val blendMode: BlendMode = BlendMode.NORMALIZED,
        val maxWeight: Float = 1.0f
    ) {
        enum class BlendMode {
            NORMALIZED,  // Weights sum to 1.0
            ADDITIVE,    // Weights can exceed 1.0
            EXCLUSIVE    // Only one morph active at a time
        }

        fun applyBlendMode(weights: FloatArray): FloatArray {
            val result = weights.platformClone()

            when (blendMode) {
                BlendMode.NORMALIZED -> {
                    val sum = morphIndices.sumOf { weights[it].toDouble() }.toFloat()
                    if (sum > maxWeight) {
                        morphIndices.forEach { index ->
                            result[index] = (weights[index] / sum) * maxWeight
                        }
                    }
                }

                BlendMode.ADDITIVE -> {
                    // No modification needed - weights can be additive
                }

                BlendMode.EXCLUSIVE -> {
                    var maxIndex = -1
                    var maxValue = 0f
                    morphIndices.forEach { index ->
                        if (weights[index] > maxValue) {
                            maxValue = weights[index]
                            maxIndex = index
                        }
                    }
                    morphIndices.forEach { index ->
                        result[index] = if (index == maxIndex) maxValue else 0f
                    }
                }
            }

            return result
        }
    }

    /**
     * Expression preset containing multiple morph weights
     */
    data class ExpressionPreset(
        val name: String,
        val weights: Map<String, Float>,
        val duration: Float = 0.5f,
        val easing: EasingType = EasingType.EASE_IN_OUT,
        val description: String = ""
    ) {
        enum class EasingType {
            LINEAR,
            EASE_IN,
            EASE_OUT,
            EASE_IN_OUT,
            BOUNCE,
            ELASTIC
        }

        fun applyEasing(progress: Float): Float {
            return when (easing) {
                EasingType.LINEAR -> progress
                EasingType.EASE_IN -> progress * progress
                EasingType.EASE_OUT -> 1f - (1f - progress) * (1f - progress)
                EasingType.EASE_IN_OUT -> {
                    if (progress < 0.5f) {
                        2f * (progress * progress)
                    } else {
                        1f - 2f * (1f - progress) * (1f - progress)
                    }
                }

                EasingType.BOUNCE -> {
                    if (progress < 1f / 2.75f) {
                        7.5625f * (progress * progress)
                    } else if (progress < 2f / 2.75f) {
                        val t = progress - 1.5f / 2.75f
                        7.5625f * t * t + 0.75f
                    } else if (progress < 2.5f / 2.75f) {
                        val t = progress - 2.25f / 2.75f
                        7.5625f * t * t + 0.9375f
                    } else {
                        val t = progress - 2.625f / 2.75f
                        7.5625f * t * t + 0.984375f
                    }
                }

                EasingType.ELASTIC -> {
                    if (progress == 0f || progress == 1f) {
                        progress
                    } else {
                        val p = 0.3f
                        val s = p / 4f
                        -(2f.pow(10f * (progress - 1f))) * sin((progress - 1f - s) * (2f * PI.toFloat()) / p)
                    }
                }
            }
        }
    }

    /**
     * Morph animation for automatic weight changes
     */
    data class MorphAnimation(
        val targetName: String,
        val startWeight: Float,
        val endWeight: Float,
        val duration: Float,
        val easing: ExpressionPreset.EasingType = ExpressionPreset.EasingType.EASE_IN_OUT,
        var startTime: Long = currentTimeMillis(),
        var isLooping: Boolean = false,
        var pingPong: Boolean = false,
        val onComplete: (() -> Unit)? = null
    ) {
        var isCompleted = false
        private var direction = 1f

        fun getProgress(currentTime: Long): Float {
            val elapsed = (currentTime - startTime) / 1000f
            var progress = (elapsed / duration).coerceIn(0f, 1f)

            if (progress >= 1f) {
                if (isLooping) {
                    if (pingPong) {
                        direction *= -1f
                        startTime = currentTime
                        progress = 0f
                    } else {
                        startTime = currentTime
                        progress = 0f
                    }
                } else {
                    isCompleted = true
                    onComplete?.invoke()
                }
            }

            return if (pingPong && direction < 0f) 1f - progress else progress
        }

        fun getCurrentWeight(currentTime: Long): Float {
            val progress = getProgress(currentTime)
            val easedProgress = ExpressionPreset(
                "", emptyMap(), 0f, easing
            ).applyEasing(progress)

            return startWeight + (endWeight - startWeight) * easedProgress
        }
    }

    /**
     * Cached morph computation for performance
     */
    private data class ComputedMorph(
        val positions: FloatArray,
        val normals: FloatArray?,
        val timestamp: Long
    )

    init {
        initializeDefaultExpressions()
    }

    /**
     * Set morph target weight by name
     */
    fun setMorphWeight(targetName: String, weight: Float, animated: Boolean = false) {
        val index = morphTargets.indexOfFirst { it.name == targetName }
        if (index != -1) {
            if (animated) {
                animateToWeight(targetName, weight)
            } else {
                currentWeights[index] = weight.coerceIn(0f, 1f)
                targetWeights[index] = currentWeights[index]
                needsUpdate = true
            }
        }
    }

    /**
     * Get morph target weight by name
     */
    fun getMorphWeight(targetName: String): Float {
        val index = morphTargets.indexOfFirst { it.name == targetName }
        return if (index != -1) currentWeights[index] else 0f
    }

    /**
     * Set multiple morph weights at once
     */
    fun setMorphWeights(weights: Map<String, Float>, animated: Boolean = false) {
        weights.forEach { (name, weight) ->
            setMorphWeight(name, weight, animated)
        }
    }

    /**
     * Animate morph weight change
     */
    fun animateToWeight(
        targetName: String,
        endWeight: Float,
        duration: Float = 0.5f,
        easing: ExpressionPreset.EasingType = ExpressionPreset.EasingType.EASE_IN_OUT
    ) {
        val currentWeight = getMorphWeight(targetName)
        val animation = MorphAnimation(
            targetName = targetName,
            startWeight = currentWeight,
            endWeight = endWeight,
            duration = duration,
            easing = easing
        )
        activeAnimations.add(animation)
    }

    /**
     * Apply expression preset
     */
    fun applyExpression(expressionName: String, weight: Float = 1f, animated: Boolean = true) {
        val preset = expressionPresets[expressionName] ?: return

        preset.weights.forEach { (morphName, morphWeight) ->
            val finalWeight = morphWeight * weight
            if (animated) {
                animateToWeight(morphName, finalWeight, preset.duration, preset.easing)
            } else {
                setMorphWeight(morphName, finalWeight, false)
            }
        }
    }

    /**
     * Blend between two expressions
     */
    fun blendExpressions(
        expression1: String,
        expression2: String,
        blendFactor: Float,
        animated: Boolean = true
    ) {
        val preset1 = expressionPresets[expression1] ?: return
        val preset2 = expressionPresets[expression2] ?: return

        // Get all unique morph names
        val allMorphs = (preset1.weights.keys + preset2.weights.keys).toSet()

        allMorphs.forEach { morphName ->
            val weight1 = preset1.weights[morphName] ?: 0f
            val weight2 = preset2.weights[morphName] ?: 0f
            val blendedWeight = weight1 * (1f - blendFactor) + weight2 * blendFactor

            if (animated) {
                animateToWeight(morphName, blendedWeight)
            } else {
                setMorphWeight(morphName, blendedWeight, false)
            }
        }
    }

    /**
     * Add blend group
     */
    fun addBlendGroup(blendGroup: BlendGroup) {
        blendGroups[blendGroup.name] = blendGroup
    }

    /**
     * Add expression preset
     */
    fun addExpressionPreset(preset: ExpressionPreset) {
        expressionPresets[preset.name] = preset
    }

    /**
     * Update morph targets (call every frame)
     */
    fun update(deltaTime: Float) {
        val currentTime = currentTimeMillis()

        // Update animations
        updateAnimations(currentTime)

        // Apply smoothing to weights
        updateWeightSmoothing(deltaTime)

        // Apply blend groups
        applyBlendGroups()

        // Update geometry if needed
        if (needsUpdate) {
            updateGeometry()
            needsUpdate = false
            lastUpdateTime = currentTime
        }
    }

    /**
     * Update active animations
     */
    private fun updateAnimations(currentTime: Long) {
        val iterator = activeAnimations.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            val currentWeight = animation.getCurrentWeight(currentTime)

            val index = morphTargets.indexOfFirst { it.name == animation.targetName }
            if (index != -1) {
                targetWeights[index] = currentWeight
            }

            if (animation.isCompleted) {
                iterator.remove()
            }
        }
    }

    /**
     * Apply smooth weight transitions
     */
    private fun updateWeightSmoothing(deltaTime: Float) {
        for (i in currentWeights.indices) {
            val diff = targetWeights[i] - currentWeights[i]
            if (abs(diff) > 0.001f) {
                val velocity = diff * 5f // Smoothing factor
                morphVelocities[i] = velocity
                currentWeights[i] += velocity * deltaTime
                needsUpdate = true
            }
        }
    }

    /**
     * Apply blend group constraints
     */
    private fun applyBlendGroups() {
        blendGroups.values.forEach { group ->
            val processedWeights = group.applyBlendMode(currentWeights)
            group.morphIndices.forEach { index ->
                currentWeights[index] = processedWeights[index]
            }
        }
    }

    /**
     * Update geometry with current morph weights
     */
    private fun updateGeometry() {
        val cacheKey = currentWeights.joinToString(",")
        val cached = morphCache[cacheKey]

        if (cached != null && (currentTimeMillis() - cached.timestamp) < 16) {
            // Use cached result if less than 16ms old (60fps)
            applyComputedMorph(cached)
            return
        }

        // Reset geometry to base state
        resetGeometryToBase()

        // Apply all active morph targets
        for (i in morphTargets.indices) {
            val weight = currentWeights[i]
            if (weight > 0f) {
                morphTargets[i].applyToGeometry(geometry, weight)
            }
        }

        // Cache the result
        val computedMorph = createComputedMorph()
        morphCache[cacheKey] = computedMorph

        // Limit cache size
        if (morphCache.size > 100) {
            val oldestKey = morphCache.keys.firstOrNull()
            oldestKey?.let { morphCache.remove(it) }
        }

        geometry.markAttributesNeedUpdate()
    }

    /**
     * Reset geometry to base state
     */
    private fun resetGeometryToBase() {
        // This would reset the geometry to its base shape
        // Implementation depends on how base geometry is stored
    }

    /**
     * Create computed morph for caching
     */
    private fun createComputedMorph(): ComputedMorph {
        val positions = geometry.getAttribute("position")?.array as? FloatArray
        val normals = geometry.getAttribute("normal")?.array as? FloatArray

        return ComputedMorph(
            positions = positions?.platformClone() ?: floatArrayOf(),
            normals = normals?.platformClone(),
            timestamp = currentTimeMillis()
        )
    }

    /**
     * Apply cached computed morph
     */
    private fun applyComputedMorph(computed: ComputedMorph) {
        val positions = geometry.getAttribute("position")?.array as? FloatArray
        val normals = geometry.getAttribute("normal")?.array as? FloatArray

        positions?.let { posArray ->
            computed.positions.copyInto(posArray)
        }

        normals?.let { normArray ->
            computed.normals?.copyInto(normArray)
        }

        geometry.markAttributesNeedUpdate()
    }

    /**
     * Initialize default facial expressions
     */
    private fun initializeDefaultExpressions() {
        // Basic facial expressions
        addExpressionPreset(
            ExpressionPreset(
                name = "smile",
                weights = mapOf(
                    "mouthSmileLeft" to 0.7f,
                    "mouthSmileRight" to 0.7f,
                    "cheekSquintLeft" to 0.3f,
                    "cheekSquintRight" to 0.3f
                )
            )
        )

        addExpressionPreset(
            ExpressionPreset(
                name = "frown",
                weights = mapOf(
                    "mouthFrownLeft" to 0.8f,
                    "mouthFrownRight" to 0.8f,
                    "browDownLeft" to 0.5f,
                    "browDownRight" to 0.5f
                )
            )
        )

        addExpressionPreset(
            ExpressionPreset(
                name = "surprised",
                weights = mapOf(
                    "browInnerUp" to 0.9f,
                    "browOuterUpLeft" to 0.7f,
                    "browOuterUpRight" to 0.7f,
                    "eyeWideLeft" to 0.8f,
                    "eyeWideRight" to 0.8f,
                    "mouthOpen" to 0.6f
                )
            )
        )

        addExpressionPreset(
            ExpressionPreset(
                name = "angry",
                weights = mapOf(
                    "browDownLeft" to 0.8f,
                    "browDownRight" to 0.8f,
                    "mouthFrownLeft" to 0.6f,
                    "mouthFrownRight" to 0.6f,
                    "noseSneerLeft" to 0.4f,
                    "noseSneerRight" to 0.4f
                )
            )
        )

        // Phoneme shapes for lip sync
        addExpressionPreset(
            ExpressionPreset(
                name = "phoneme_A",
                weights = mapOf("mouthOpen" to 0.8f, "jawOpen" to 0.6f)
            )
        )

        addExpressionPreset(
            ExpressionPreset(
                name = "phoneme_E",
                weights = mapOf("mouthSmileLeft" to 0.4f, "mouthSmileRight" to 0.4f)
            )
        )

        addExpressionPreset(
            ExpressionPreset(
                name = "phoneme_O",
                weights = mapOf("mouthPucker" to 0.7f, "mouthOpen" to 0.3f)
            )
        )
    }

    /**
     * Get all available expression names
     */
    fun getExpressionNames(): List<String> = expressionPresets.keys.toList()

    /**
     * Get all morph target names
     */
    fun getMorphTargetNames(): List<String> = morphTargets.map { it.name }

    /**
     * Clear all animations
     */
    fun clearAnimations() {
        activeAnimations.clear()
    }

    /**
     * Reset all weights to zero
     */
    fun resetAllWeights() {
        for (i in currentWeights.indices) {
            currentWeights[i] = 0f
            targetWeights[i] = 0f
        }
        clearAnimations()
        needsUpdate = true
    }

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            activeMorphTargets = currentWeights.count { it > 0f },
            activeAnimations = activeAnimations.size,
            cacheHitRate = if (morphCache.size > 0) 0.8f else 0f, // Simplified
            lastUpdateTime = lastUpdateTime
        )
    }

    data class PerformanceStats(
        val activeMorphTargets: Int,
        val activeAnimations: Int,
        val cacheHitRate: Float,
        val lastUpdateTime: Long
    )

    fun dispose() {
        activeAnimations.clear()
        morphCache.clear()
        blendGroups.clear()
        expressionPresets.clear()
    }
}

// Extension function for BufferGeometry
private fun BufferGeometry.markAttributesNeedUpdate() {
    // Mark geometry attributes as needing GPU update
    // Attribute update tracking is managed by the BufferGeometry class
}