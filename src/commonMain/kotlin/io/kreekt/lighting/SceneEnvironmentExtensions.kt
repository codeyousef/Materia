package io.kreekt.lighting

import io.kreekt.core.scene.Scene
import io.kreekt.lighting.ibl.IBLEnvironmentMaps

/**
 * Applies the generated environment maps to a scene so renderers can bind
 * the prefiltered cubemap and BRDF lookup texture.
 */
fun Scene.applyEnvironmentMaps(maps: IBLEnvironmentMaps) {
    environment = maps.prefilter
    environmentBrdfLut = maps.brdfLut
}
