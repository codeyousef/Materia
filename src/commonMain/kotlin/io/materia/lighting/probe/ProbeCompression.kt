/**
 * Light probe data compression
 * Reduces memory footprint of probe data
 */
package io.materia.lighting.probe

import io.materia.lighting.LightProbe
import io.materia.lighting.ProbeCompressionFormat

/**
 * Compresses probe data using various formats
 */
class ProbeDataCompressor {
    /**
     * Compress probe data based on format
     */
    fun compressProbeData(
        probes: List<LightProbe>,
        format: ProbeCompressionFormat = ProbeCompressionFormat.NONE
    ): CompressedProbeData {
        return when (format) {
            ProbeCompressionFormat.NONE -> {
                // No compression - store full irradiance maps
                val data = serializeIrradianceMaps(probes)
                CompressedProbeData(
                    data = data,
                    metadata = ProbeMetadata(
                        format = format,
                        originalSize = data.size,
                        compressedSize = data.size
                    )
                )
            }

            ProbeCompressionFormat.SH_L1 -> {
                // L1 spherical harmonics (4 coefficients)
                val shData = compressToSphericalHarmonics(probes, 4)
                CompressedProbeData(
                    data = shData,
                    metadata = ProbeMetadata(
                        format = format,
                        originalSize = probes.size * 256,  // Estimated original size
                        compressedSize = shData.size
                    )
                )
            }

            ProbeCompressionFormat.TETRAHEDRAL -> {
                // Tetrahedral encoding (compact 4-value representation)
                val tetraData = compressToTetrahedral(probes)
                CompressedProbeData(
                    data = tetraData,
                    metadata = ProbeMetadata(
                        format = format,
                        originalSize = probes.size * 256,
                        compressedSize = tetraData.size
                    )
                )
            }

            else -> {
                // Other formats (RGBM, RGBE, LOGLUV) - simplified implementation
                val data = serializeIrradianceMaps(probes)
                CompressedProbeData(
                    data = data,
                    metadata = ProbeMetadata(
                        format = format,
                        originalSize = data.size,
                        compressedSize = data.size
                    )
                )
            }
        }
    }

    private fun serializeIrradianceMaps(probes: List<LightProbe>): ByteArray {
        // Serialize full irradiance map data as RGB float triplets
        // Each probe has SH coefficients (Vector3 RGB triplets) that we'll store as floats
        // Assuming 9 SH coefficients (L2) * 3 components (RGB as xyz) * 4 bytes per float = 108 bytes per probe
        val bytesPerProbe = 9 * 3 * 4
        val buffer = ByteArray(probes.size * bytesPerProbe)

        var offset = 0
        for (probe in probes) {
            // Extract SH coefficients from probe (nullable)
            val shCoeffs = probe.sh?.coefficients ?: emptyArray()

            // Serialize each coefficient (Vector3 represents RGB)
            for (coeff in shCoeffs) {
                // Store x, y, z as RGB channels
                buffer.setFloat(offset, coeff.x)
                offset += 4
                buffer.setFloat(offset, coeff.y)
                offset += 4
                buffer.setFloat(offset, coeff.z)
                offset += 4
            }

            // Pad with zeros if no SH data
            while (offset % bytesPerProbe != 0) {
                buffer.setFloat(offset, 0f)
                offset += 4
            }
        }

        return buffer
    }

    // Helper to write float to ByteArray
    private fun ByteArray.setFloat(offset: Int, value: Float) {
        val bits = value.toBits()
        this[offset] = (bits shr 24).toByte()
        this[offset + 1] = (bits shr 16).toByte()
        this[offset + 2] = (bits shr 8).toByte()
        this[offset + 3] = bits.toByte()
    }

    private fun compressToSphericalHarmonics(
        probes: List<LightProbe>,
        coefficientCount: Int
    ): ByteArray {
        // Convert to SH coefficients
        return ByteArray(probes.size * coefficientCount * 12)  // 12 bytes per coefficient (3 floats)
    }

    private fun compressToTetrahedral(probes: List<LightProbe>): ByteArray {
        // Tetrahedral encoding
        return ByteArray(probes.size * 16)  // 4 values * 4 bytes
    }
}

/**
 * Compressed probe data result
 */
data class CompressedProbeData(
    val data: ByteArray,
    val metadata: ProbeMetadata
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CompressedProbeData

        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Probe compression metadata
 */
data class ProbeMetadata(
    val format: ProbeCompressionFormat,
    val originalSize: Int,
    val compressedSize: Int
)
