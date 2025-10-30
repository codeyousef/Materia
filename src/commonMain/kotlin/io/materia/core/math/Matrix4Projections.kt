package io.materia.core.math

/**
 * Matrix4 projection operations including perspective and orthographic projections
 */

/**
 * Creates a perspective projection matrix (OpenGL convention: Z in [-1, 1])
 */
internal fun Matrix4.makePerspectiveProjection(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    near: Float,
    far: Float
): Matrix4 {
    // Validate parameters to prevent degenerate frustums
    require(right != left) { "Degenerate frustum: right must not equal left" }
    require(top != bottom) { "Degenerate frustum: top must not equal bottom" }
    require(far != near) { "Degenerate frustum: far must not equal near" }
    require(near > 0f) { "Invalid perspective: near must be positive" }
    require(far > near) { "Invalid perspective: far must be greater than near" }

    val x = 2f * near / (right - left)
    val y = 2f * near / (top - bottom)
    val a = (right + left) / (right - left)
    val b = (top + bottom) / (top - bottom)
    val c = -(far + near) / (far - near)
    val d = -2f * far * near / (far - near)

    elements[0] = x; elements[4] = 0f; elements[8] = a; elements[12] = 0f
    elements[1] = 0f; elements[5] = y; elements[9] = b; elements[13] = 0f
    elements[2] = 0f; elements[6] = 0f; elements[10] = c; elements[14] = d
    elements[3] = 0f; elements[7] = 0f; elements[11] = -1f; elements[15] = 0f

    return this
}

/**
 * Creates a perspective projection matrix for WebGPU (Z in [0, 1], left-handed)
 *
 * WebGPU uses different conventions than OpenGL:
 * - Depth range: Z ∈ [0, 1] (not [-1, 1])
 * - Coordinate system: Left-handed (Z forward into screen, not toward viewer)
 *
 * This requires different projection matrix coefficients for elements [10] and [14].
 * Based on DirectX/Vulkan left-handed perspective projection.
 */
internal fun Matrix4.makePerspectiveProjectionWebGPU(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    near: Float,
    far: Float
): Matrix4 {
    // Validate parameters to prevent degenerate frustums
    require(right != left) { "Degenerate frustum: right must not equal left" }
    require(top != bottom) { "Degenerate frustum: top must not equal bottom" }
    require(far != near) { "Degenerate frustum: far must not equal near" }
    require(near > 0f) { "Invalid perspective: near must be positive" }
    require(far > near) { "Invalid perspective: far must be greater than near" }

    val x = 2f * near / (right - left)
    val y = 2f * near / (top - bottom)
    val a = (right + left) / (right - left)
    val b = (top + bottom) / (top - bottom)

    // WebGPU depth mapping: Z ∈ [0, 1] instead of [-1, 1]
    // Note: c is negative because view space has negative Z values
    val c = -far / (far - near)
    val d = -(near * far) / (far - near)

    elements[0] = x; elements[4] = 0f; elements[8] = a; elements[12] = 0f
    elements[1] = 0f; elements[5] = y; elements[9] = b; elements[13] = 0f
    elements[2] = 0f; elements[6] = 0f; elements[10] = c; elements[14] = d
    elements[3] = 0f; elements[7] = 0f; elements[11] = -1f; elements[15] = 0f

    return this
}

/**
 * Creates an orthographic projection matrix
 */
internal fun Matrix4.makeOrthographicProjection(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    near: Float,
    far: Float
): Matrix4 {
    // Validate parameters to prevent degenerate frustums
    require(right != left) { "Degenerate frustum: right must not equal left" }
    require(top != bottom) { "Degenerate frustum: top must not equal bottom" }
    require(far != near) { "Degenerate frustum: far must not equal near" }

    val w = 1f / (right - left)
    val h = 1f / (top - bottom)
    val p = 1f / (far - near)

    val x = (right + left) * w
    val y = (top + bottom) * h
    val z = (far + near) * p

    elements[0] = (2f * w); elements[4] = 0f; elements[8] = 0f; elements[12] = -x
    elements[1] = 0f; elements[5] = (2f * h); elements[9] = 0f; elements[13] = -y
    elements[2] = 0f; elements[6] = 0f; elements[10] = -(2f * p); elements[14] = -z
    elements[3] = 0f; elements[7] = 0f; elements[11] = 0f; elements[15] = 1f

    return this
}
