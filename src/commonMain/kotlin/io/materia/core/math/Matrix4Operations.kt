package io.materia.core.math

/**
 * Matrix4 mathematical operations including multiply, invert, transpose, determinant
 */

/**
 * Multiplies this matrix by another matrix
 */
internal fun Matrix4.multiplyMatrix(matrix: Matrix4): Matrix4 {
    return multiplyMatricesInternal(this, matrix)
}

/**
 * Multiplies another matrix by this matrix (order matters!)
 */
internal fun Matrix4.premultiplyMatrix(matrix: Matrix4): Matrix4 {
    return multiplyMatricesInternal(matrix, this)
}

/**
 * Multiplies two matrices and stores result in this matrix
 */
internal fun Matrix4.multiplyMatricesInternal(a: Matrix4, b: Matrix4): Matrix4 {
    val ae = a.elements
    val be = b.elements
    val te = elements

    val a11 = ae[0];
    val a12 = ae[4];
    val a13 = ae[8];
    val a14 = ae[12]
    val a21 = ae[1];
    val a22 = ae[5];
    val a23 = ae[9];
    val a24 = ae[13]
    val a31 = ae[2];
    val a32 = ae[6];
    val a33 = ae[10];
    val a34 = ae[14]
    val a41 = ae[3];
    val a42 = ae[7];
    val a43 = ae[11];
    val a44 = ae[15]

    val b11 = be[0];
    val b12 = be[4];
    val b13 = be[8];
    val b14 = be[12]
    val b21 = be[1];
    val b22 = be[5];
    val b23 = be[9];
    val b24 = be[13]
    val b31 = be[2];
    val b32 = be[6];
    val b33 = be[10];
    val b34 = be[14]
    val b41 = be[3];
    val b42 = be[7];
    val b43 = be[11];
    val b44 = be[15]

    te[0] = a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41
    te[4] = a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42
    te[8] = a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43
    te[12] = a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44

    te[1] = a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41
    te[5] = a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42
    te[9] = a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43
    te[13] = a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44

    te[2] = a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41
    te[6] = a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42
    te[10] = a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43
    te[14] = a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44

    te[3] = a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41
    te[7] = a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42
    te[11] = a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43
    te[15] = a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44

    return this
}

/**
 * Calculates the determinant of this matrix
 */
internal fun Matrix4.calculateDeterminant(): Float {
    val n11 = elements[0];
    val n12 = elements[4];
    val n13 = elements[8];
    val n14 = elements[12]
    val n21 = elements[1];
    val n22 = elements[5];
    val n23 = elements[9];
    val n24 = elements[13]
    val n31 = elements[2];
    val n32 = elements[6];
    val n33 = elements[10];
    val n34 = elements[14]
    val n41 = elements[3];
    val n42 = elements[7];
    val n43 = elements[11];
    val n44 = elements[15]

    return (
            n41 * (
                    +n14 * n23 * n32
                            - n13 * n24 * n32
                            - n14 * n22 * n33
                            + n12 * n24 * n33
                            + n13 * n22 * n34
                            - n12 * (n23 * n34)
                    ) +
                    n42 * (
                    +n11 * n23 * n34
                            - n11 * n24 * n33
                            + n14 * n21 * n33
                            - n13 * n21 * n34
                            + n13 * n24 * n31
                            - n14 * (n23 * n31)
                    ) +
                    n43 * (
                    +n11 * n24 * n32
                            - n11 * n22 * n34
                            - n14 * n21 * n32
                            + n12 * n21 * n34
                            + n14 * n22 * n31
                            - n12 * (n24 * n31)
                    ) +
                    n44 * (
                    -n13 * n22 * n31
                            - n11 * n23 * n32
                            + n11 * n22 * n33
                            + n13 * n21 * n32
                            - n12 * n21 * n33
                            + n12 * (n23 * n31)
                    )
            )
}

/**
 * Inverts this matrix
 */
internal fun Matrix4.invertMatrix(): Matrix4 {
    val n11 = elements[0];
    val n21 = elements[1];
    val n31 = elements[2];
    val n41 = elements[3]
    val n12 = elements[4];
    val n22 = elements[5];
    val n32 = elements[6];
    val n42 = elements[7]
    val n13 = elements[8];
    val n23 = elements[9];
    val n33 = elements[10];
    val n43 = elements[11]
    val n14 = elements[12];
    val n24 = elements[13];
    val n34 = elements[14];
    val n44 = elements[15]

    val t11 =
        n23 * n34 * n42 - n24 * n33 * n42 + n24 * n32 * n43 - n22 * n34 * n43 - n23 * n32 * n44 + n22 * n33 * n44
    val t12 =
        n14 * n33 * n42 - n13 * n34 * n42 - n14 * n32 * n43 + n12 * n34 * n43 + n13 * n32 * n44 - n12 * n33 * n44
    val t13 =
        n13 * n24 * n42 - n14 * n23 * n42 + n14 * n22 * n43 - n12 * n24 * n43 - n13 * n22 * n44 + n12 * n23 * n44
    val t14 =
        n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34

    val det = n11 * t11 + n21 * t12 + n31 * t13 + n41 * t14

    val epsilon = 1e-10f
    if (kotlin.math.abs(det) < epsilon) {
        throw IllegalArgumentException("Cannot invert matrix with determinant near zero: $det")
    }

    val detInv = 1f / det

    elements[0] = t11 * detInv
    elements[1] =
        (n24 * n33 * n41 - n23 * n34 * n41 - n24 * n31 * n43 + n21 * n34 * n43 + n23 * n31 * n44 - n21 * (n33 * n44)) * detInv
    elements[2] =
        (n22 * n34 * n41 - n24 * n32 * n41 + n24 * n31 * n42 - n21 * n34 * n42 - n22 * n31 * n44 + n21 * (n32 * n44)) * detInv
    elements[3] =
        (n23 * n32 * n41 - n22 * n33 * n41 - n23 * n31 * n42 + n21 * n33 * n42 + n22 * n31 * n43 - n21 * (n32 * n43)) * detInv

    elements[4] = t12 * detInv
    elements[5] =
        (n13 * n34 * n41 - n14 * n33 * n41 + n14 * n31 * n43 - n11 * n34 * n43 - n13 * n31 * n44 + n11 * (n33 * n44)) * detInv
    elements[6] =
        (n14 * n32 * n41 - n12 * n34 * n41 - n14 * n31 * n42 + n11 * n34 * n42 + n12 * n31 * n44 - n11 * (n32 * n44)) * detInv
    elements[7] =
        (n12 * n33 * n41 - n13 * n32 * n41 + n13 * n31 * n42 - n11 * n33 * n42 - n12 * n31 * n43 + n11 * (n32 * n43)) * detInv

    elements[8] = t13 * detInv
    elements[9] =
        (n14 * n23 * n41 - n13 * n24 * n41 - n14 * n21 * n43 + n11 * n24 * n43 + n13 * n21 * n44 - n11 * (n23 * n44)) * detInv
    elements[10] =
        (n12 * n24 * n41 - n14 * n22 * n41 + n14 * n21 * n42 - n11 * n24 * n42 - n12 * n21 * n44 + n11 * (n22 * n44)) * detInv
    elements[11] =
        (n13 * n22 * n41 - n12 * n23 * n41 - n13 * n21 * n42 + n11 * n23 * n42 + n12 * n21 * n43 - n11 * (n22 * n43)) * detInv

    elements[12] = t14 * detInv
    elements[13] =
        (n13 * n24 * n31 - n14 * n23 * n31 + n14 * n21 * n33 - n11 * n24 * n33 - n13 * n21 * n34 + n11 * (n23 * n34)) * detInv
    elements[14] =
        (n14 * n22 * n31 - n12 * n24 * n31 - n14 * n21 * n32 + n11 * n24 * n32 + n12 * n21 * n34 - n11 * (n22 * n34)) * detInv
    elements[15] =
        (n12 * n23 * n31 - n13 * n22 * n31 + n13 * n21 * n32 - n11 * n23 * n32 - n12 * n21 * n33 + n11 * (n22 * n33)) * detInv

    return this
}

/**
 * Transposes this matrix
 */
internal fun Matrix4.transposeMatrix(): Matrix4 {
    var tmp = elements[1]; elements[1] = elements[4]; elements[4] = tmp
    tmp = elements[2]; elements[2] = elements[8]; elements[8] = tmp
    tmp = elements[6]; elements[6] = elements[9]; elements[9] = tmp
    tmp = elements[3]; elements[3] = elements[12]; elements[12] = tmp
    tmp = elements[7]; elements[7] = elements[13]; elements[13] = tmp
    tmp = elements[11]; elements[11] = elements[14]; elements[14] = tmp
    return this
}
