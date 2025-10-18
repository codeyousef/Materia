package io.kreekt.renderer.geometry

import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.material.MaterialDescriptor

/**
 * Shared helpers for deriving geometry build options from material descriptors.
 *
 * These helpers are used by both Vulkan and WebGPU renderers to ensure consistent
 * attribute inclusion across targets without relying on platform-specific maps.
 */
fun MaterialDescriptor.buildGeometryOptions(geometry: BufferGeometry): GeometryBuildOptions {
    fun requires(attribute: GeometryAttribute): Boolean = requiredAttributes.contains(attribute)
    fun optional(attribute: GeometryAttribute): Boolean = optionalAttributes.contains(attribute)

    fun hasAttribute(attribute: GeometryAttribute): Boolean = when (attribute) {
        GeometryAttribute.POSITION -> geometry.hasAttribute(POSITION_ATTR)
        GeometryAttribute.NORMAL -> geometry.hasAttribute(NORMAL_ATTR)
        GeometryAttribute.COLOR -> geometry.hasAttribute(COLOR_ATTR)
        GeometryAttribute.UV0 -> geometry.hasAttribute(UV_ATTR)
        GeometryAttribute.UV1 -> geometry.hasAttribute(UV2_ATTR)
        GeometryAttribute.TANGENT -> geometry.hasAttribute(TANGENT_ATTR)
        GeometryAttribute.MORPH_POSITION,
        GeometryAttribute.MORPH_NORMAL -> geometry.morphAttributes.isNotEmpty()
        GeometryAttribute.INSTANCE_MATRIX -> geometry.isInstanced
    }

    return GeometryBuildOptions(
        includeNormals = requires(GeometryAttribute.NORMAL) ||
            (optional(GeometryAttribute.NORMAL) && hasAttribute(GeometryAttribute.NORMAL)),
        includeColors = requires(GeometryAttribute.COLOR) ||
            (optional(GeometryAttribute.COLOR) && hasAttribute(GeometryAttribute.COLOR)),
        includeUVs = requires(GeometryAttribute.UV0) ||
            (optional(GeometryAttribute.UV0) && hasAttribute(GeometryAttribute.UV0)),
        includeSecondaryUVs = requires(GeometryAttribute.UV1) ||
            (optional(GeometryAttribute.UV1) && hasAttribute(GeometryAttribute.UV1)),
        includeTangents = requires(GeometryAttribute.TANGENT) ||
            (optional(GeometryAttribute.TANGENT) && hasAttribute(GeometryAttribute.TANGENT)),
        includeMorphTargets = geometry.morphAttributes.isNotEmpty(),
        includeInstancing = geometry.isInstanced || requires(GeometryAttribute.INSTANCE_MATRIX)
    )
}

private const val POSITION_ATTR = "position"
private const val NORMAL_ATTR = "normal"
private const val COLOR_ATTR = "color"
private const val UV_ATTR = "uv"
private const val UV2_ATTR = "uv2"
private const val TANGENT_ATTR = "tangent"
