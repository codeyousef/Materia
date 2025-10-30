#!/usr/bin/env kotlin

/**
 * Simple Materia Library Demo
 *
 * This standalone Kotlin script demonstrates the core functionality of the Materia 3D library
 * without requiring complex build setup. You can run this directly with:
 *
 *   kotlinc -script simple-demo.kt
 *
 * Or compile and run:
 *   kotlinc simple-demo.kt -include-runtime -d simple-demo.jar
 *   java -jar simple-demo.jar
 */

import kotlin.math.*

// ============================================================================
// Core Math Library Demo
// ============================================================================

data class Vector3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    constructor(value: Float) : this(value, value, value)

    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)

    fun length() = sqrt(x * x + y * y + z * z)
    fun normalize() = this * (1f / length())
    fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun set(newX: Float, newY: Float, newZ: Float) {
        x = newX; y = newY; z = newZ
    }

    override fun toString() = "Vector3($x, $y, $z)"

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UP = Vector3(0f, 1f, 0f)
        val RIGHT = Vector3(1f, 0f, 0f)
        val FORWARD = Vector3(0f, 0f, -1f)
    }
}

data class Color(var r: Float, var g: Float, var b: Float, var a: Float = 1f) {
    constructor(gray: Float) : this(gray, gray, gray, 1f)

    operator fun plus(other: Color) = Color(r + other.r, g + other.g, b + other.b, a + other.a)
    operator fun times(scalar: Float) = Color(r * scalar, g * scalar, b * scalar, a * scalar)

    fun toHex(): String {
        val rInt = (r * 255).toInt().coerceIn(0, 255)
        val gInt = (g * 255).toInt().coerceIn(0, 255)
        val bInt = (b * 255).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(rInt, gInt, bInt)
    }

    companion object {
        val RED = Color(1f, 0f, 0f)
        val GREEN = Color(0f, 1f, 0f)
        val BLUE = Color(0f, 0f, 1f)
        val WHITE = Color(1f, 1f, 1f)
        val BLACK = Color(0f, 0f, 0f)

        fun lerp(a: Color, b: Color, t: Float): Color {
            val invT = 1f - t
            return Color(
                a.r * invT + b.r * t,
                a.g * invT + b.g * t,
                a.b * invT + b.b * t,
                a.a * invT + b.a * t
            )
        }
    }
}

data class Matrix4(val elements: FloatArray = FloatArray(16)) {
    init {
        if (elements.size != 16) throw IllegalArgumentException("Matrix4 requires 16 elements")
    }

    fun setIdentity(): Matrix4 {
        elements.fill(0f)
        elements[0] = 1f; elements[5] = 1f; elements[10] = 1f; elements[15] = 1f
        return this
    }

    fun setTranslation(v: Vector3): Matrix4 {
        setIdentity()
        elements[12] = v.x; elements[13] = v.y; elements[14] = v.z
        return this
    }

    fun setScale(v: Vector3): Matrix4 {
        setIdentity()
        elements[0] = v.x; elements[5] = v.y; elements[10] = v.z
        return this
    }

    operator fun times(other: Matrix4): Matrix4 {
        val result = Matrix4()
        for (i in 0..3) {
            for (j in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += elements[i * 4 + k] * other.elements[k * 4 + j]
                }
                result.elements[i * 4 + j] = sum
            }
        }
        return result
    }

    companion object {
        fun identity() = Matrix4().setIdentity()
        fun translation(v: Vector3) = Matrix4().setTranslation(v)
        fun scale(v: Vector3) = Matrix4().setScale(v)
    }
}

// ============================================================================
// Scene Graph Demo
// ============================================================================

open class Object3D {
    val position = Vector3()
    val rotation = Vector3()
    val scale = Vector3(1f)
    val children = mutableListOf<Object3D>()
    var parent: Object3D? = null
    var name = "Object3D"
    var visible = true

    val matrix = Matrix4.identity()
    val matrixWorld = Matrix4.identity()

    fun add(child: Object3D) {
        children.add(child)
        child.parent = this
    }

    fun remove(child: Object3D) {
        children.remove(child)
        child.parent = null
    }

    fun updateMatrix() {
        val translation = Matrix4.translation(position)
        val scaling = Matrix4.scale(scale)
        // Simplified - in real implementation would include rotation
        matrix.elements.fill(0f)
        matrix.setIdentity()
        for (i in matrix.elements.indices) {
            matrix.elements[i] = translation.elements[i] + scaling.elements[i] -
                                  (if (i % 5 == 0) 1f else 0f) // Adjust for identity overlap
        }
    }

    fun updateMatrixWorld() {
        updateMatrix()
        if (parent != null) {
            matrixWorld.elements.copyFrom(parent!!.matrixWorld * matrix)
        } else {
            matrixWorld.elements.copyFrom(matrix.elements)
        }

        children.forEach { it.updateMatrixWorld() }
    }

    fun traverse(callback: (Object3D) -> Unit) {
        callback(this)
        children.forEach { it.traverse(callback) }
    }

    override fun toString() = "$name at $position"
}

private fun FloatArray.copyFrom(source: FloatArray) {
    for (i in indices) {
        this[i] = source[i]
    }
}

class Scene : Object3D() {
    var background = Color.BLACK
    val lights = mutableListOf<Object3D>()

    init {
        name = "Scene"
    }

    fun addLight(light: Object3D) {
        lights.add(light)
        add(light)
    }
}

class Camera : Object3D() {
    var fov = 75f
    var aspect = 16f / 9f
    var near = 0.1f
    var far = 1000f

    init {
        name = "Camera"
    }

    fun lookAt(target: Vector3) {
        val direction = (target - position).normalize()
        // Simplified look-at calculation
        println("  Camera looking at $target (direction: $direction)")
    }
}

// ============================================================================
// Material System Demo
// ============================================================================

abstract class Material {
    var transparent = false
    var opacity = 1f
    var side = "front" // front, back, double

    abstract fun compile(): String
}

class BasicMaterial(var color: Color = Color.WHITE) : Material() {
    override fun compile(): String {
        return "Basic material with color ${color.toHex()}"
    }
}

class PBRMaterial : Material() {
    var baseColor = Color.WHITE
    var metallic = 0f
    var roughness = 0.5f
    var emissive = Color.BLACK

    override fun compile(): String {
        return "PBR material: baseColor=${baseColor.toHex()}, metallic=$metallic, roughness=$roughness"
    }
}

// ============================================================================
// Geometry Demo
// ============================================================================

class Geometry {
    val vertices = mutableListOf<Vector3>()
    val indices = mutableListOf<Int>()
    val uvs = mutableListOf<Vector3>()

    fun addVertex(v: Vector3) = vertices.add(v)
    fun addTriangle(a: Int, b: Int, c: Int) {
        indices.addAll(listOf(a, b, c))
    }

    companion object {
        fun createBox(width: Float, height: Float, depth: Float): Geometry {
            val geom = Geometry()
            val w = width / 2f
            val h = height / 2f
            val d = depth / 2f

            // Add 8 vertices for a cube
            geom.addVertex(Vector3(-w, -h, -d)) // 0
            geom.addVertex(Vector3( w, -h, -d)) // 1
            geom.addVertex(Vector3( w,  h, -d)) // 2
            geom.addVertex(Vector3(-w,  h, -d)) // 3
            geom.addVertex(Vector3(-w, -h,  d)) // 4
            geom.addVertex(Vector3( w, -h,  d)) // 5
            geom.addVertex(Vector3( w,  h,  d)) // 6
            geom.addVertex(Vector3(-w,  h,  d)) // 7

            // Add 12 triangles (2 per face × 6 faces)
            val faces = listOf(
                listOf(0, 1, 2, 0, 2, 3), // front
                listOf(1, 5, 6, 1, 6, 2), // right
                listOf(5, 4, 7, 5, 7, 6), // back
                listOf(4, 0, 3, 4, 3, 7), // left
                listOf(3, 2, 6, 3, 6, 7), // top
                listOf(4, 5, 1, 4, 1, 0)  // bottom
            )

            faces.forEach { face ->
                for (i in 0 until face.size step 3) {
                    geom.addTriangle(face[i], face[i + 1], face[i + 2])
                }
            }

            return geom
        }

        fun createSphere(radius: Float, segments: Int): Geometry {
            val geom = Geometry()

            // Simplified sphere generation (just a few rings)
            for (lat in 0..segments) {
                val theta = lat * PI / segments
                val sinTheta = sin(theta).toFloat()
                val cosTheta = cos(theta).toFloat()

                for (lon in 0..segments) {
                    val phi = lon * 2 * PI / segments
                    val sinPhi = sin(phi).toFloat()
                    val cosPhi = cos(phi).toFloat()

                    val x = radius * sinTheta * cosPhi
                    val y = radius * cosTheta
                    val z = radius * sinTheta * sinPhi

                    geom.addVertex(Vector3(x, y, z))
                }
            }

            return geom
        }
    }
}

class Mesh(val geometry: Geometry, val material: Material) : Object3D() {
    init {
        name = "Mesh"
    }

    fun getTriangleCount() = geometry.indices.size / 3
    fun getVertexCount() = geometry.vertices.size
}

// ============================================================================
// Animation System Demo
// ============================================================================

class AnimationClip(val name: String, val duration: Float) {
    val tracks = mutableListOf<AnimationTrack>()

    fun addTrack(track: AnimationTrack) = tracks.add(track)
}

class AnimationTrack(val property: String, val target: Object3D) {
    val keyframes = mutableListOf<Keyframe>()

    fun addKeyframe(time: Float, value: Vector3) {
        keyframes.add(Keyframe(time, value))
    }

    fun evaluate(time: Float): Vector3 {
        if (keyframes.isEmpty()) return Vector3.ZERO
        if (keyframes.size == 1) return keyframes[0].value

        // Find surrounding keyframes
        val before = keyframes.lastOrNull { it.time <= time } ?: keyframes.first()
        val after = keyframes.firstOrNull { it.time > time } ?: keyframes.last()

        if (before == after) return before.value

        // Linear interpolation
        val t = (time - before.time) / (after.time - before.time)
        return before.value + (after.value - before.value) * t
    }
}

data class Keyframe(val time: Float, val value: Vector3)

class AnimationMixer {
    private val activeClips = mutableListOf<AnimationClip>()

    fun play(clip: AnimationClip) {
        activeClips.add(clip)
    }

    fun update(deltaTime: Float) {
        // Update all active animation clips
        activeClips.forEach { clip ->
            // Apply animation transformations
        }
    }
}

// ============================================================================
// Main Demo Function
// ============================================================================

fun main() {
    println("🚀 Materia 3D Library Demo")
    println("=" .repeat(50))

    // Core Math Demo
    println("\n📐 Core Math Library:")
    val v1 = Vector3(1f, 2f, 3f)
    val v2 = Vector3(4f, 5f, 6f)
    val v3 = v1 + v2

    println("  Vector addition: $v1 + $v2 = $v3")
    println("  Vector length: |$v1| = ${v1.length()}")
    println("  Vector dot product: $v1 · $v2 = ${v1.dot(v2)}")
    println("  Vector cross product: $v1 × $v2 = ${v1.cross(v2)}")

    val color1 = Color.RED
    val color2 = Color.BLUE
    val blended = Color.lerp(color1, color2, 0.5f)
    println("  Color blending: RED + BLUE = ${blended.toHex()}")

    // Scene Graph Demo
    println("\n🏗️ Scene Graph System:")
    val scene = Scene()
    scene.background = Color(0.1f, 0.1f, 0.2f)

    // Create geometries
    val cubeGeom = Geometry.createBox(2f, 2f, 2f)
    val sphereGeom = Geometry.createSphere(1f, 16)

    // Create materials
    val pbrMaterial = PBRMaterial().apply {
        baseColor = Color(0.8f, 0.3f, 0.2f)
        metallic = 0.7f
        roughness = 0.3f
        emissive = Color(0.1f, 0.05f, 0f)
    }

    val basicMaterial = BasicMaterial(Color.BLUE)

    // Create meshes
    val cube = Mesh(cubeGeom, pbrMaterial).apply {
        name = "RotatingCube"
        position.set(0f, 2f, 0f)
    }

    val sphere = Mesh(sphereGeom, basicMaterial).apply {
        name = "FloatingSphere"
        position.set(3f, 3f, 2f)
    }

    scene.add(cube)
    scene.add(sphere)

    // Create camera
    val camera = Camera().apply {
        position.set(5f, 5f, 5f)
        lookAt(Vector3.ZERO)
    }
    scene.add(camera)

    println("  Scene created with ${scene.children.size} objects")
    println("  Cube: ${cube.getTriangleCount()} triangles, ${cube.getVertexCount()} vertices")
    println("  Sphere: ${sphere.getTriangleCount()} triangles, ${sphere.getVertexCount()} vertices")
    println("  Materials compiled:")
    println("    - ${pbrMaterial.compile()}")
    println("    - ${basicMaterial.compile()}")

    // Animation Demo
    println("\n🎬 Animation System:")
    val animationClip = AnimationClip("CubeRotation", 2f)
    val rotationTrack = AnimationTrack("rotation", cube)

    // Add keyframes for a spinning animation
    rotationTrack.addKeyframe(0f, Vector3(0f, 0f, 0f))
    rotationTrack.addKeyframe(1f, Vector3(0f, PI.toFloat(), 0f))
    rotationTrack.addKeyframe(2f, Vector3(0f, 2 * PI.toFloat(), 0f))

    animationClip.addTrack(rotationTrack)

    val mixer = AnimationMixer()
    mixer.play(animationClip)

    println("  Animation clip '${animationClip.name}' created (${animationClip.duration}s)")
    println("  Rotation track with ${rotationTrack.keyframes.size} keyframes")

    // Simulate animation frames
    println("  Animation frames:")
    for (frame in 0..4) {
        val time = frame * 0.5f
        val rotation = rotationTrack.evaluate(time)
        cube.rotation.set(rotation.x, rotation.y, rotation.z)
        println("    Frame $frame (t=${time}s): rotation = $rotation")
    }

    // Scene traversal demo
    println("\n🔍 Scene Traversal:")
    var objectCount = 0
    scene.traverse { obj ->
        objectCount++
        println("  [$objectCount] ${obj.name} at ${obj.position}")
    }

    // Update world matrices
    scene.updateMatrixWorld()
    println("  World matrices updated for all objects")

    // Render info simulation
    println("\n📊 Render Statistics:")
    var totalTriangles = 0
    var totalVertices = 0
    var meshCount = 0

    scene.traverse { obj ->
        if (obj is Mesh) {
            meshCount++
            totalTriangles += obj.getTriangleCount()
            totalVertices += obj.getVertexCount()
        }
    }

    println("  Meshes: $meshCount")
    println("  Total triangles: $totalTriangles")
    println("  Total vertices: $totalVertices")
    println("  Camera: ${camera.name} (FOV: ${camera.fov}°)")
    println("  Background: ${scene.background.toHex()}")

    println("\n✅ Materia Demo Complete!")
    println("\nThis demonstrates the core functionality of the Materia 3D library:")
    println("• Math primitives (Vector3, Matrix4, Color)")
    println("• Scene graph system (Object3D hierarchy)")
    println("• Geometry generation (Box, Sphere)")
    println("• Material system (Basic, PBR)")
    println("• Animation framework (Clips, Tracks, Keyframes)")
    println("• Rendering pipeline (Meshes, Cameras)")

    println("\nTo run the full interactive example with WebGPU/Vulkan rendering:")
    println("./gradlew :examples:basic-scene:run")
}