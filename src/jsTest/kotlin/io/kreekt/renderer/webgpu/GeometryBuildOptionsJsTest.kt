package io.kreekt.renderer.webgpu

import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.geometry.buildGeometryOptions
import io.kreekt.renderer.material.MaterialDescriptor
import io.kreekt.renderer.material.MaterialDescriptorRegistry
import io.kreekt.renderer.shader.MaterialShaderDescriptor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryBuildOptionsJsTest {

    @AfterTest
    fun resetRegistry() {
        MaterialDescriptorRegistry.resetForTests()
    }

    @Test
    fun optionalAttributesOnlyIncludedWhenPresent() {
        val descriptor = descriptor(
            required = setOf(GeometryAttribute.POSITION),
            optional = setOf(GeometryAttribute.TANGENT, GeometryAttribute.UV1)
        )

        val geometry = BufferGeometry().apply {
            setAttribute("position", positionAttribute())
        }

        val optionsWithout = descriptor.buildGeometryOptions(geometry)
        assertFalse(optionsWithout.includeTangents, "Tangents should be excluded when geometry lacks them")
        assertFalse(optionsWithout.includeSecondaryUVs, "Secondary UVs should be excluded when geometry lacks them")

        geometry.setAttribute("tangent", tangentAttribute())
        geometry.setAttribute("uv2", uvAttribute())

        val optionsWith = descriptor.buildGeometryOptions(geometry)
        assertTrue(optionsWith.includeTangents, "Tangents should be included when geometry provides them")
        assertTrue(optionsWith.includeSecondaryUVs, "Secondary UVs should be included when geometry provides them")
    }

    @Test
    fun instancingDerivesFromGeometry() {
        val descriptor = descriptor(required = setOf(GeometryAttribute.POSITION))
        val geometry = BufferGeometry().apply {
            setAttribute("position", positionAttribute())
            instanceCount = 2
            setInstancedAttribute(
                "instanceMatrix",
                BufferAttribute(
                    FloatArray(32) { index -> if (index % 5 == 0) 1f else 0f },
                    itemSize = 16
                )
            )
        }

        val options = descriptor.buildGeometryOptions(geometry)
        assertTrue(options.includeInstancing, "Instancing should be enabled when geometry is instanced")
    }

    @Test
    fun morphTargetsEnableMorphOption() {
        val descriptor = descriptor(required = setOf(GeometryAttribute.POSITION))
        val geometry = BufferGeometry().apply {
            setAttribute("position", positionAttribute())
            setMorphAttribute(
                "position",
                arrayOf(
                    BufferAttribute(
                        floatArrayOf(
                            0.1f, 0f, 0f,
                            0f, 0.1f, 0f,
                            0f, 0f, 0.1f
                        ),
                        itemSize = 3
                    )
                )
            )
        }

        val options = descriptor.buildGeometryOptions(geometry)
        assertTrue(options.includeMorphTargets, "Morph targets should be enabled when geometry defines them")
    }

    private fun descriptor(
        required: Set<GeometryAttribute>,
        optional: Set<GeometryAttribute> = emptySet()
    ): MaterialDescriptor {
        return MaterialDescriptor(
            key = "js.test",
            shader = MaterialShaderDescriptor(
                key = "js.test",
                vertexChunks = emptyList(),
                fragmentChunks = emptyList()
            ),
            uniformBlock = MaterialDescriptorRegistry.sharedUniformBlock,
            requiredAttributes = required,
            optionalAttributes = optional
        )
    }

    private fun positionAttribute(): BufferAttribute = BufferAttribute(
        floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        ),
        itemSize = 3
    )

    private fun tangentAttribute(): BufferAttribute = BufferAttribute(
        floatArrayOf(
            1f, 0f, 0f, 1f,
            1f, 0f, 0f, 1f,
            1f, 0f, 0f, 1f
        ),
        itemSize = 4
    )

    private fun uvAttribute(): BufferAttribute = BufferAttribute(
        floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f
        ),
        itemSize = 2
    )
}
