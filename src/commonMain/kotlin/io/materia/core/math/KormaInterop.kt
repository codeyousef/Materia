/**
 * Korma Interoperability Layer
 * 
 * This file provides conversion utilities between Materia's math types and Korma types.
 * This enables zero-allocation interop when passing data to/from Korma-based libraries.
 * 
 * The Materia math types maintain their Three.js-style API for user compatibility,
 * while these extension functions allow seamless conversion to Korma when needed.
 */
package io.materia.core.math

import korlibs.math.geom.Matrix4 as KormaMatrix4
import korlibs.math.geom.Vector3F as KormaVector3F
import korlibs.math.geom.Vector4F as KormaVector4F
import korlibs.math.geom.Vector2F as KormaVector2F
import korlibs.math.geom.Quaternion as KormaQuaternion

// ============================================================================
// Vector2 <-> Korma Vector2F Interop
// ============================================================================

/**
 * Converts this Materia Vector2 to a Korma Vector2F.
 * Creates a new Korma vector instance.
 */
fun Vector2.toKorma(): KormaVector2F = KormaVector2F(x, y)

/**
 * Creates a Materia Vector2 from a Korma Vector2F.
 */
fun KormaVector2F.toMateria(): Vector2 = Vector2(x, y)

/**
 * Sets this Vector2 from a Korma Vector2F.
 * Reuses the existing Vector2 instance (zero-allocation).
 */
fun Vector2.setFromKorma(korma: KormaVector2F): Vector2 = set(korma.x, korma.y)

// ============================================================================
// Vector3 <-> Korma Vector3F Interop
// ============================================================================

/**
 * Converts this Materia Vector3 to a Korma Vector3F.
 * Creates a new Korma vector instance.
 */
fun Vector3.toKorma(): KormaVector3F = KormaVector3F(x, y, z)

/**
 * Creates a Materia Vector3 from a Korma Vector3F.
 */
fun KormaVector3F.toMateria(): Vector3 = Vector3(x, y, z)

/**
 * Sets this Vector3 from a Korma Vector3F.
 * Reuses the existing Vector3 instance (zero-allocation).
 */
fun Vector3.setFromKorma(korma: KormaVector3F): Vector3 = set(korma.x, korma.y, korma.z)

// ============================================================================
// Vector4 <-> Korma Vector4F Interop
// ============================================================================

/**
 * Converts this Materia Vector4 to a Korma Vector4F.
 * Creates a new Korma vector instance.
 */
fun Vector4.toKorma(): KormaVector4F = KormaVector4F(x, y, z, w)

/**
 * Creates a Materia Vector4 from a Korma Vector4F.
 */
fun KormaVector4F.toMateria(): Vector4 = Vector4(x, y, z, w)

/**
 * Sets this Vector4 from a Korma Vector4F.
 * Reuses the existing Vector4 instance (zero-allocation).
 */
fun Vector4.setFromKorma(korma: KormaVector4F): Vector4 = set(korma.x, korma.y, korma.z, korma.w)

// ============================================================================
// Matrix4 <-> Korma Matrix4 Interop
// ============================================================================

/**
 * Converts this Materia Matrix4 to a Korma Matrix4.
 * Creates a new Korma matrix instance.
 */
fun Matrix4.toKorma(): KormaMatrix4 = KormaMatrix4.fromColumns(
    elements[0], elements[1], elements[2], elements[3],
    elements[4], elements[5], elements[6], elements[7],
    elements[8], elements[9], elements[10], elements[11],
    elements[12], elements[13], elements[14], elements[15]
)

/**
 * Creates a Materia Matrix4 from a Korma Matrix4.
 */
fun KormaMatrix4.toMateria(): Matrix4 {
    val result = Matrix4()
    result.elements[0] = this.v00
    result.elements[1] = this.v10
    result.elements[2] = this.v20
    result.elements[3] = this.v30
    result.elements[4] = this.v01
    result.elements[5] = this.v11
    result.elements[6] = this.v21
    result.elements[7] = this.v31
    result.elements[8] = this.v02
    result.elements[9] = this.v12
    result.elements[10] = this.v22
    result.elements[11] = this.v32
    result.elements[12] = this.v03
    result.elements[13] = this.v13
    result.elements[14] = this.v23
    result.elements[15] = this.v33
    return result
}

/**
 * Sets this Matrix4 from a Korma Matrix4.
 * Reuses the existing Matrix4 instance (zero-allocation).
 */
fun Matrix4.setFromKorma(korma: KormaMatrix4): Matrix4 {
    elements[0] = korma.v00
    elements[1] = korma.v10
    elements[2] = korma.v20
    elements[3] = korma.v30
    elements[4] = korma.v01
    elements[5] = korma.v11
    elements[6] = korma.v21
    elements[7] = korma.v31
    elements[8] = korma.v02
    elements[9] = korma.v12
    elements[10] = korma.v22
    elements[11] = korma.v32
    elements[12] = korma.v03
    elements[13] = korma.v13
    elements[14] = korma.v23
    elements[15] = korma.v33
    return this
}

// ============================================================================
// Quaternion <-> Korma Quaternion Interop
// ============================================================================

/**
 * Converts this Materia Quaternion to a Korma Quaternion.
 * Creates a new Korma quaternion instance.
 */
fun Quaternion.toKorma(): KormaQuaternion = KormaQuaternion(x, y, z, w)

/**
 * Creates a Materia Quaternion from a Korma Quaternion.
 */
fun KormaQuaternion.toMateria(): Quaternion = Quaternion(x, y, z, w)

/**
 * Sets this Quaternion from a Korma Quaternion.
 * Reuses the existing Quaternion instance (zero-allocation).
 */
fun Quaternion.setFromKorma(korma: KormaQuaternion): Quaternion = set(korma.x, korma.y, korma.z, korma.w)

// ============================================================================
// Korma Vector3F extension functions for Three.js-style API compatibility
// ============================================================================

/**
 * Applies a Matrix4 transformation to a Korma Vector3F, returning a new Vector3F.
 * This provides Three.js-style applyMatrix4 functionality for Korma vectors.
 */
fun KormaVector3F.applyMatrix4(matrix: Matrix4): KormaVector3F {
    val e = matrix.elements
    val w = 1f / (e[3] * x + e[7] * y + e[11] * z + e[15])
    return KormaVector3F(
        (e[0] * x + e[4] * y + e[8] * z + e[12]) * w,
        (e[1] * x + e[5] * y + e[9] * z + e[13]) * w,
        (e[2] * x + e[6] * y + e[10] * z + e[14]) * w
    )
}

/**
 * Applies a Korma Matrix4 transformation to a Korma Vector3F, returning a new Vector3F.
 */
fun KormaVector3F.applyMatrix4(matrix: KormaMatrix4): KormaVector3F {
    val w = 1f / (matrix.v30 * x + matrix.v31 * y + matrix.v32 * z + matrix.v33)
    return KormaVector3F(
        (matrix.v00 * x + matrix.v01 * y + matrix.v02 * z + matrix.v03) * w,
        (matrix.v10 * x + matrix.v11 * y + matrix.v12 * z + matrix.v13) * w,
        (matrix.v20 * x + matrix.v21 * y + matrix.v22 * z + matrix.v23) * w
    )
}

// ============================================================================
// Factory functions for creating Korma vectors with Three.js-style signatures
// ============================================================================

/**
 * Creates a Korma Vector3F with the familiar Vector3(x, y, z) pattern.
 */
@Suppress("FunctionName")
fun KormaVector3(x: Float = 0f, y: Float = 0f, z: Float = 0f): KormaVector3F = KormaVector3F(x, y, z)

/**
 * Creates a Korma Vector3F with a single scalar value for all components.
 */
@Suppress("FunctionName")
fun KormaVector3(scalar: Float): KormaVector3F = KormaVector3F(scalar, scalar, scalar)

/**
 * Creates a Korma Vector4F with the familiar Vector4(x, y, z, w) pattern.
 */
@Suppress("FunctionName")
fun KormaVector4(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 1f): KormaVector4F = KormaVector4F(x, y, z, w)

/**
 * Creates a Korma Quaternion with the familiar Quaternion(x, y, z, w) pattern.
 */
@Suppress("FunctionName")
fun KormaQuaternion(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 1f): KormaQuaternion = KormaQuaternion(x, y, z, w)
