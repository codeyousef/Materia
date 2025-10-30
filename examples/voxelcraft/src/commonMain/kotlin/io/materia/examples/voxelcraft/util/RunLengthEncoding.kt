package io.materia.examples.voxelcraft.util

/**
 * Run-Length Encoding (RLE) compression utility for chunk data
 *
 * RLE reduces storage size by encoding consecutive identical values as (value, count) pairs.
 * This is highly effective for voxel data where many consecutive blocks are the same type
 * (e.g., large areas of Air or Stone).
 *
 * Format: [blockID, count, blockID, count, ...]
 * - blockID: Byte (0-7 for BlockType)
 * - count: Byte (1-255)
 *
 * For runs longer than 255, multiple pairs are emitted:
 * Example: 500 Air blocks → [0, 255, 0, 245]
 *
 * Expected compression ratio: 90%+ for typical Minecraft-style terrain
 * - Empty chunks (all Air): 65,536 bytes → 2 bytes (99.997% reduction)
 * - Typical chunks: 65,536 bytes → 2,000-6,000 bytes (90-97% reduction)
 *
 * Research: research.md Section "LocalStorage Compression"
 */
object RunLengthEncoding {

    /**
     * Encode a ByteArray using Run-Length Encoding
     *
     * @param data Input ByteArray to compress (typically chunk blocks: 65,536 bytes)
     * @return Compressed ByteArray with [value, count, value, count, ...] format
     */
    fun encode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)

        val encoded = mutableListOf<Byte>()
        var currentValue = data[0]
        var count = 1

        for (i in 1 until data.size) {
            if (data[i] == currentValue && count < 255) {
                // Continue the current run
                count++
            } else {
                // Emit the current run
                encoded.add(currentValue)
                encoded.add(count.toByte())

                // Start new run
                currentValue = data[i]
                count = 1
            }
        }

        // Emit the final run
        encoded.add(currentValue)
        encoded.add(count.toByte())

        return encoded.toByteArray()
    }

    /**
     * Decode a Run-Length Encoded ByteArray
     *
     * @param encoded Compressed ByteArray in [value, count, value, count, ...] format
     * @return Decompressed ByteArray
     * @throws IllegalArgumentException if encoded data is malformed (odd length)
     */
    fun decode(encoded: ByteArray): ByteArray {
        require(encoded.size % 2 == 0) {
            "RLE encoded data must have even length (value, count pairs), got ${encoded.size}"
        }

        val decoded = mutableListOf<Byte>()

        var i = 0
        while (i < encoded.size) {
            val value = encoded[i]
            val count = encoded[i + 1].toInt() and 0xFF // Convert to unsigned

            // Expand the run
            repeat(count) {
                decoded.add(value)
            }

            i += 2
        }

        return decoded.toByteArray()
    }

    /**
     * Calculate compression ratio for given data
     *
     * @param original Original uncompressed data
     * @param compressed Compressed data
     * @return Compression ratio (0.0 to 1.0, lower is better)
     */
    fun compressionRatio(original: ByteArray, compressed: ByteArray): Double {
        if (original.isEmpty()) return 1.0
        return compressed.size.toDouble() / original.size.toDouble()
    }

    /**
     * Estimate compressed size without actually compressing
     *
     * Useful for predicting localStorage usage before saving.
     *
     * @param data Data to estimate compression for
     * @return Estimated compressed size in bytes
     */
    fun estimateCompressedSize(data: ByteArray): Int {
        if (data.isEmpty()) return 0

        var runs = 0
        var currentValue = data[0]
        var count = 1

        for (i in 1 until data.size) {
            if (data[i] == currentValue && count < 255) {
                count++
            } else {
                runs++
                currentValue = data[i]
                count = 1
            }
        }
        runs++ // Final run

        return runs * 2 // Each run is 2 bytes (value, count)
    }
}

/**
 * Extension function for ByteArray to encode using RLE
 */
fun ByteArray.encodeRLE(): ByteArray = RunLengthEncoding.encode(this)

/**
 * Extension function for ByteArray to decode from RLE
 */
fun ByteArray.decodeRLE(): ByteArray = RunLengthEncoding.decode(this)
