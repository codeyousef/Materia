package io.materia.core.scene

import io.materia.core.math.*

/**
 * Extension functions for Object3D supporting classes (Vector3, Euler, Quaternion, Matrix4, Color)
 */

/**
 * Sets position from Vector3
 */
fun Vector3.setFromMatrixPosition(matrix: Matrix4): Vector3 {
    val e = matrix.elements
    return set(e[12], e[13], e[14])
}

/**
 * Adds onChange callback to Euler (uses internal _onChangeCallback)
 */
var Euler.onChange: (() -> Unit)?
    get() = this._onChangeCallback
    set(value) { 
        this._onChangeCallback = value
    }

/**
 * Adds onChange callback to Quaternion (uses internal _onChangeCallback)
 */
var Quaternion.onChange: (() -> Unit)?
    get() = this._onChangeCallback
    set(value) {
        this._onChangeCallback = value
    }

// Note: Matrix4.compose() member function exists - this extension is shadowed and removed

/**
 * Extracts rotation from another matrix (removes scale)
 * This extension function does NOT exist as a member function in Matrix4
 */
fun Matrix4.extractRotation(matrix: Matrix4): Matrix4 {
    val te = elements
    val me = matrix.elements

    val EPSILON = 0.000001f

    val lengthX = Vector3(me[0], me[1], me[2]).length()
    val lengthY = Vector3(me[4], me[5], me[6]).length()
    val lengthZ = Vector3(me[8], me[9], me[10]).length()

    if (lengthX < EPSILON || lengthY < EPSILON || lengthZ < EPSILON) {
        // Degenerate matrix - return identity rotation
        te[0] = 1f; te[1] = 0f; te[2] = 0f; te[3] = 0f
        te[4] = 0f; te[5] = 1f; te[6] = 0f; te[7] = 0f
        te[8] = 0f; te[9] = 0f; te[10] = 1f; te[11] = 0f
        te[12] = 0f; te[13] = 0f; te[14] = 0f; te[15] = 1f
        return this
    }

    val scaleX = 1f / lengthX
    val scaleY = 1f / lengthY
    val scaleZ = 1f / lengthZ

    te[0] = me[0] * scaleX
    te[1] = me[1] * scaleX
    te[2] = me[2] * scaleX
    te[3] = 0f

    te[4] = me[4] * scaleY
    te[5] = me[5] * scaleY
    te[6] = me[6] * scaleY
    te[7] = 0f

    te[8] = me[8] * scaleZ
    te[9] = me[9] * scaleZ
    te[10] = me[10] * scaleZ
    te[11] = 0f

    te[12] = 0f
    te[13] = 0f
    te[14] = 0f
    te[15] = 1f

    return this
}

/**
 * Converts Color to array with offset
 * Extension function for Color.toArray(array, offset) - the no-param version exists, but not this one
 */
fun Color.toArray(array: FloatArray, offset: Int = 0): FloatArray {
    array[offset] = r
    array[offset + 1] = g
    array[offset + 2] = b
    return array
}

// Note: Matrix4.toArray(array, offset) member function exists - this extension is shadowed and removed
