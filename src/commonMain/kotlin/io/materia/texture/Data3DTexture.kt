package io.materia.texture

import io.materia.core.math.Color
import io.materia.renderer.Texture3D
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * 3D data texture implementation for volume data and procedural fields.
 */
class Data3DTexture(
    data: ByteArray,
    override val width: Int,
    override val height: Int,
    override val depth: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    type: TextureType = TextureType.UNSIGNED_BYTE,
    magFilter: TextureFilter = TextureFilter.NEAREST,
    minFilter: TextureFilter = TextureFilter.NEAREST,
    wrapS: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    wrapT: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    var wrapR: TextureWrap = TextureWrap.CLAMP_TO_EDGE,
    textureName: String = "Data3DTexture"
) : Texture(), Texture3D {

    private var dataBytes: ByteArray = data.copyOf()
    private var floatData: FloatArray? = null
    private var intData: IntArray? = null

    init {
        name = textureName
        this.format = format
        this.type = type
        this.magFilter = magFilter
        this.minFilter = minFilter
        this.wrapS = wrapS
        this.wrapT = wrapT
        this.generateMipmaps = false
        this.flipY = false
        this.unpackAlignment = 1
    }

    companion object {
        fun fromFloatArray(
            data: FloatArray,
            width: Int,
            height: Int,
            depth: Int,
            format: TextureFormat = TextureFormat.RGBA32F
        ): Data3DTexture = Data3DTexture(
            data = ByteArray(0),
            width = width,
            height = height,
            depth = depth,
            format = format,
            type = TextureType.FLOAT,
            textureName = "FloatData3DTexture"
        ).apply {
            setFloatData(data)
        }

        fun fromIntArray(
            data: IntArray,
            width: Int,
            height: Int,
            depth: Int,
            format: TextureFormat = TextureFormat.RGBA8
        ): Data3DTexture = Data3DTexture(
            data = ByteArray(0),
            width = width,
            height = height,
            depth = depth,
            format = format,
            type = TextureType.UNSIGNED_INT,
            textureName = "IntData3DTexture"
        ).apply {
            setIntData(data)
        }

        fun solidColor(
            color: Color,
            width: Int = 1,
            height: Int = 1,
            depth: Int = 1
        ): Data3DTexture {
            val data = ByteArray(width * height * depth * 4) { index ->
                when (index % 4) {
                    0 -> (color.r * 255f).toInt().coerceIn(0, 255).toByte()
                    1 -> (color.g * 255f).toInt().coerceIn(0, 255).toByte()
                    2 -> (color.b * 255f).toInt().coerceIn(0, 255).toByte()
                    3 -> 255.toByte()
                    else -> 0
                }
            }
            return Data3DTexture(
                data = data,
                width = width,
                height = height,
                depth = depth,
                textureName = "SolidColor3DTexture"
            )
        }

        fun createNoise(
            width: Int,
            height: Int,
            depth: Int,
            seed: Int = 12345,
            amplitude: Float = 1f,
            format: TextureFormat = TextureFormat.RGBA8
        ): Data3DTexture {
            val random = kotlin.random.Random(seed)
            val data = ByteArray(width * height * depth * 4)

            for (index in data.indices step 4) {
                val noise = random.nextFloat() * amplitude
                val value = (noise * 255f).toInt().coerceIn(0, 255).toByte()
                data[index] = value
                data[index + 1] = value
                data[index + 2] = value
                data[index + 3] = 255.toByte()
            }

            return Data3DTexture(
                data = data,
                width = width,
                height = height,
                depth = depth,
                format = format,
                textureName = "Noise3DTexture"
            ).apply {
                wrapS = TextureWrap.REPEAT
                wrapT = TextureWrap.REPEAT
                wrapR = TextureWrap.REPEAT
            }
        }
    }

    fun setData(data: ByteArray) {
        dataBytes = data.copyOf()
        floatData = null
        intData = null
        needsUpdate = true
        version++
    }

    fun setFloatData(data: FloatArray) {
        floatData = data.copyOf()
        dataBytes = ByteArray(0)
        intData = null
        type = TextureType.FLOAT
        needsUpdate = true
        version++
    }

    fun setIntData(data: IntArray) {
        intData = data.copyOf()
        dataBytes = ByteArray(0)
        floatData = null
        type = TextureType.UNSIGNED_INT
        needsUpdate = true
        version++
    }

    fun getData(): ByteArray = dataBytes.copyOf()

    fun getFloatData(): FloatArray? = floatData?.copyOf()

    fun getIntData(): IntArray? = intData?.copyOf()

    fun getVoxel(x: Int, y: Int, z: Int): Color {
        if (x !in 0 until width || y !in 0 until height || z !in 0 until depth) {
            return Color.BLACK
        }

        val index = voxelIndex(x, y, z)
        return when {
            floatData != null -> {
                val values = floatData
                if (values != null && index + 3 < values.size) {
                    Color(values[index], values[index + 1], values[index + 2])
                } else {
                    Color.BLACK
                }
            }

            dataBytes.isNotEmpty() && index + 3 < dataBytes.size -> Color(
                dataBytes[index].toUByte().toFloat() / 255f,
                dataBytes[index + 1].toUByte().toFloat() / 255f,
                dataBytes[index + 2].toUByte().toFloat() / 255f
            )

            else -> Color.BLACK
        }
    }

    fun setVoxel(x: Int, y: Int, z: Int, color: Color) {
        if (x !in 0 until width || y !in 0 until height || z !in 0 until depth) {
            return
        }

        val index = voxelIndex(x, y, z)
        when {
            floatData != null -> {
                val values = floatData ?: return
                if (index + 3 >= values.size) return
                values[index] = color.r
                values[index + 1] = color.g
                values[index + 2] = color.b
                values[index + 3] = 1f
            }

            dataBytes.isNotEmpty() && index + 3 < dataBytes.size -> {
                dataBytes[index] = (color.r * 255f).toInt().coerceIn(0, 255).toByte()
                dataBytes[index + 1] = (color.g * 255f).toInt().coerceIn(0, 255).toByte()
                dataBytes[index + 2] = (color.b * 255f).toInt().coerceIn(0, 255).toByte()
                dataBytes[index + 3] = 255.toByte()
            }
        }

        needsUpdate = true
        version++
    }

    fun clear(color: Color = Color.BLACK) {
        when {
            floatData != null -> {
                val values = floatData ?: return
                for (index in values.indices step 4) {
                    if (index + 3 >= values.size) break
                    values[index] = color.r
                    values[index + 1] = color.g
                    values[index + 2] = color.b
                    values[index + 3] = 1f
                }
            }

            else -> {
                val r = (color.r * 255f).toInt().coerceIn(0, 255).toByte()
                val g = (color.g * 255f).toInt().coerceIn(0, 255).toByte()
                val b = (color.b * 255f).toInt().coerceIn(0, 255).toByte()
                for (index in dataBytes.indices step 4) {
                    dataBytes[index] = r
                    dataBytes[index + 1] = g
                    dataBytes[index + 2] = b
                    dataBytes[index + 3] = 255.toByte()
                }
            }
        }

        needsUpdate = true
        version++
    }

    fun mapVoxels(transform: (x: Int, y: Int, z: Int, color: Color) -> Color) {
        for (z in 0 until depth) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setVoxel(x, y, z, transform(x, y, z, getVoxel(x, y, z)))
                }
            }
        }
    }

    override fun clone(): Data3DTexture = Data3DTexture(
        data = dataBytes,
        width = width,
        height = height,
        depth = depth,
        format = format,
        type = type,
        magFilter = magFilter,
        minFilter = minFilter,
        wrapS = wrapS,
        wrapT = wrapT,
        wrapR = wrapR,
        textureName = name
    ).apply {
        copy(this@Data3DTexture)
        wrapR = this@Data3DTexture.wrapR
        floatData?.let { setFloatData(it) }
        intData?.let { setIntData(it) }
    }

    override fun dispose() {
        super.dispose()
        dataBytes = ByteArray(0)
        floatData = null
        intData = null
    }

    fun getDataSize(): Int = when {
        floatData != null -> (floatData?.size ?: 0) * 4
        intData != null -> (intData?.size ?: 0) * 4
        else -> dataBytes.size
    }

    private fun voxelIndex(x: Int, y: Int, z: Int): Int =
        (((z * height) + y) * width + x) * 4
}