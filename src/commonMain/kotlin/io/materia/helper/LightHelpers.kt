package io.materia.helper

import io.materia.camera.Camera
import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.light.DirectionalLight
import io.materia.light.HemisphereLight
import io.materia.light.PointLight
import io.materia.light.SpotLight

/**
 * Light and Camera helper implementations
 * Visual debugging for lights and camera frustums
 */

/**
 * CameraHelper - Visualizes camera frustum
 */
class CameraHelper(val camera: Camera) : Object3D() {
    var geometry: BufferGeometry? = null
        private set

    val pointMap = mutableMapOf<String, Vector3>()

    init {
        // Create frustum geometry
        geometry = BufferGeometry()

        // Create frustum corner points
        pointMap["n1"] = Vector3(-1f, -1f, -1f) // near bottom-left
        pointMap["n2"] = Vector3(1f, -1f, -1f)  // near bottom-right
        pointMap["n3"] = Vector3(1f, 1f, -1f)   // near top-right
        pointMap["n4"] = Vector3(-1f, 1f, -1f)  // near top-left
        pointMap["f1"] = Vector3(-1f, -1f, 1f)  // far bottom-left
        pointMap["f2"] = Vector3(1f, -1f, 1f)   // far bottom-right
        pointMap["f3"] = Vector3(1f, 1f, 1f)    // far top-right
        pointMap["f4"] = Vector3(-1f, 1f, 1f)   // far top-left
        pointMap["c"] = Vector3(0f, 0f, 0f)     // camera position

        // Create line segments for frustum edges
        val positions = mutableListOf<Float>()

        // Near plane
        pointMap["n1"]?.let { n1 -> pointMap["n2"]?.let { n2 -> addLine(positions, n1, n2) } }
        pointMap["n2"]?.let { n2 -> pointMap["n3"]?.let { n3 -> addLine(positions, n2, n3) } }
        pointMap["n3"]?.let { n3 -> pointMap["n4"]?.let { n4 -> addLine(positions, n3, n4) } }
        pointMap["n4"]?.let { n4 -> pointMap["n1"]?.let { n1 -> addLine(positions, n4, n1) } }

        // Far plane
        pointMap["f1"]?.let { f1 -> pointMap["f2"]?.let { f2 -> addLine(positions, f1, f2) } }
        pointMap["f2"]?.let { f2 -> pointMap["f3"]?.let { f3 -> addLine(positions, f2, f3) } }
        pointMap["f3"]?.let { f3 -> pointMap["f4"]?.let { f4 -> addLine(positions, f3, f4) } }
        pointMap["f4"]?.let { f4 -> pointMap["f1"]?.let { f1 -> addLine(positions, f4, f1) } }

        // Connecting lines
        pointMap["n1"]?.let { n1 -> pointMap["f1"]?.let { f1 -> addLine(positions, n1, f1) } }
        pointMap["n2"]?.let { n2 -> pointMap["f2"]?.let { f2 -> addLine(positions, n2, f2) } }
        pointMap["n3"]?.let { n3 -> pointMap["f3"]?.let { f3 -> addLine(positions, n3, f3) } }
        pointMap["n4"]?.let { n4 -> pointMap["f4"]?.let { f4 -> addLine(positions, n4, f4) } }

        geometry?.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))

        name = "CameraHelper"
    }

    private fun addLine(positions: MutableList<Float>, start: Vector3, end: Vector3) {
        positions.add(start.x)
        positions.add(start.y)
        positions.add(start.z)
        positions.add(end.x)
        positions.add(end.y)
        positions.add(end.z)
    }

    fun update() {
        // Update frustum based on camera changes
        geometry?.getAttribute("position")?.needsUpdate = true
    }

    fun dispose() {
        geometry = null
    }
}

/**
 * DirectionalLightHelper - Visualizes directional light
 */
class DirectionalLightHelper(
    val light: DirectionalLight,
    val size: Float = 1f,
    val color: Color? = null
) : Object3D() {
    var geometry: BufferGeometry? = null
        private set

    init {
        // Create visual representation
        geometry = BufferGeometry()

        val positions = mutableListOf<Float>()

        // Create arrow showing light direction
        positions.add(0f)
        positions.add(0f)
        positions.add(0f)
        positions.add(0f)
        positions.add(0f)
        positions.add(-size)

        // Cross at base
        positions.add(-size * 0.2f)
        positions.add(0f)
        positions.add(0f)
        positions.add(size * 0.2f)
        positions.add(0f)
        positions.add(0f)

        geometry?.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))

        name = "DirectionalLightHelper"
    }

    fun update() {
        // Update helper visualization based on light changes
        geometry?.getAttribute("position")?.needsUpdate = true
    }

    fun dispose() {
        geometry = null
    }
}

/**
 * PointLightHelper - Visualizes point light
 */
class PointLightHelper(
    val light: PointLight,
    val sphereSize: Float = 1f,
    val color: Color? = null
) : Object3D() {
    var geometry: BufferGeometry? = null
        private set

    init {
        // Create sphere wireframe
        geometry = BufferGeometry()
        val positions = mutableListOf<Float>()
        val segments = 16

        // Horizontal circle
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f
            val angle2 = ((i + 1).toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f

            positions.add(kotlin.math.cos(angle1) * sphereSize)
            positions.add(0f)
            positions.add(kotlin.math.sin(angle1) * sphereSize)

            positions.add(kotlin.math.cos(angle2) * sphereSize)
            positions.add(0f)
            positions.add(kotlin.math.sin(angle2) * sphereSize)
        }

        geometry?.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))

        name = "PointLightHelper"
    }

    fun update() {
        geometry?.getAttribute("position")?.needsUpdate = true
    }

    fun dispose() {
        geometry = null
    }
}

/**
 * SpotLightHelper - Visualizes spot light
 */
class SpotLightHelper(
    val light: SpotLight,
    val color: Color? = null
) : Object3D() {
    var geometry: BufferGeometry? = null
        private set

    init {
        // Create cone
        geometry = BufferGeometry()
        val positions = mutableListOf<Float>()

        val distance = light.distance.takeIf { it > 0 } ?: 100f
        val radius = kotlin.math.tan(light.angle) * distance
        val segments = 16

        // Cone from apex to base circle
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f
            val x = kotlin.math.cos(angle) * radius
            val y = kotlin.math.sin(angle) * radius
            val z = -distance

            positions.add(0f)
            positions.add(0f)
            positions.add(0f)
            positions.add(x)
            positions.add(y)
            positions.add(z)
        }

        geometry?.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))

        name = "SpotLightHelper"
    }

    fun update() {
        // Recreate geometry with current light parameters
        geometry = null
        geometry = BufferGeometry()
        // Rebuild geometry...
        geometry?.getAttribute("position")?.needsUpdate = true
    }

    fun dispose() {
        geometry = null
    }
}

/**
 * HemisphereLightHelper - Visualizes hemisphere light
 */
class HemisphereLightHelper(
    val light: HemisphereLight,
    val size: Float = 1f
) : Object3D() {
    var geometry: BufferGeometry? = null
        private set

    init {
        // Create split sphere
        geometry = BufferGeometry()
        val positions = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        val segments = 16

        // Upper hemisphere (sky color)
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f
            val angle2 = ((i + 1).toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f

            positions.add(kotlin.math.cos(angle1) * size)
            positions.add(size * 0.5f)
            positions.add(kotlin.math.sin(angle1) * size)
            positions.add(kotlin.math.cos(angle2) * size)
            positions.add(size * 0.5f)
            positions.add(kotlin.math.sin(angle2) * size)

            // Sky color
            colors.add(light.color.r)
            colors.add(light.color.g)
            colors.add(light.color.b)
            colors.add(light.color.r)
            colors.add(light.color.g)
            colors.add(light.color.b)
        }

        // Lower hemisphere (ground color)
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f
            val angle2 = ((i + 1).toFloat() / segments.toFloat()) * kotlin.math.PI.toFloat() * 2f

            positions.add(kotlin.math.cos(angle1) * size)
            positions.add(-size * 0.5f)
            positions.add(kotlin.math.sin(angle1) * size)
            positions.add(kotlin.math.cos(angle2) * size)
            positions.add(-size * 0.5f)
            positions.add(kotlin.math.sin(angle2) * size)

            // Ground color
            colors.add(light.groundColor.r)
            colors.add(light.groundColor.g)
            colors.add(light.groundColor.b)
            colors.add(light.groundColor.r)
            colors.add(light.groundColor.g)
            colors.add(light.groundColor.b)
        }

        geometry?.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))
        geometry?.setAttribute("color", BufferAttribute(colors.toFloatArray(), 3))

        name = "HemisphereLightHelper"
    }

    fun update() {
        // Recreate geometry with current light colors
        geometry?.getAttribute("position")?.needsUpdate = true
        geometry?.getAttribute("color")?.needsUpdate = true
    }

    fun dispose() {
        geometry = null
    }
}
