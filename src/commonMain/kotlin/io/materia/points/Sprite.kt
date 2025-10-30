package io.materia.points

import io.materia.core.math.*
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.raycaster.Intersection
import io.materia.raycaster.Raycaster
import io.materia.texture.Texture

/**
 * Sprite - Billboard that always faces the camera
 * T093 - Camera-facing quads with textures
 *
 * A plane that always faces the camera, useful for:
 * - Particle effects
 * - Labels and annotations
 * - Impostors for distant objects
 * - UI elements in 3D space
 */
open class Sprite(
    var material: SpriteMaterial = SpriteMaterial()
) : Object3D() {

    override val type = "Sprite"

    /**
     * Sprite center point (0.5, 0.5 is center)
     */
    val center = Vector2(0.5f, 0.5f)

    /**
     * Internal geometry for raycasting
     */
    private val geometry: BufferGeometry by lazy {
        BufferGeometry().apply {
            val vertices = floatArrayOf(
                -0.5f, -0.5f, 0f,
                0.5f, -0.5f, 0f,
                0.5f, 0.5f, 0f,
                -0.5f, 0.5f, 0f
            )
            setAttribute("position", BufferAttribute(vertices, 3))
        }
    }

    /**
     * Raycast against sprite
     */
    fun raycast(raycaster: Raycaster, intersects: MutableList<Intersection>) {
        // Transform ray to sprite's local space
        val worldMatrix = this.matrixWorld
        // Extract scale from world matrix
        val worldScale = Vector3()
        worldMatrix.decompose(Vector3(), Quaternion(), worldScale)

        // For sprites, we need to handle them specially since they face the camera
        // Camera view matrix would come from renderer context
        // For now, using identity matrix as placeholder
        val viewMatrix = Matrix4()

        // Create a matrix that combines world position with camera rotation
        val mvMatrix = Matrix4()
        mvMatrix.multiplyMatrices(viewMatrix, worldMatrix)

        // Extract position from model-view matrix
        val position = Vector3().setFromMatrixColumn(mvMatrix, 3)

        // Sprites are in screen space after camera transform
        // We need to test in view space
        transformVertex(vertices[0], mvMatrix, worldScale)
        transformVertex(vertices[1], mvMatrix, worldScale)
        transformVertex(vertices[2], mvMatrix, worldScale)
        transformVertex(vertices[3], mvMatrix, worldScale)

        // Check if ray intersects the sprite quad
        val intersectionPoint = Vector3()

        // Test two triangles that make up the sprite quad
        if (rayIntersectsTriangle(
                raycaster.ray,
                vertices[0],
                vertices[1],
                vertices[2],
                intersectionPoint
            ) ||
            rayIntersectsTriangle(
                raycaster.ray,
                vertices[0],
                vertices[2],
                vertices[3],
                intersectionPoint
            )
        ) {

            val distance = raycaster.ray.origin.distanceTo(intersectionPoint)

            if (distance >= raycaster.near && distance <= raycaster.far) {
                intersects.add(
                    Intersection(
                        distance = distance,
                        point = intersectionPoint.clone(),
                        face = null,
                        `object` = this
                    )
                )
            }
        }
    }

    private val vertices = Array(4) { Vector3() }

    private fun transformVertex(vertex: Vector3, mvMatrix: Matrix4, scale: Vector3) {
        vertex.applyMatrix4(mvMatrix)
        vertex.x *= scale.x
        vertex.y *= scale.y
    }

    private fun rayIntersectsTriangle(
        ray: Ray,
        a: Vector3,
        b: Vector3,
        c: Vector3,
        target: Vector3
    ): Boolean {
        // Möller-Trumbore ray-triangle intersection algorithm
        val edge1 = Vector3().subVectors(b, a)
        val edge2 = Vector3().subVectors(c, a)
        val h = Vector3().crossVectors(ray.direction, edge2)
        val det = edge1.dot(h)

        if (det > -0.00001f && det < 0.00001f) return false

        val invDet = 1f / det
        val s = Vector3().subVectors(ray.origin, a)
        val u = invDet * s.dot(h)

        if (u < 0f || u > 1f) return false

        val q = Vector3().crossVectors(s, edge1)
        val v = invDet * ray.direction.dot(q)

        if (v < 0f || u + v > 1f) return false

        val t = invDet * edge2.dot(q)

        if (t > 0.00001f) {
            target.copy(ray.direction).multiplyScalar(t).add(ray.origin)
            return true
        }

        return false
    }

    /**
     * Copy from another sprite
     */
    fun copy(source: Sprite, recursive: Boolean = true): Sprite {
        super.copy(source, recursive)

        this.center.copy(source.center)
        this.material = source.material.clone()

        return this
    }

    /**
     * Clone this sprite
     */
    override fun clone(recursive: Boolean): Sprite {
        return Sprite(material.clone()).copy(this, recursive)
    }

    companion object {
        /**
         * Create a sprite with a texture
         */
        fun fromTexture(
            texture: Texture,
            color: Int = 0xffffff,
            scale: Float = 1f
        ): Sprite {
            val material = SpriteMaterial(
                map = texture,
                color = Color(color)
            )

            return Sprite(material).apply {
                this.scale.set(scale, scale, 1f)
            }
        }

        /**
         * Create a colored sprite
         */
        fun colored(
            color: Int,
            size: Float = 1f,
            opacity: Float = 1f
        ): Sprite {
            val material = SpriteMaterial(
                color = Color(color),
                opacity = opacity,
                transparent = opacity < 1f
            )

            return Sprite(material).apply {
                scale.set(size, size, 1f)
            }
        }
    }
}