package io.kreekt.engine.math

object MatrixOps {
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (row in 0 until 4) {
            val r0 = a[row]
            val r1 = a[row + 4]
            val r2 = a[row + 8]
            val r3 = a[row + 12]

            out[row] = r0 * b[0] + r1 * b[1] + r2 * b[2] + r3 * b[3]
            out[row + 4] = r0 * b[4] + r1 * b[5] + r2 * b[6] + r3 * b[7]
            out[row + 8] = r0 * b[8] + r1 * b[9] + r2 * b[10] + r3 * b[11]
            out[row + 12] = r0 * b[12] + r1 * b[13] + r2 * b[14] + r3 * b[15]
        }
    }

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )
}
