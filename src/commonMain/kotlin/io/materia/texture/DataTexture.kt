package io.materia.texture

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * Data texture implementation for procedural and computed textures
 * T108 - DataTexture for programmatically generated texture data
 */
class DataTexture(
    data: ByteArray,
    override val width: Int,
    override val height: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    type: TextureType = TextureType.UNSIGNED_BYTE,
    magFilter: TextureFilter = TextureFilter.NEAREST,
    minFilter: TextureFilter = TextureFilter.NEAREST,
    wrapS: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    wrapT: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    textureName: String = "DataTexture"
) : Texture() {

    // Data storage
    private var _data: ByteArray = data.copyOf()
    private var _floatData: FloatArray? = null
    private var _intData: IntArray? = null

    init {
        name = textureName
        this.format = format
        this.type = type
        this.magFilter = magFilter
        this.minFilter = minFilter
        this.wrapS = wrapS
        this.wrapT = wrapT
        this.generateMipmaps = false // Data textures typically don't use mipmaps
        this.flipY = false
        this.unpackAlignment = 1
    }

    companion object {
        /**
         * Create a data texture from float array
         */
        fun fromFloatArray(
            data: FloatArray,
            width: Int,
            height: Int,
            format: TextureFormat = TextureFormat.RGBA32F
        ): DataTexture = DataTexture(
            data = ByteArray(0), // Will be overridden
            width = width,
            height = height,
            format = format,
            type = TextureType.FLOAT,
            textureName = "FloatDataTexture"
        ).apply {
            setFloatData(data)
        }

        /**
         * Create a data texture from int array
         */
        fun fromIntArray(
            data: IntArray,
            width: Int,
            height: Int,
            format: TextureFormat = TextureFormat.RGBA8
        ): DataTexture = DataTexture(
            data = ByteArray(0), // Will be overridden
            width = width,
            height = height,
            format = format,
            type = TextureType.UNSIGNED_INT,
            textureName = "IntDataTexture"
        ).apply {
            setIntData(data)
        }

        /**
         * Create a lookup table texture (1D texture stored as thin 2D)
         */
        fun createLUT(
            values: FloatArray,
            size: Int = values.size / 4,
            format: TextureFormat = TextureFormat.RGBA32F
        ): DataTexture = fromFloatArray(
            data = values,
            width = size,
            height = 1,
            format = format
        ).apply {
            name = "LookupTable"
            wrapS = TextureWrap.CLAMP_TO_EDGE
            wrapT = TextureWrap.CLAMP_TO_EDGE
            minFilter = TextureFilter.LINEAR
            magFilter = TextureFilter.LINEAR
        }

        /**
         * Create a noise texture
         */
        fun createNoise(
            width: Int,
            height: Int,
            seed: Int = 12345,
            amplitude: Float = 1f,
            format: TextureFormat = TextureFormat.RGBA8
        ): DataTexture {
            val random = kotlin.random.Random(seed)
            val data = ByteArray(width * height * 4)

            for (i in data.indices step 4) {
                val noise = random.nextFloat() * amplitude
                val value = (noise * 255).toInt().coerceIn(0, 255).toByte()
                data[i] = value     // R
                data[i + 1] = value // G
                data[i + 2] = value // B
                data[i + 3] = 255.toByte() // A
            }

            return DataTexture(
                data = data,
                width = width,
                height = height,
                format = format,
                textureName = "NoiseTexture"
            ).apply {
                wrapS = TextureWrap.REPEAT
                wrapT = TextureWrap.REPEAT
            }
        }

        /**
         * Create a Perlin noise texture
         */
        fun createPerlinNoise(
            width: Int,
            height: Int,
            scale: Float = 0.1f,
            octaves: Int = 4,
            persistence: Float = 0.5f,
            seed: Int = 12345
        ): DataTexture {
            val noise = PerlinNoise(seed)
            val data = ByteArray(width * height * 4)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val noiseValue = noise.fractalNoise(
                        x * scale,
                        y * scale,
                        octaves,
                        persistence
                    )

                    val value = ((noiseValue + 1f) * 0.5f * 255).toInt().coerceIn(0, 255).toByte()
                    val index = (y * width + x) * 4

                    data[index] = value     // R
                    data[index + 1] = value // G
                    data[index + 2] = value // B
                    data[index + 3] = 255.toByte() // A
                }
            }

            return DataTexture(
                data = data,
                width = width,
                height = height,
                textureName = "PerlinNoise"
            ).apply {
                wrapS = TextureWrap.REPEAT
                wrapT = TextureWrap.REPEAT
            }
        }

        /**
         * Create a distance field texture
         */
        fun createDistanceField(
            width: Int,
            height: Int,
            shapes: List<Shape2D>,
            maxDistance: Float = 64f
        ): DataTexture {
            val data = ByteArray(width * height * 4)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val point = Vector2(x.toFloat(), y.toFloat())
                    var minDistance = Float.MAX_VALUE

                    // Find minimum distance to all shapes
                    for (shape in shapes) {
                        val distance = shape.distanceToPoint(point)
                        minDistance = kotlin.math.min(minDistance, kotlin.math.abs(distance))
                    }

                    // Normalize distance to [0, 1]
                    val normalizedDistance = (minDistance / maxDistance).coerceIn(0f, 1f)
                    val value = (normalizedDistance * 255).toInt().toByte()

                    val index = (y * width + x) * 4
                    data[index] = value     // R
                    data[index + 1] = value // G
                    data[index + 2] = value // B
                    data[index + 3] = 255.toByte() // A
                }
            }

            return DataTexture(
                data = data,
                width = width,
                height = height,
                textureName = "DistanceField"
            )
        }
    }

    /**
     * Set new byte data
     */
    fun setData(data: ByteArray) {
        _data = data.copyOf()
        _floatData = null
        _intData = null
        needsUpdate = true
        version++
    }

    /**
     * Set new float data
     */
    fun setFloatData(data: FloatArray) {
        _floatData = data.copyOf()
        _data = ByteArray(0)
        _intData = null
        type = TextureType.FLOAT
        needsUpdate = true
        version++
    }

    /**
     * Set new int data
     */
    fun setIntData(data: IntArray) {
        _intData = data.copyOf()
        _data = ByteArray(0)
        _floatData = null
        type = TextureType.UNSIGNED_INT
        needsUpdate = true
        version++
    }

    /**
     * Get current data as byte array
     */
    fun getData(): ByteArray = _data.copyOf()

    /**
     * Get current data as float array
     */
    fun getFloatData(): FloatArray? = _floatData?.copyOf()

    /**
     * Get current data as int array
     */
    fun getIntData(): IntArray? = _intData?.copyOf()

    /**
     * Get a pixel value at specific coordinates
     */
    fun getPixel(x: Int, y: Int): Color {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return Color.BLACK
        }

        val index = (y * width + x) * 4

        return when {
            _floatData != null -> {
                val fd = _floatData
                if (fd != null && index + 3 < fd.size) {
                    Color(fd[index], fd[index + 1], fd[index + 2])
                } else {
                    Color.BLACK
                }
            }

            _data.isNotEmpty() && index + 3 < _data.size -> Color(
                _data[index].toUByte().toFloat() / 255f,
                _data[index + 1].toUByte().toFloat() / 255f,
                _data[index + 2].toUByte().toFloat() / 255f
            )

            else -> Color.BLACK
        }
    }

    /**
     * Set a pixel value at specific coordinates
     */
    fun setPixel(x: Int, y: Int, color: Color) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return
        }

        val index = (y * width + x) * 4

        when {
            _floatData != null -> {
                val fd = _floatData
                if (fd != null && index + 3 < fd.size) {
                    fd[index] = color.r
                    fd[index + 1] = color.g
                    fd[index + 2] = color.b
                    fd[index + 3] = 1.0f // Full alpha
                }
            }

            _data.isNotEmpty() && index + 3 < _data.size -> {
                _data[index] = (color.r * 255).toInt().toByte()
                _data[index + 1] = (color.g * 255).toInt().toByte()
                _data[index + 2] = (color.b * 255).toInt().toByte()
                _data[index + 3] = 255.toByte() // Full alpha
            }
        }

        needsUpdate = true
        version++
    }

    /**
     * Clear the texture with a solid color
     */
    fun clear(color: Color = Color.BLACK) {
        when {
            _floatData != null -> {
                _floatData?.let { fd ->
                    for (i in fd.indices step 4) {
                        if (i + 3 < fd.size) {
                            fd[i] = color.r
                            fd[i + 1] = color.g
                            fd[i + 2] = color.b
                            fd[i + 3] = 1.0f // Full alpha
                        }
                    }
                }
            }

            else -> {
                val r = (color.r * 255).toInt().toByte()
                val g = (color.g * 255).toInt().toByte()
                val b = (color.b * 255).toInt().toByte()
                val a = 255.toByte() // Full alpha

                for (i in _data.indices step 4) {
                    _data[i] = r
                    _data[i + 1] = g
                    _data[i + 2] = b
                    _data[i + 3] = a
                }
            }
        }

        needsUpdate = true
        version++
    }

    /**
     * Apply a function to each pixel
     */
    fun mapPixels(transform: (x: Int, y: Int, color: Color) -> Color) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val currentColor = getPixel(x, y)
                val newColor = transform(x, y, currentColor)
                setPixel(x, y, newColor)
            }
        }
    }

    /**
     * Clone this data texture
     */
    override fun clone(): DataTexture = DataTexture(
        data = _data,
        width = width,
        height = height,
        format = format,
        type = type,
        magFilter = magFilter,
        minFilter = minFilter,
        wrapS = wrapS,
        wrapT = wrapT,
        textureName = name
    ).apply {
        copy(this@DataTexture)
        _floatData?.let { setFloatData(it) }
        _intData?.let { setIntData(it) }
    }

    /**
     * Dispose of this texture
     */
    override fun dispose() {
        super.dispose()
        _data = ByteArray(0)
        _floatData = null
        _intData = null
    }

    /**
     * Get the size of the texture data in bytes
     */
    fun getDataSize(): Int = when {
        _floatData != null -> (_floatData?.size ?: 0) * 4
        _intData != null -> (_intData?.size ?: 0) * 4
        else -> _data.size
    }
}

/**
 * Simple Perlin noise implementation
 */
private class PerlinNoise(seed: Int) {
    private val random = kotlin.random.Random(seed)
    private val permutation = IntArray(512) { random.nextInt(256) }

    fun noise(x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x).toInt() and 255
        val yi = kotlin.math.floor(y).toInt() and 255

        val xf = x - kotlin.math.floor(x)
        val yf = y - kotlin.math.floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]

        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)

        return lerp(x1, x2, v)
    }

    fun fractalNoise(x: Float, y: Float, octaves: Int, persistence: Float): Float {
        var total = 0f
        var frequency = 1f
        var amplitude = 1f
        var maxValue = 0f

        for (i in 0 until octaves) {
            total += noise(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2f
        }

        return total / maxValue
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)

    private fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }
}

/**
 * Simple shape interface for distance field calculation
 */
interface Shape2D {
    fun distanceToPoint(point: Vector2): Float
}

/**
 * Circle shape for distance fields
 */
data class Circle(val center: Vector2, val radius: Float) : Shape2D {
    override fun distanceToPoint(point: Vector2): Float {
        return point.distance(center) - radius
    }
}

/**
 * Rectangle shape for distance fields
 */
data class Rectangle(val center: Vector2, val size: Vector2) : Shape2D {
    override fun distanceToPoint(point: Vector2): Float {
        val d = Vector2(
            kotlin.math.abs(point.x - center.x) - size.x * 0.5f,
            kotlin.math.abs(point.y - center.y) - size.y * 0.5f
        )
        val outside = Vector2(kotlin.math.max(d.x, 0f), kotlin.math.max(d.y, 0f)).length()
        val inside = kotlin.math.min(kotlin.math.max(d.x, d.y), 0f)
        return outside + inside
    }
}