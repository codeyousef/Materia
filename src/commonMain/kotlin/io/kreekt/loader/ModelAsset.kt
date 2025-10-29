package io.kreekt.loader

import io.kreekt.animation.AnimationClip
import io.kreekt.core.scene.Material
import io.kreekt.core.scene.Scene

/**
 * Represents a fully loaded 3D model.
 */
data class ModelAsset(
    val scene: Scene,
    val materials: List<Material> = emptyList(),
    val animations: List<AnimationClip> = emptyList()
)
