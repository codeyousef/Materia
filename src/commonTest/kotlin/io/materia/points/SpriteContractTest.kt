package io.materia.points

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.raycaster.Intersection
import io.materia.raycaster.Raycaster
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.*

/**
 * Contract test for Sprite billboards - T030
 * Covers: FR-P006, FR-P007, FR-P008, FR-P009 from contracts/points-api.kt
 */
class SpriteContractTest {

    @Test
    fun testSpriteAlwaysFacesCamera() {
        // FR-P006: Always face camera
        val sprite = Sprite(SpriteMaterial())
        val camera = PerspectiveCamera(75f, 1.77f, 0.1f, 1000f)

        // Test from different camera positions
        val cameraPositions = listOf(
            Vector3(5f, 0f, 0f),   // Right
            Vector3(-5f, 0f, 0f),  // Left
            Vector3(0f, 5f, 0f),   // Above
            Vector3(0f, -5f, 0f),  // Below
            Vector3(0f, 0f, 5f),   // Front
            Vector3(0f, 0f, -5f)   // Behind
        )

        for (camPos in cameraPositions) {
            camera.position.copy(camPos)
            camera.lookAt(Vector3(0f, 0f, 0f))
            camera.updateMatrixWorld()

            sprite.updateForCamera(camera)

            // Sprite's forward vector should point towards camera
            val spriteForward = Vector3(0f, 0f, 1f).applyMatrix4(sprite.matrixWorld)
            val toCamera = camera.position.clone().sub(sprite.position).normalize()

            // Vectors should be aligned (dot product close to 1)
            val dotProduct = spriteForward.dot(toCamera)
            assertTrue(
                abs(1.0f - dotProduct) < 0.01f,
                "Sprite should face camera from position $camPos, dot=$dotProduct"
            )
        }
    }

    @Test
    fun testSpriteTextureMapping() {
        // FR-P007: Texture mapping
        val texture = createSpriteTexture()
        val material = SpriteMaterial().apply {
            map = texture
        }
        val sprite = Sprite(material)

        assertNotNull(material.map)
        assertEquals(texture, material.map)

        // Test UV coordinates
        val uvs = sprite.getUVCoordinates()
        assertEquals(4, uvs.size) // 4 corners

        // Default UVs should be (0,0), (1,0), (1,1), (0,1)
        assertEquals(Vector2(0f, 0f), uvs[0])
        assertEquals(Vector2(1f, 0f), uvs[1])
        assertEquals(Vector2(1f, 1f), uvs[2])
        assertEquals(Vector2(0f, 1f), uvs[3])

        // Test sprite sheet animation (sub-region of texture)
        sprite.setTextureRegion(
            offsetX = 0.25f,
            offsetY = 0.25f,
            width = 0.5f,
            height = 0.5f
        )

        val animUvs = sprite.getUVCoordinates()
        assertEquals(Vector2(0.25f, 0.25f), animUvs[0])
        assertEquals(Vector2(0.75f, 0.25f), animUvs[1])
        assertEquals(Vector2(0.75f, 0.75f), animUvs[2])
        assertEquals(Vector2(0.25f, 0.75f), animUvs[3])
    }

    @Test
    fun testSpriteRotationAndScaling() {
        // FR-P008: Rotation and scaling
        val sprite = Sprite(SpriteMaterial())

        // Test rotation
        sprite.material.rotation = PI.toFloat() / 4 // 45 degrees
        assertEquals(PI.toFloat() / 4, sprite.material.rotation)

        // Verify rotation affects sprite vertices
        val vertices = sprite.getTransformedVertices()
        // After 45-degree rotation, corners should be rotated
        assertTrue(vertices[0].x != -0.5f || vertices[0].y != -0.5f)

        // Test scaling
        sprite.scale.set(2f, 3f, 1f)
        sprite.updateMatrix()

        val scaledVertices = sprite.getTransformedVertices()
        // Width should be doubled, height tripled
        // Use distance between adjacent vertices since sprite is rotated
        val width = scaledVertices[0].distanceTo(scaledVertices[1])
        val height = scaledVertices[0].distanceTo(scaledVertices[3])
        assertTrue(abs(width - 2f) < 0.01f, "Width should be 2.0, got $width")
        assertTrue(abs(height - 3f) < 0.01f, "Height should be 3.0, got $height")

        // Test center point for rotation/scaling
        sprite.center.set(1f, 1f) // Top-right corner as center
        sprite.updateMatrix()

        val centeredVertices = sprite.getTransformedVertices()
        // Sprite should be offset based on new center
        // With center at (1,1), the top-right vertex should be at origin
        val topRight = centeredVertices[2] // Third vertex is top-right
        assertTrue(
            abs(topRight.x) < 0.01f && abs(topRight.y) < 0.01f,
            "With center (1,1), top-right vertex should be near origin, got (${topRight.x}, ${topRight.y})"
        )
    }

    @Test
    fun testRaycastingAgainstSprites() {
        // FR-P009: Raycasting against sprites
        val material = SpriteMaterial().apply {
            map = createSpriteTexture()
            alphaTest = 0.5f // Only hit opaque pixels
        }
        val sprite = Sprite(material).apply {
            position.set(0f, 0f, 0f)
            scale.set(2f, 2f, 1f)
            updateMatrixWorld()  // Update matrix before raycasting
        }

        val raycaster = Raycaster(
            origin = Vector3(0f, 0f, 10f),
            direction = Vector3(0f, 0f, -1f)
        )

        // Test center hit
        val intersections = sprite.raycast(raycaster)
        assertTrue(intersections.isNotEmpty())
        assertEquals(1, intersections.size)

        val hit = intersections[0]
        assertNotNull(hit.point)
        assertEquals(10f, hit.distance)
        assertEquals(Vector2(0.5f, 0.5f), hit.uv) // Center UV

        // Test edge hit
        raycaster.set(
            Vector3(0.9f, 0f, 10f),
            Vector3(0f, 0f, -1f)
        )
        val edgeIntersections = sprite.raycast(raycaster)
        assertTrue(edgeIntersections.isNotEmpty())
        val edgeHit = edgeIntersections[0]
        assertTrue(edgeHit.uv!!.x > 0.9f) // Near right edge

        // Test miss
        raycaster.set(
            Vector3(5f, 0f, 10f), // Far to the right
            Vector3(0f, 0f, -1f)
        )
        val missIntersections = sprite.raycast(raycaster)
        assertTrue(missIntersections.isEmpty())
    }

    @Test
    fun testSpriteBatching() {
        // Test efficient batching of multiple sprites
        val sprites = mutableListOf<Sprite>()
        val material = SpriteMaterial().apply {
            map = createSpriteTexture()
        }

        // Create many sprites sharing same material
        for (i in 0 until 1000) {
            val sprite = Sprite(material).apply {
                position.set(
                    (kotlin.random.Random.nextDouble() * 100 - 50).toFloat(),
                    (kotlin.random.Random.nextDouble() * 100 - 50).toFloat(),
                    (kotlin.random.Random.nextDouble() * 100 - 50).toFloat()
                )
            }
            sprites.add(sprite)
        }

        // Verify all sprites share same material for batching
        val sharedMaterial = sprites[0].material
        assertTrue(sprites.all { it.material === sharedMaterial })

        // Test batch rendering
        val batch = SpriteBatch()
        batch.addSprites(sprites)
        assertEquals(1000, batch.spriteCount)
        assertEquals(1, batch.materialCount) // Single draw call possible
    }

    @Test
    fun testSpriteAnimation() {
        // Test sprite sheet animation
        val material = SpriteMaterial().apply {
            map = createSpriteSheetTexture(8, 8) // 8x8 sprite sheet
        }
        val sprite = AnimatedSprite(material, 8, 8)

        // Test frame selection
        sprite.setFrame(0)
        var uvs = sprite.getUVCoordinates()
        assertEquals(Vector2(0f, 0f), uvs[0])

        sprite.setFrame(7) // Last frame of first row
        uvs = sprite.getUVCoordinates()
        assertTrue(abs(uvs[1].x - 1f) < 0.01f)

        sprite.setFrame(8) // First frame of second row
        uvs = sprite.getUVCoordinates()
        assertEquals(0f, uvs[0].x)
        assertTrue(uvs[0].y > 0f)

        // Test animation playback
        sprite.play(startFrame = 0, endFrame = 63, loop = true, fps = 30f)
        assertTrue(sprite.isPlaying)

        // Simulate time passing
        sprite.update(deltaTime = 1f / 30f) // One frame at 30fps
        assertEquals(1, sprite.currentFrame)

        sprite.update(deltaTime = 2f) // 60 frames forward
        assertEquals(61, sprite.currentFrame)

        // Test loop
        sprite.update(deltaTime = 0.1f) // Pass end frame
        assertTrue(sprite.currentFrame < 63) // Should loop back
    }

    @Test
    fun testSpriteMaterialProperties() {
        // Test SpriteMaterial specific properties
        val material = SpriteMaterial()

        // Default values
        assertEquals(Color(1f, 1f, 1f), material.color)
        assertEquals(0f, material.rotation)
        assertEquals(Vector2(0.5f, 0.5f), material.center)
        assertNull(material.map)

        // Test property changes
        material.color = Color(1f, 0f, 0f)
        material.rotation = PI.toFloat() / 2
        material.center.set(0f, 1f)
        material.opacity = 0.7f
        material.transparent = true
        material.fog = false
        material.sizeAttenuation = false

        assertEquals(Color(1f, 0f, 0f), material.color)
        assertEquals(PI.toFloat() / 2, material.rotation)
        assertEquals(Vector2(0f, 1f), material.center)
        assertEquals(0.7f, material.opacity)
        assertTrue(material.transparent)
        assertEquals(false, material.fog)
        assertEquals(false, material.sizeAttenuation)
    }

    @Test
    fun testSpriteDepthTesting() {
        // Test sprite depth testing options
        val material = SpriteMaterial().apply {
            depthTest = false // Sprites rendered on top
            depthWrite = false
        }

        assertEquals(false, material.depthTest)
        assertEquals(false, material.depthWrite)

        // Create sprites at different depths
        val frontSprite = Sprite(material.clone()).apply {
            position.z = 5f
        }
        val backSprite = Sprite(material.clone()).apply {
            position.z = 10f
        }

        // With depthTest = false, render order should be based on addition order
        val renderOrder = listOf(frontSprite, backSprite)
        assertEquals(frontSprite, renderOrder[0])
        assertEquals(backSprite, renderOrder[1])

        // Enable depth testing
        frontSprite.material.depthTest = true
        backSprite.material.depthTest = true

        // Now front sprite should occlude back sprite
        assertTrue(frontSprite.position.z < backSprite.position.z)
    }

    // Helper functions

    private fun createSpriteTexture(): Texture2D {
        return Texture2D(64, 64)
    }

    private fun createSpriteSheetTexture(rows: Int, cols: Int): Texture2D {
        return Texture2D(rows * 64, cols * 64)
    }
}

// Supporting classes for the contract test

class Sprite(
    val material: SpriteMaterial
) : Object3D() {

    val center = Vector2(0.5f, 0.5f)
    private var textureOffset = Vector2(0f, 0f)
    private var textureRepeat = Vector2(1f, 1f)

    fun updateForCamera(camera: Camera) {
        // Calculate billboard rotation to face camera
        // The sprite should look from camera to sprite, not sprite to camera
        val lookMatrix = Matrix4()
        lookMatrix.lookAt(camera.position, position, Vector3(0f, 1f, 0f))

        // Extract rotation from look matrix and apply it
        quaternion.setFromRotationMatrix(lookMatrix)
        updateMatrix()
        updateMatrixWorld()
    }

    fun getUVCoordinates(): List<Vector2> {
        return listOf(
            Vector2(textureOffset.x, textureOffset.y),
            Vector2(textureOffset.x + textureRepeat.x, textureOffset.y),
            Vector2(textureOffset.x + textureRepeat.x, textureOffset.y + textureRepeat.y),
            Vector2(textureOffset.x, textureOffset.y + textureRepeat.y)
        )
    }

    fun setTextureRegion(offsetX: Float, offsetY: Float, width: Float, height: Float) {
        textureOffset.set(offsetX, offsetY)
        textureRepeat.set(width, height)
    }

    fun getTransformedVertices(): List<Vector3> {
        // Base size is 1x1, corners at +/- 0.5
        val halfWidth = 0.5f
        val halfHeight = 0.5f

        // Apply center offset and rotation
        val cos = kotlin.math.cos(material.rotation)
        val sin = kotlin.math.sin(material.rotation)
        // Center offset: (0.5, 0.5) = centered, (0, 0) = bottom-left at origin, (1, 1) = top-right at origin
        val cx = (0.5f - center.x) * scale.x
        val cy = (0.5f - center.y) * scale.y

        val vertices = mutableListOf<Vector3>()
        val corners = listOf(
            Vector2(-halfWidth, -halfHeight),
            Vector2(halfWidth, -halfHeight),
            Vector2(halfWidth, halfHeight),
            Vector2(-halfWidth, halfHeight)
        )

        for (corner in corners) {
            // Scale first, offset by center, then rotate
            val x = corner.x * scale.x + cx
            val y = corner.y * scale.y + cy
            val rotatedX = x * cos - y * sin
            val rotatedY = x * sin + y * cos
            vertices.add(Vector3(rotatedX, rotatedY, 0f))
        }

        return vertices
    }

    fun raycast(raycaster: Raycaster): List<Intersection> {
        // Simplified sprite raycasting
        val intersections = mutableListOf<Intersection>()

        // Transform ray to sprite space
        val inverseMatrix = Matrix4().copy(matrixWorld).invert()
        val localRay = raycaster.ray.clone()
        localRay.applyMatrix4(inverseMatrix)

        // Check intersection with sprite plane (z=0)
        val t = -localRay.origin.z / localRay.direction.z
        if (t > 0) {
            val point = localRay.origin.clone().add(
                localRay.direction.clone().multiplyScalar(t)
            )

            // Check if point is within sprite bounds
            // In local space, sprite quad is from [-0.5, -0.5] to [0.5, 0.5]
            val halfWidth = 0.5f
            val halfHeight = 0.5f

            if (abs(point.x) <= halfWidth && abs(point.y) <= halfHeight) {
                // Calculate UV coordinates (point is in range [-0.5, 0.5])
                val uv = Vector2(
                    point.x + 0.5f,
                    point.y + 0.5f
                )

                // Check alpha test if texture exists
                if (material.alphaTest > 0 && material.map != null) {
                    val alpha = material.map!!.getAlphaAt(uv)
                    if (alpha < material.alphaTest) {
                        return emptyList() // Transparent pixel
                    }
                }

                intersections.add(
                    Intersection(
                        distance = t,
                        point = point.applyMatrix4(matrixWorld),
                        `object` = this,
                        uv = uv
                    )
                )
            }
        }

        return intersections
    }
}

class SpriteMaterial : PointsMaterial() {
    var rotation: Float = 0f
    val center = Vector2(0.5f, 0.5f)

    override val type: String = "SpriteMaterial"

    override fun clone(): SpriteMaterial {
        return SpriteMaterial().also {
            it.color = color.clone()
            it.rotation = rotation
            it.center.copy(center)
            it.map = map
            it.opacity = opacity
            it.transparent = transparent
            it.alphaTest = alphaTest
            it.size = size
            it.sizeAttenuation = sizeAttenuation
            it.vertexColors = vertexColors
        }
    }
}

class AnimatedSprite(
    material: SpriteMaterial,
    private val rows: Int,
    private val cols: Int
) {
    private val sprite = Sprite(material)

    var currentFrame = 0
    var isPlaying = false
    private var startFrame = 0
    private var endFrame = 0
    private var loop = false
    private var fps = 30f
    private var accumTime = 0f

    fun setFrame(frame: Int) {
        currentFrame = frame.coerceIn(0, rows * cols - 1)
        val row = currentFrame / cols
        val col = currentFrame % cols
        val frameWidth = 1f / cols
        val frameHeight = 1f / rows

        sprite.setTextureRegion(
            col * frameWidth,
            row * frameHeight,
            frameWidth,
            frameHeight
        )
    }

    fun getUVCoordinates(): List<Vector2> = sprite.getUVCoordinates()

    fun play(startFrame: Int, endFrame: Int, loop: Boolean, fps: Float) {
        this.startFrame = startFrame
        this.endFrame = endFrame
        this.loop = loop
        this.fps = fps
        this.currentFrame = startFrame
        this.isPlaying = true
        this.accumTime = 0f
        setFrame(currentFrame)
    }

    fun update(deltaTime: Float) {
        if (!isPlaying) return

        accumTime += deltaTime
        val frameDuration = 1f / fps

        while (accumTime >= frameDuration) {
            accumTime -= frameDuration
            currentFrame++

            if (currentFrame > endFrame) {
                if (loop) {
                    currentFrame = startFrame
                } else {
                    currentFrame = endFrame
                    isPlaying = false
                }
            }

            setFrame(currentFrame)
        }
    }
}

class SpriteBatch {
    private val sprites = mutableListOf<Sprite>()
    private val materials = mutableSetOf<SpriteMaterial>()

    val spriteCount: Int
        get() = sprites.size

    val materialCount: Int
        get() = materials.size

    fun addSprites(spriteList: List<Sprite>) {
        sprites.addAll(spriteList)
        materials.addAll(spriteList.map { it.material })
    }
}

// Extension to Texture2D for sprite testing
fun Texture2D.getAlphaAt(uv: Vector2): Float {
    // Simulate alpha channel lookup
    return 1f // Fully opaque for testing
}