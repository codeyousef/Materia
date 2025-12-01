package io.materia.helper

import io.materia.core.math.Color
import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.core.scene.Mesh
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute
import io.materia.material.LineBasicMaterial

/**
 * Vertex normals helper - visualizes vertex normals as lines.
 *
 * Creates a visual representation of the vertex normals of a geometry
 * as lines extending from each vertex in the normal direction.
 * Useful for debugging lighting issues and verifying normal orientation.
 *
 * Based on Three.js VertexNormalsHelper.
 *
 * Usage:
 * ```kotlin
 * val mesh = Mesh(geometry, material)
 * val helper = VertexNormalsHelper(mesh, 1f, Color.RED)
 * scene.add(mesh)
 * scene.add(helper)
 * 
 * // Update when mesh transforms change
 * helper.update()
 * ```
 */
class VertexNormalsHelper(
    /** The object whose normals to visualize */
    val targetObject: Object3D,
    /** Length of the normal lines */
    var size: Float = 1f,
    /** Color of the normal lines */
    var color: Color = Color(0xff0000) // Red
) : Object3D() {

    private val geometry: BufferGeometry
    private val material: LineBasicMaterial
    private var positionAttribute: BufferAttribute? = null

    init {
        geometry = BufferGeometry()
        material = LineBasicMaterial().apply {
            this.color = this@VertexNormalsHelper.color
            toneMapped = false
        }

        name = "VertexNormalsHelper"
        matrixAutoUpdate = false

        update()
    }

    /**
     * Updates the helper to reflect changes in the target object.
     * Call this after modifying the target object's geometry or transform.
     */
    fun update() {
        val objGeometry = getGeometry(targetObject) ?: return
        
        val posAttr = objGeometry.getAttribute("position") as? BufferAttribute ?: return
        val normalAttr = objGeometry.getAttribute("normal") as? BufferAttribute ?: return
        
        val vertexCount = posAttr.count
        val lineCount = vertexCount * 2 // 2 vertices per line

        // Create or resize position buffer
        if (positionAttribute == null || positionAttribute!!.count != lineCount) {
            positionAttribute = BufferAttribute(FloatArray(lineCount * 3), 3)
            geometry.setAttribute("position", positionAttribute!!)
        }

        val positions = positionAttribute!!.array
        
        // Update world matrix
        targetObject.updateMatrixWorld(true)
        val matrixWorld = targetObject.matrixWorld
        val normalMatrix = Matrix3().getNormalMatrix(matrixWorld)

        val position = Vector3()
        val normal = Vector3()

        for (i in 0 until vertexCount) {
            // Get vertex position
            position.set(
                posAttr.getX(i),
                posAttr.getY(i),
                posAttr.getZ(i)
            )
            position.applyMatrix4(matrixWorld)

            // Get normal
            normal.set(
                normalAttr.getX(i),
                normalAttr.getY(i),
                normalAttr.getZ(i)
            )
            normal.applyMatrix3(normalMatrix).normalize()

            // Line start (vertex position)
            val idx = i * 6
            positions[idx] = position.x
            positions[idx + 1] = position.y
            positions[idx + 2] = position.z

            // Line end (vertex + normal * size)
            positions[idx + 3] = position.x + normal.x * size
            positions[idx + 4] = position.y + normal.y * size
            positions[idx + 5] = position.z + normal.z * size
        }

        positionAttribute!!.needsUpdate = true
    }

    private fun getGeometry(obj: Object3D): BufferGeometry? {
        return when (obj) {
            is Mesh -> obj.geometry as? BufferGeometry
            else -> null
        }
    }

    fun dispose() {
        geometry.dispose()
        // Material cleanup is handled by the scene
    }
}

/**
 * Vertex tangents helper - visualizes vertex tangents as lines.
 *
 * Creates a visual representation of the vertex tangents of a geometry
 * as lines extending from each vertex in the tangent direction.
 * Useful for debugging normal mapping and verifying tangent space.
 *
 * Based on Three.js VertexTangentsHelper.
 *
 * Usage:
 * ```kotlin
 * // Geometry must have tangent attribute computed
 * geometry.computeTangents()
 * 
 * val mesh = Mesh(geometry, material)
 * val helper = VertexTangentsHelper(mesh, 1f, Color.CYAN)
 * scene.add(mesh)
 * scene.add(helper)
 * ```
 */
class VertexTangentsHelper(
    /** The object whose tangents to visualize */
    val targetObject: Object3D,
    /** Length of the tangent lines */
    var size: Float = 1f,
    /** Color of the tangent lines */
    var color: Color = Color(0x00ffff) // Cyan
) : Object3D() {

    private val geometry: BufferGeometry
    private val material: LineBasicMaterial
    private var positionAttribute: BufferAttribute? = null

    init {
        geometry = BufferGeometry()
        material = LineBasicMaterial().apply {
            this.color = this@VertexTangentsHelper.color
            toneMapped = false
        }

        name = "VertexTangentsHelper"
        matrixAutoUpdate = false

        update()
    }

    /**
     * Updates the helper to reflect changes in the target object.
     */
    fun update() {
        val objGeometry = getGeometry(targetObject) ?: return
        
        val posAttr = objGeometry.getAttribute("position") as? BufferAttribute ?: return
        val tangentAttr = objGeometry.getAttribute("tangent") as? BufferAttribute
        
        if (tangentAttr == null) {
            // Tangents not computed - geometry needs tangent attribute
            // Use geometry.computeTangents() if available
            return
        }
        
        val vertexCount = posAttr.count
        val lineCount = vertexCount * 2

        // Create or resize position buffer
        if (positionAttribute == null || positionAttribute!!.count != lineCount) {
            positionAttribute = BufferAttribute(FloatArray(lineCount * 3), 3)
            geometry.setAttribute("position", positionAttribute!!)
        }

        val positions = positionAttribute!!.array

        // Update world matrix
        targetObject.updateMatrixWorld(true)
        val matrixWorld = targetObject.matrixWorld

        val position = Vector3()
        val tangent = Vector3()

        for (i in 0 until vertexCount) {
            // Get vertex position
            position.set(
                posAttr.getX(i),
                posAttr.getY(i),
                posAttr.getZ(i)
            )
            position.applyMatrix4(matrixWorld)

            // Get tangent (tangents are vec4: xyz = tangent, w = handedness)
            tangent.set(
                tangentAttr.getX(i),
                tangentAttr.getY(i),
                tangentAttr.getZ(i)
            )
            tangent.transformDirection(matrixWorld)

            // Line start
            val idx = i * 6
            positions[idx] = position.x
            positions[idx + 1] = position.y
            positions[idx + 2] = position.z

            // Line end
            positions[idx + 3] = position.x + tangent.x * size
            positions[idx + 4] = position.y + tangent.y * size
            positions[idx + 5] = position.z + tangent.z * size
        }

        positionAttribute!!.needsUpdate = true
    }

    private fun getGeometry(obj: Object3D): BufferGeometry? {
        return when (obj) {
            is Mesh -> obj.geometry as? BufferGeometry
            else -> null
        }
    }

    fun dispose() {
        geometry.dispose()
        // Material cleanup is handled by the scene
    }
}

/**
 * Face normals helper - visualizes face normals as lines.
 *
 * Creates a visual representation of face normals (computed from
 * face vertices) as lines extending from the center of each face.
 * Useful for debugging face winding and flat shading issues.
 *
 * Based on Three.js FaceNormalsHelper.
 *
 * Note: This helper works with indexed and non-indexed geometry.
 * For indexed geometry, faces are defined by every 3 indices.
 * For non-indexed geometry, faces are defined by every 3 vertices.
 *
 * Usage:
 * ```kotlin
 * val mesh = Mesh(geometry, material)
 * val helper = FaceNormalsHelper(mesh, 1f, Color.YELLOW)
 * scene.add(mesh)
 * scene.add(helper)
 * ```
 */
class FaceNormalsHelper(
    /** The object whose face normals to visualize */
    val targetObject: Object3D,
    /** Length of the normal lines */
    var size: Float = 1f,
    /** Color of the normal lines */
    var color: Color = Color(0xffff00) // Yellow
) : Object3D() {

    private val geometry: BufferGeometry
    private val material: LineBasicMaterial
    private var positionAttribute: BufferAttribute? = null

    init {
        geometry = BufferGeometry()
        material = LineBasicMaterial().apply {
            this.color = this@FaceNormalsHelper.color
            toneMapped = false
        }

        name = "FaceNormalsHelper"
        matrixAutoUpdate = false

        update()
    }

    /**
     * Updates the helper to reflect changes in the target object.
     */
    fun update() {
        val objGeometry = getGeometry(targetObject) ?: return
        
        val posAttr = objGeometry.getAttribute("position") as? BufferAttribute ?: return
        val indexAttr = objGeometry.index
        
        val faceCount = if (indexAttr != null) {
            indexAttr.count / 3
        } else {
            posAttr.count / 3
        }
        
        val lineCount = faceCount * 2

        // Create or resize position buffer
        if (positionAttribute == null || positionAttribute!!.count != lineCount) {
            positionAttribute = BufferAttribute(FloatArray(lineCount * 3), 3)
            geometry.setAttribute("position", positionAttribute!!)
        }

        val positions = positionAttribute!!.array

        // Update world matrix
        targetObject.updateMatrixWorld(true)
        val matrixWorld = targetObject.matrixWorld
        val normalMatrix = Matrix3().getNormalMatrix(matrixWorld)

        val vA = Vector3()
        val vB = Vector3()
        val vC = Vector3()
        val center = Vector3()
        val normal = Vector3()
        val cb = Vector3()
        val ab = Vector3()

        for (i in 0 until faceCount) {
            // Get face vertices
            val (a, b, c) = if (indexAttr != null) {
                Triple(
                    indexAttr.getX(i * 3).toInt(),
                    indexAttr.getX(i * 3 + 1).toInt(),
                    indexAttr.getX(i * 3 + 2).toInt()
                )
            } else {
                Triple(i * 3, i * 3 + 1, i * 3 + 2)
            }

            vA.set(posAttr.getX(a), posAttr.getY(a), posAttr.getZ(a))
            vB.set(posAttr.getX(b), posAttr.getY(b), posAttr.getZ(b))
            vC.set(posAttr.getX(c), posAttr.getY(c), posAttr.getZ(c))

            // Compute face center
            center.set(
                (vA.x + vB.x + vC.x) / 3f,
                (vA.y + vB.y + vC.y) / 3f,
                (vA.z + vB.z + vC.z) / 3f
            )
            center.applyMatrix4(matrixWorld)

            // Compute face normal
            cb.copy(vC).subtract(vB)
            ab.copy(vA).subtract(vB)
            normal.copy(cb).cross(ab).normalize()
            normal.applyMatrix3(normalMatrix).normalize()

            // Line start (face center)
            val idx = i * 6
            positions[idx] = center.x
            positions[idx + 1] = center.y
            positions[idx + 2] = center.z

            // Line end (center + normal * size)
            positions[idx + 3] = center.x + normal.x * size
            positions[idx + 4] = center.y + normal.y * size
            positions[idx + 5] = center.z + normal.z * size
        }

        positionAttribute!!.needsUpdate = true
    }

    private fun getGeometry(obj: Object3D): BufferGeometry? {
        return when (obj) {
            is Mesh -> obj.geometry as? BufferGeometry
            else -> null
        }
    }

    fun dispose() {
        geometry.dispose()
        // Material cleanup is handled by the scene
    }
}
