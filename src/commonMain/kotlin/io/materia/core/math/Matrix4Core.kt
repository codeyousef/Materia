package io.materia.core.math

/**
 * Core Matrix4 operations including identity, copy, clone, and element access
 */

/**
 * Sets this matrix to identity
 */
internal fun Matrix4.setIdentity(): Matrix4 {
    elements[0] = 1f; elements[4] = 0f; elements[8] = 0f; elements[12] = 0f
    elements[1] = 0f; elements[5] = 1f; elements[9] = 0f; elements[13] = 0f
    elements[2] = 0f; elements[6] = 0f; elements[10] = 1f; elements[14] = 0f
    elements[3] = 0f; elements[7] = 0f; elements[11] = 0f; elements[15] = 1f
    return this
}

/**
 * Checks if this matrix is the identity matrix
 */
internal fun Matrix4.checkIsIdentity(): Boolean {
    return elements[0] == 1f && elements[4] == 0f && elements[8] == 0f && elements[12] == 0f &&
            elements[1] == 0f && elements[5] == 1f && elements[9] == 0f && elements[13] == 0f &&
            elements[2] == 0f && elements[6] == 0f && elements[10] == 1f && elements[14] == 0f &&
            elements[3] == 0f && elements[7] == 0f && elements[11] == 0f && elements[15] == 1f
}

/**
 * Creates a copy of this matrix
 */
internal fun Matrix4.createClone(): Matrix4 {
    return Matrix4(elements.copyOf())
}

/**
 * Copies values from another matrix
 */
internal fun Matrix4.copyFrom(matrix: Matrix4): Matrix4 {
    matrix.elements.copyInto(elements)
    return this
}

/**
 * Sets matrix elements from individual values
 */
internal fun Matrix4.setElements(
    m11: Float, m12: Float, m13: Float, m14: Float,
    m21: Float, m22: Float, m23: Float, m24: Float,
    m31: Float, m32: Float, m33: Float, m34: Float,
    m41: Float, m42: Float, m43: Float, m44: Float
): Matrix4 {
    elements[0] = m11; elements[4] = m12; elements[8] = m13; elements[12] = m14
    elements[1] = m21; elements[5] = m22; elements[9] = m23; elements[13] = m24
    elements[2] = m31; elements[6] = m32; elements[10] = m33; elements[14] = m34
    elements[3] = m41; elements[7] = m42; elements[11] = m43; elements[15] = m44
    return this
}

/**
 * Returns the matrix elements as an array
 */
internal fun Matrix4.asArray(): FloatArray = elements.copyOf()

/**
 * Copy matrix elements to an array at the specified offset
 */
internal fun Matrix4.copyToArray(array: FloatArray, offset: Int = 0) {
    elements.copyInto(array, offset, 0, 16)
}

/**
 * Set matrix elements from an array at the specified offset
 */
internal fun Matrix4.setFromArray(array: FloatArray, offset: Int = 0): Matrix4 {
    array.copyInto(elements, 0, offset, offset + 16)
    return this
}

/**
 * Extracts position from transformation matrix
 */
internal fun Matrix4.extractPosition(): Vector3 {
    return Vector3(elements[12], elements[13], elements[14])
}

/**
 * Sets position in transformation matrix
 */
internal fun Matrix4.updatePosition(x: Float, y: Float, z: Float): Matrix4 {
    elements[12] = x
    elements[13] = y
    elements[14] = z
    return this
}

/**
 * Sets position in transformation matrix from vector
 */
internal fun Matrix4.updatePositionFromVector(v: Vector3): Matrix4 {
    return updatePosition(v.x, v.y, v.z)
}

/**
 * Extracts scale from transformation matrix
 */
internal fun Matrix4.extractScale(): Vector3 {
    val sx = Vector3(elements[0], elements[1], elements[2]).length()
    val sy = Vector3(elements[4], elements[5], elements[6]).length()
    val sz = Vector3(elements[8], elements[9], elements[10]).length()

    // Check for negative determinant (reflection)
    if (this.determinant() < 0) {
        return Vector3(-sx, sy, sz)
    }

    return Vector3(sx, sy, sz)
}

/**
 * Transforms a 3D point by this matrix
 */
internal fun Matrix4.transformPoint3(point: Vector3): Vector3 {
    val x = point.x
    val y = point.y
    val z = point.z
    val e = elements

    val w = 1f / (e[3] * x + e[7] * y + e[11] * z + e[15])

    return Vector3(
        (e[0] * x + e[4] * y + e[8] * z + e[12]) * w,
        (e[1] * x + e[5] * y + e[9] * z + e[13]) * w,
        (e[2] * x + e[6] * y + e[10] * z + e[14]) * w
    )
}

/**
 * Extracts the translation component from this matrix
 */
internal fun Matrix4.extractTranslationTo(target: Vector3 = Vector3()): Vector3 {
    target.x = elements[12]
    target.y = elements[13]
    target.z = elements[14]
    return target
}
