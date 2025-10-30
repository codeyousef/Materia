package io.materia.texture

import io.materia.core.math.Color
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * 2D Texture implementation following Three.js Texture API
 * T105 - Standard 2D texture with comprehensive features
 */
class Texture2D(
    override val width: Int,
    override val height: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    wrapS: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    wrapT: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    magFilter: TextureFilter = TextureFilter.LINEAR,
    minFilter: TextureFilter = TextureFilter.LINEAR,
    textureName: String = "Texture2D"
) : Texture() {

    init {
        name = textureName
        this.format = format
        this.wrapS = wrapS
        this.wrapT = wrapT
        this.magFilter = magFilter
        this.minFilter = minFilter
    }

    // Image data
    private var _data: ByteArray? = null
    private var _floatData: FloatArray? = null

    companion object {
        /**
         * Create a texture from image data
         */
        fun fromImageData(
            width: Int,
            height: Int,
            data: ByteArray,
            format: TextureFormat = TextureFormat.RGBA8
        ): Texture2D = Texture2D(width, height, format).apply {
            setData(data)
        }

        /**
         * Create a texture from float data
         */
        fun fromFloatData(
            width: Int,
            height: Int,
            data: FloatArray,
            format: TextureFormat = TextureFormat.RGBA32F
        ): Texture2D = Texture2D(width, height, format).apply {
            setFloatData(data)
            type = TextureType.FLOAT
        }

        /**
         * Create a solid color texture
         */
        fun solidColor(
            color: Color,
            width: Int = 1,
            height: Int = 1
        ): Texture2D {
            val data = ByteArray(width * height * 4) { i ->
                when (i % 4) {
                    0 -> (color.r * 255).toInt().toByte()
                    1 -> (color.g * 255).toInt().toByte()
                    2 -> (color.b * 255).toInt().toByte()
                    3 -> 255.toByte() // Full alpha
                    else -> 0
                }
            }
            return fromImageData(width, height, data)
        }

        /**
         * Create a checkerboard pattern texture
         */
        fun checkerboard(
            size: Int = 256,
            checkSize: Int = 32,
            color1: Color = Color.WHITE,
            color2: Color = Color.BLACK
        ): Texture2D {
            val data = ByteArray(size * size * 4)

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val checkX = (x / checkSize) % 2
                    val checkY = (y / checkSize) % 2
                    val useColor1 = (checkX + checkY) % 2 == 0
                    val color = if (useColor1) color1 else color2

                    val index = (y * size + x) * 4
                    data[index] = (color.r * 255).toInt().toByte()
                    data[index + 1] = (color.g * 255).toInt().toByte()
                    data[index + 2] = (color.b * 255).toInt().toByte()
                    data[index + 3] = 255.toByte() // Full alpha
                }
            }

            return fromImageData(size, size, data).apply {
                name = "Checkerboard"
                wrapS = TextureWrap.REPEAT
                wrapT = TextureWrap.REPEAT
            }
        }

        /**
         * Create a gradient texture
         */
        fun gradient(
            width: Int = 256,
            height: Int = 256,
            colorStart: Color = Color.BLACK,
            colorEnd: Color = Color.WHITE,
            direction: GradientDirection = GradientDirection.HORIZONTAL
        ): Texture2D {
            val data = ByteArray(width * height * 4)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val t = when (direction) {
                        GradientDirection.HORIZONTAL -> x.toFloat() / (width - 1)
                        GradientDirection.VERTICAL -> y.toFloat() / (height - 1)
                        GradientDirection.DIAGONAL -> (x + y).toFloat() / (width + height - 2)
                        GradientDirection.RADIAL -> {
                            val cx = width / 2f
                            val cy = height / 2f
                            val dx = x - cx
                            val dy = y - cy
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            val maxDistance = kotlin.math.sqrt(cx * cx + cy * cy)
                            (distance / maxDistance).coerceIn(0f, 1f)
                        }
                    }

                    val color = colorStart.clone().lerp(colorEnd, t)
                    val index = (y * width + x) * 4
                    data[index] = (color.r * 255).toInt().toByte()
                    data[index + 1] = (color.g * 255).toInt().toByte()
                    data[index + 2] = (color.b * 255).toInt().toByte()
                    data[index + 3] = 255.toByte() // Full alpha
                }
            }

            return fromImageData(width, height, data).apply {
                name = "Gradient"
            }
        }
    }

    /**
     * Set texture data from byte array
     */
    fun setData(data: ByteArray) {
        _data = data.copyOf()
        _floatData = null
        needsUpdate = true
        version++
    }

    /**
     * Set texture data from float array
     */
    fun setFloatData(data: FloatArray) {
        _floatData = data.copyOf()
        _data = null
        type = TextureType.FLOAT
        needsUpdate = true
        version++
    }

    /**
     * Get texture data as byte array
     */
    fun getData(): ByteArray? = _data?.copyOf()

    /**
     * Get texture data as float array
     */
    fun getFloatData(): FloatArray? = _floatData?.copyOf()

    /**
     * Check if texture has data
     */
    fun hasData(): Boolean = _data != null || _floatData != null

    /**
     * Get the size of the texture data in bytes
     */
    fun getDataSize(): Int = when {
        _data != null -> _data?.size ?: 0
        _floatData != null -> (_floatData?.size ?: 0) * 4 // 4 bytes per float
        else -> 0
    }

    /**
     * Clone this texture
     */
    override fun clone(): Texture2D = Texture2D(
        width = width,
        height = height,
        format = format,
        wrapS = wrapS,
        wrapT = wrapT,
        magFilter = magFilter,
        minFilter = minFilter,
        textureName = name
    ).apply {
        copy(this@Texture2D)
        _data?.let { setData(it) }
        _floatData?.let { setFloatData(it) }
    }

    /**
     * Resize the texture (creates new data array)
     */
    fun resize(newWidth: Int, newHeight: Int): Texture2D {
        // Simple nearest-neighbor resize
        val oldData = _data
        val oldFloatData = _floatData

        return Texture2D(
            newWidth,
            newHeight,
            format,
            wrapS,
            wrapT,
            magFilter,
            minFilter,
            name
        ).apply {
            copy(this@Texture2D)

            if (oldData != null) {
                val newData = ByteArray(newWidth * newHeight * 4)
                resizeImageData(oldData, width, height, newData, newWidth, newHeight)
                setData(newData)
            } else if (oldFloatData != null) {
                val newFloatData = FloatArray(newWidth * newHeight * 4)
                resizeFloatImageData(oldFloatData, width, height, newFloatData, newWidth, newHeight)
                setFloatData(newFloatData)
            }
        }
    }

    /**
     * Dispose of this texture
     */
    override fun dispose() {
        super.dispose()
        _data = null
        _floatData = null
    }

    private fun resizeImageData(
        oldData: ByteArray, oldWidth: Int, oldHeight: Int,
        newData: ByteArray, newWidth: Int, newHeight: Int
    ) {
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val oldX = (x * oldWidth / newWidth).coerceIn(0, oldWidth - 1)
                val oldY = (y * oldHeight / newHeight).coerceIn(0, oldHeight - 1)

                val oldIndex = (oldY * oldWidth + oldX) * 4
                val newIndex = (y * newWidth + x) * 4

                for (c in 0..3) {
                    newData[newIndex + c] = oldData[oldIndex + c]
                }
            }
        }
    }

    private fun resizeFloatImageData(
        oldData: FloatArray, oldWidth: Int, oldHeight: Int,
        newData: FloatArray, newWidth: Int, newHeight: Int
    ) {
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val oldX = (x * oldWidth / newWidth).coerceIn(0, oldWidth - 1)
                val oldY = (y * oldHeight / newHeight).coerceIn(0, oldHeight - 1)

                val oldIndex = (oldY * oldWidth + oldX) * 4
                val newIndex = (y * newWidth + x) * 4

                for (c in 0..3) {
                    newData[newIndex + c] = oldData[oldIndex + c]
                }
            }
        }
    }
}

/**
 * Gradient directions for generated textures
 */
enum class GradientDirection {
    HORIZONTAL, VERTICAL, DIAGONAL, RADIAL
}