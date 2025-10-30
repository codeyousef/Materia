package io.materia.helper

import io.materia.core.scene.Object3D
import io.materia.core.math.Color

/**
 * Base class for visual debugging helpers
 */
abstract class Helper : Object3D() {
    var color: Color = Color(0xffffff)
}