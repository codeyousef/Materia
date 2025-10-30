package io.materia.texture

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * Cube texture implementation for environment mapping
 * T106 - CubeTexture for skyboxes and environment mapping
 */
class CubeTexture(
    override val size: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    magFilter: TextureFilter = TextureFilter.LINEAR,
    minFilter: TextureFilter = TextureFilter.LINEAR,
    textureName: String = "CubeTexture"
) : Texture(), io.materia.renderer.CubeTexture {

    override val width: Int get() = size
    override val height: Int get() = size

    init {
        name = textureName
        this.format = format
        this.magFilter = magFilter
        this.minFilter = minFilter
        this.mapping = TextureMapping.CUBE_REFLECTION
        this.wrapS = TextureWrap.CLAMP_TO_EDGE
        this.wrapT = TextureWrap.CLAMP_TO_EDGE
        this.flipY = false // Cube textures don't flip Y
    }

    // Face data storage
    private val faceData = Array<ByteArray?>(6) { null }
    private val faceFloatData = Array<FloatArray?>(6) { null }

    companion object {
        /**
         * Create a cube texture from 6 images
         */
        fun fromImages(
            images: Array<ByteArray>,
            size: Int,
            format: TextureFormat = TextureFormat.RGBA8
        ): CubeTexture {
            require(images.size == 6) { "CubeTexture requires exactly 6 images" }

            return CubeTexture(size, format).apply {
                images.forEachIndexed { index, data ->
                    setFaceData(CubeFace.values()[index], data)
                }
            }
        }

        /**
         * Create a cube texture from float data
         */
        fun fromFloatData(
            faces: Array<FloatArray>,
            size: Int,
            format: TextureFormat = TextureFormat.RGBA32F
        ): CubeTexture {
            require(faces.size == 6) { "CubeTexture requires exactly 6 face data arrays" }

            return CubeTexture(size, format).apply {
                type = TextureType.FLOAT
                faces.forEachIndexed { index, data ->
                    setFaceFloatData(CubeFace.values()[index], data)
                }
            }
        }

        /**
         * Create a solid color cube texture
         */
        fun solidColor(
            color: Color,
            size: Int = 64
        ): CubeTexture {
            val faceData = ByteArray(size * size * 4) { i ->
                when (i % 4) {
                    0 -> (color.r * 255).toInt().toByte()
                    1 -> (color.g * 255).toInt().toByte()
                    2 -> (color.b * 255).toInt().toByte()
                    3 -> 255.toByte() // Full alpha
                    else -> 0
                }
            }

            return CubeTexture(size).apply {
                CubeFace.values().forEach { face ->
                    setFaceData(face, faceData.copyOf())
                }
                name = "SolidColorCube"
            }
        }

        /**
         * Create a gradient cube texture (useful for skyboxes)
         */
        fun gradientSky(
            size: Int = 256,
            topColor: Color = Color(0.5f, 0.7f, 1f),    // Light blue
            bottomColor: Color = Color(1f, 0.9f, 0.7f),  // Warm white
            horizonColor: Color = Color(0.8f, 0.9f, 1f)  // Horizon blue
        ): CubeTexture {
            return CubeTexture(size).apply {
                CubeFace.values().forEach { face ->
                    val data = generateSkyFace(face, size, topColor, bottomColor, horizonColor)
                    setFaceData(face, data)
                }
                name = "GradientSky"
            }
        }

        private fun generateSkyFace(
            face: CubeFace,
            size: Int,
            topColor: Color,
            bottomColor: Color,
            horizonColor: Color
        ): ByteArray {
            val data = ByteArray(size * size * 4)

            for (y in 0 until size) {
                for (x in 0 until size) {
                    // Convert to [-1, 1] range
                    val u = (x.toFloat() / (size - 1)) * 2f - 1f
                    val v = (y.toFloat() / (size - 1)) * 2f - 1f

                    // Get the world direction for this cube face texel
                    val direction = getCubeDirection(face, u, v).normalize()

                    // Calculate gradient based on Y component (vertical gradient)
                    val t = (direction.y + 1f) * 0.5f // Convert from [-1,1] to [0,1]

                    val color = when {
                        t > 0.8f -> {
                            // Top region - interpolate between horizon and top
                            val localT = (t - 0.8f) / 0.2f
                            horizonColor.clone().lerp(topColor, localT)
                        }

                        t < 0.2f -> {
                            // Bottom region - interpolate between bottom and horizon
                            val localT = t / 0.2f
                            bottomColor.clone().lerp(horizonColor, localT)
                        }

                        else -> {
                            // Middle region - just horizon color
                            horizonColor
                        }
                    }

                    val index = (y * size + x) * 4
                    data[index] = (color.r * 255).toInt().toByte()
                    data[index + 1] = (color.g * 255).toInt().toByte()
                    data[index + 2] = (color.b * 255).toInt().toByte()
                    data[index + 3] = 255.toByte() // Full alpha
                }
            }

            return data
        }

        private fun getCubeDirection(face: CubeFace, u: Float, v: Float): Vector3 {
            return when (face) {
                CubeFace.POSITIVE_X -> Vector3(1f, -v, -u)
                CubeFace.NEGATIVE_X -> Vector3(-1f, -v, u)
                CubeFace.POSITIVE_Y -> Vector3(u, 1f, v)
                CubeFace.NEGATIVE_Y -> Vector3(u, -1f, -v)
                CubeFace.POSITIVE_Z -> Vector3(u, -v, 1f)
                CubeFace.NEGATIVE_Z -> Vector3(-u, -v, -1f)
            }
        }
    }

    /**
     * Set data for a specific cube face
     */
    fun setFaceData(face: CubeFace, data: ByteArray) {
        faceData[face.ordinal] = data.copyOf()
        faceFloatData[face.ordinal] = null
        needsUpdate = true
        version++
    }

    /**
     * Set float data for a specific cube face
     */
    fun setFaceFloatData(face: CubeFace, data: FloatArray) {
        faceFloatData[face.ordinal] = data.copyOf()
        faceData[face.ordinal] = null
        type = TextureType.FLOAT
        needsUpdate = true
        version++
    }

    /**
     * Get data for a specific cube face
     */
    fun getFaceData(face: CubeFace): ByteArray? = faceData[face.ordinal]?.copyOf()

    /**
     * Get float data for a specific cube face
     */
    fun getFaceFloatData(face: CubeFace): FloatArray? = faceFloatData[face.ordinal]?.copyOf()

    /**
     * Check if a face has data
     */
    fun hasFaceData(face: CubeFace): Boolean =
        faceData[face.ordinal] != null || faceFloatData[face.ordinal] != null

    /**
     * Check if all faces have data
     */
    fun isComplete(): Boolean = CubeFace.values().all { hasFaceData(it) }

    /**
     * Get the total size of all face data in bytes
     */
    fun getTotalDataSize(): Int {
        var totalSize = 0
        for (i in 0..5) {
            faceData[i]?.let { totalSize += it.size }
            faceFloatData[i]?.let { totalSize += it.size * 4 }
        }
        return totalSize
    }

    /**
     * Clone this cube texture
     */
    override fun clone(): CubeTexture = CubeTexture(
        size = size,
        format = format,
        magFilter = magFilter,
        minFilter = minFilter,
        textureName = name
    ).apply {
        copy(this@CubeTexture)

        // Copy face data
        CubeFace.values().forEach { face ->
            faceData[face.ordinal]?.let { setFaceData(face, it) }
            faceFloatData[face.ordinal]?.let { setFaceFloatData(face, it) }
        }
    }

    /**
     * Dispose of this texture
     */
    override fun dispose() {
        super.dispose()
        for (i in 0..5) {
            faceData[i] = null
            faceFloatData[i] = null
        }
    }

    /**
     * Convert to equirectangular texture (useful for processing)
     */
    fun toEquirectangular(width: Int = size * 4, height: Int = size * 2): Texture2D {
        val data = ByteArray(width * height * 4)

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Convert pixel to spherical coordinates
                val u = x.toFloat() / width
                val v = y.toFloat() / height

                val phi = u * 2f * kotlin.math.PI.toFloat() // Longitude [0, 2π]
                val theta = v * kotlin.math.PI.toFloat()    // Latitude [0, π]

                // Convert to Cartesian direction
                val sinTheta = kotlin.math.sin(theta)
                val direction = Vector3(
                    sinTheta * kotlin.math.cos(phi),
                    kotlin.math.cos(theta),
                    sinTheta * kotlin.math.sin(phi)
                )

                // Sample from cube texture
                val color = sampleDirection(direction)

                val index = (y * width + x) * 4
                data[index] = (color.r * 255).toInt().toByte()
                data[index + 1] = (color.g * 255).toInt().toByte()
                data[index + 2] = (color.b * 255).toInt().toByte()
                data[index + 3] = 255.toByte() // Full alpha
            }
        }

        return Texture2D.fromImageData(width, height, data, format).apply {
            name = "${this@CubeTexture.name}_equirectangular"
        }
    }

    /**
     * Sample a face of the cube texture at UV coordinates
     */
    override fun sampleFace(face: Int, u: Float, v: Float): Vector3 {
        val cubeFace = CubeFace.values().getOrNull(face) ?: return Vector3.ZERO.clone()

        // Convert UV coordinates [0,1] to pixel coordinates
        val x = (u * (size - 1)).toInt().coerceIn(0, size - 1)
        val y = (v * (size - 1)).toInt().coerceIn(0, size - 1)

        // Sample the face data
        val data = faceData[cubeFace.ordinal]
        val floatData = faceFloatData[cubeFace.ordinal]

        return when {
            data != null -> {
                val index = (y * size + x) * 4
                Vector3(
                    data[index].toUByte().toFloat() / 255f,
                    data[index + 1].toUByte().toFloat() / 255f,
                    data[index + 2].toUByte().toFloat() / 255f
                )
            }

            floatData != null -> {
                val index = (y * size + x) * 4
                Vector3(
                    floatData[index],
                    floatData[index + 1],
                    floatData[index + 2]
                )
            }

            else -> Vector3.ZERO.clone()
        }
    }

    /**
     * Sample the cube texture in a given direction
     */
    private fun sampleDirection(direction: Vector3): Color {
        // Find which face to sample from
        val absX = kotlin.math.abs(direction.x)
        val absY = kotlin.math.abs(direction.y)
        val absZ = kotlin.math.abs(direction.z)

        val (face, u, v) = when {
            absX >= absY && absX >= absZ -> {
                if (direction.x > 0) {
                    Triple(
                        CubeFace.POSITIVE_X,
                        -direction.z / direction.x,
                        -direction.y / direction.x
                    )
                } else {
                    Triple(
                        CubeFace.NEGATIVE_X,
                        direction.z / direction.x,
                        -direction.y / direction.x
                    )
                }
            }

            absY >= absZ -> {
                if (direction.y > 0) {
                    Triple(
                        CubeFace.POSITIVE_Y,
                        direction.x / direction.y,
                        direction.z / direction.y
                    )
                } else {
                    Triple(
                        CubeFace.NEGATIVE_Y,
                        direction.x / direction.y,
                        -direction.z / direction.y
                    )
                }
            }

            else -> {
                if (direction.z > 0) {
                    Triple(
                        CubeFace.POSITIVE_Z,
                        direction.x / direction.z,
                        -direction.y / direction.z
                    )
                } else {
                    Triple(
                        CubeFace.NEGATIVE_Z,
                        -direction.x / direction.z,
                        -direction.y / direction.z
                    )
                }
            }
        }

        // Convert [-1,1] to [0,1] and then to pixel coordinates
        val x = ((u + 1f) * 0.5f * (size - 1)).toInt().coerceIn(0, size - 1)
        val y = ((v + 1f) * 0.5f * (size - 1)).toInt().coerceIn(0, size - 1)

        // Sample the face data
        val data = faceData[face.ordinal]
        val floatData = faceFloatData[face.ordinal]

        return when {
            data != null -> {
                val index = (y * size + x) * 4
                Color(
                    data[index].toUByte().toFloat() / 255f,
                    data[index + 1].toUByte().toFloat() / 255f,
                    data[index + 2].toUByte().toFloat() / 255f
                )
            }

            floatData != null -> {
                val index = (y * size + x) * 4
                Color(
                    floatData[index],
                    floatData[index + 1],
                    floatData[index + 2]
                )
            }

            else -> Color.BLACK
        }
    }
}

/**
 * Cube face enumeration
 */
enum class CubeFace {
    POSITIVE_X, // Right
    NEGATIVE_X, // Left
    POSITIVE_Y, // Top
    NEGATIVE_Y, // Bottom
    POSITIVE_Z, // Front
    NEGATIVE_Z  // Back
}