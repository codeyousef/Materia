package io.materia.examples.voxelcraft

import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Simplex Noise implementation for terrain generation
 *
 * Based on Stefan Gustavson's implementation of Ken Perlin's Simplex Noise.
 * Provides 2D and 3D noise functions for procedural terrain generation.
 *
 * Usage:
 * - 2D noise: Height maps for terrain surface (hills, valleys)
 * - 3D noise: Caves and overhangs (volumetric features)
 *
 * Output range: -1.0 to 1.0 (approximately)
 *
 * Research: research.md "Terrain Generation Algorithm"
 */
class SimplexNoise(seed: Long) {
    private val perm = IntArray(512)
    private val permMod12 = IntArray(512)

    init {
        val random = Random(seed)
        val p = IntArray(256) { it }

        // Shuffle using Fisher-Yates
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = p[i]
            p[i] = p[j]
            p[j] = temp
        }

        // Duplicate permutation array
        for (i in 0..511) {
            perm[i] = p[i and 255]
            permMod12[i] = perm[i] % 12
        }
    }

    /**
     * 2D Simplex Noise
     *
     * Used for terrain height maps. Returns continuous noise value
     * that can be scaled and used for terrain elevation.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return Noise value in range approximately -1.0 to 1.0
     */
    fun eval(x: Double, y: Double): Double {
        val F2 = 0.5 * (sqrt(3.0) - 1.0)
        val G2 = (3.0 - sqrt(3.0)) / 6.0

        val s = (x + y) * F2
        val i = floor(x + s).toInt()
        val j = floor(y + s).toInt()

        val t = (i + j) * G2
        val X0 = i - t
        val Y0 = j - t
        val x0 = x - X0
        val y0 = y - Y0

        val i1: Int
        val j1: Int
        if (x0 > y0) {
            i1 = 1
            j1 = 0
        } else {
            i1 = 0
            j1 = 1
        }

        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2

        val ii = i and 255
        val jj = j and 255

        val gi0 = permMod12[ii + perm[jj]]
        val gi1 = permMod12[ii + i1 + perm[jj + j1]]
        val gi2 = permMod12[ii + 1 + perm[jj + 1]]

        var n0 = 0.0
        var t0 = 0.5 - x0 * x0 - y0 * y0
        if (t0 >= 0) {
            t0 *= t0
            n0 = t0 * t0 * dot(grad3[gi0], x0, y0)
        }

        var n1 = 0.0
        var t1 = 0.5 - x1 * x1 - y1 * y1
        if (t1 >= 0) {
            t1 *= t1
            n1 = t1 * t1 * dot(grad3[gi1], x1, y1)
        }

        var n2 = 0.0
        var t2 = 0.5 - x2 * x2 - y2 * y2
        if (t2 >= 0) {
            t2 *= t2
            n2 = t2 * t2 * dot(grad3[gi2], x2, y2)
        }

        return 70.0 * (n0 + n1 + n2)
    }

    /**
     * 3D Simplex Noise
     *
     * Used for caves and volumetric features. Returns continuous noise
     * value for 3D space.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Noise value in range approximately -1.0 to 1.0
     */
    fun eval(x: Double, y: Double, z: Double): Double {
        val F3 = 1.0 / 3.0
        val G3 = 1.0 / 6.0

        val s = (x + y + z) * F3
        val i = floor(x + s).toInt()
        val j = floor(y + s).toInt()
        val k = floor(z + s).toInt()

        val t = (i + j + k) * G3
        val X0 = i - t
        val Y0 = j - t
        val Z0 = k - t
        val x0 = x - X0
        val y0 = y - Y0
        val z0 = z - Z0

        val i1: Int
        val j1: Int
        val k1: Int
        val i2: Int
        val j2: Int
        val k2: Int

        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0
            }
        }

        val x1 = x0 - i1 + G3
        val y1 = y0 - j1 + G3
        val z1 = z0 - k1 + G3
        val x2 = x0 - i2 + 2.0 * G3
        val y2 = y0 - j2 + 2.0 * G3
        val z2 = z0 - k2 + 2.0 * G3
        val x3 = x0 - 1.0 + 3.0 * G3
        val y3 = y0 - 1.0 + 3.0 * G3
        val z3 = z0 - 1.0 + 3.0 * G3

        val ii = i and 255
        val jj = j and 255
        val kk = k and 255

        val gi0 = permMod12[ii + perm[jj + perm[kk]]]
        val gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]]
        val gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]]
        val gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]]

        var n0 = 0.0
        var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0
        if (t0 >= 0) {
            t0 *= t0
            n0 = t0 * t0 * dot(grad3[gi0], x0, y0, z0)
        }

        var n1 = 0.0
        var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1
        if (t1 >= 0) {
            t1 *= t1
            n1 = t1 * t1 * dot(grad3[gi1], x1, y1, z1)
        }

        var n2 = 0.0
        var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2
        if (t2 >= 0) {
            t2 *= t2
            n2 = t2 * t2 * dot(grad3[gi2], x2, y2, z2)
        }

        var n3 = 0.0
        var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3
        if (t3 >= 0) {
            t3 *= t3
            n3 = t3 * t3 * dot(grad3[gi3], x3, y3, z3)
        }

        return 32.0 * (n0 + n1 + n2 + n3)
    }

    private fun dot(g: IntArray, x: Double, y: Double): Double {
        return g[0] * x + g[1] * y
    }

    private fun dot(g: IntArray, x: Double, y: Double, z: Double): Double {
        return g[0] * x + g[1] * y + g[2] * z
    }

    companion object {
        private val grad3 = arrayOf(
            intArrayOf(1, 1, 0), intArrayOf(-1, 1, 0), intArrayOf(1, -1, 0),
            intArrayOf(-1, -1, 0), intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1),
            intArrayOf(1, 0, -1), intArrayOf(-1, 0, -1), intArrayOf(0, 1, 1),
            intArrayOf(0, -1, 1), intArrayOf(0, 1, -1), intArrayOf(0, -1, -1)
        )
    }
}
