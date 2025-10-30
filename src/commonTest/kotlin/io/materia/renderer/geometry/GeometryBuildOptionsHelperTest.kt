package io.materia.renderer.geometry

import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.renderer.material.MaterialDescriptor
import io.materia.renderer.material.MaterialUniformBlock
import io.materia.renderer.material.MaterialUniformField
import io.materia.renderer.material.MaterialUniformType
import io.materia.renderer.shader.MaterialShaderDescriptor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryBuildOptionsHelperTest {

    @Test
    fun includesOptionalAttributesOnlyWhenPresent() {
        val descriptor = descriptor(
            required = setOf(GeometryAttribute.POSITION),
            optional = setOf(GeometryAttribute.TANGENT, GeometryAttribute.UV1)
        )

        val baseGeometry = geometry()
        val optionsWithout = descriptor.buildGeometryOptions(baseGeometry)
        assertFalse(
            optionsWithout.includeTangents,
            "Tangents should be excluded when geometry lacks them"
        )
        assertFalse(
            optionsWithout.includeSecondaryUVs,
            "Secondary UVs should be excluded when geometry lacks them"
        )

        val enrichedGeometry = geometry(includeTangents = true, includeSecondaryUv = true)
        val optionsWith = descriptor.buildGeometryOptions(enrichedGeometry)
        assertTrue(
            optionsWith.includeTangents,
            "Tangents should be included when geometry provides them"
        )
        assertTrue(
            optionsWith.includeSecondaryUVs,
            "Secondary UVs should be included when geometry provides them"
        )
    }

    @Test
    fun honoursInstanceRequirements() {
        val requiredDescriptor = descriptor(
            required = setOf(GeometryAttribute.POSITION, GeometryAttribute.INSTANCE_MATRIX)
        )
        val optionalDescriptor = descriptor(
            required = setOf(GeometryAttribute.POSITION),
            optional = setOf(GeometryAttribute.INSTANCE_MATRIX)
        )

        val nonInstanced = geometry()
        val requiredOptions = requiredDescriptor.buildGeometryOptions(nonInstanced)
        assertTrue(
            requiredOptions.includeInstancing,
            "Instancing should be included when descriptor requires it"
        )

        val instanced = geometry(instanced = true)
        val optionalOptions = optionalDescriptor.buildGeometryOptions(instanced)
        assertTrue(
            optionalOptions.includeInstancing,
            "Instancing should be included when geometry provides instanced attributes"
        )

        val optionalNonInstanced = optionalDescriptor.buildGeometryOptions(nonInstanced)
        assertFalse(
            optionalNonInstanced.includeInstancing,
            "Instancing should be skipped when optional and geometry is not instanced"
        )
    }

    @Test
    fun detectsMorphTargets() {
        val descriptor = descriptor(required = setOf(GeometryAttribute.POSITION))
        val geometry = geometry()
        val plainOptions = descriptor.buildGeometryOptions(geometry)
        assertFalse(
            plainOptions.includeMorphTargets,
            "Geometry without morph targets should not include them"
        )

        val morphGeometry = geometry()
        val morphAttribute = BufferAttribute(
            array = floatArrayOf(
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f
            ),
            itemSize = 3
        )
        morphGeometry.setMorphAttribute("position", arrayOf(morphAttribute))
        val morphOptions = descriptor.buildGeometryOptions(morphGeometry)
        assertTrue(
            morphOptions.includeMorphTargets,
            "Geometry with morph targets should enable them"
        )
    }

    private fun descriptor(
        required: Set<GeometryAttribute>,
        optional: Set<GeometryAttribute> = emptySet()
    ): MaterialDescriptor {
        return MaterialDescriptor(
            key = "test",
            shader = MaterialShaderDescriptor(
                key = "test",
                vertexChunks = emptyList(),
                fragmentChunks = emptyList()
            ),
            uniformBlock = TEST_UNIFORM_BLOCK,
            requiredAttributes = required,
            optionalAttributes = optional
        )
    }

    private fun geometry(
        includeNormals: Boolean = true,
        includeColors: Boolean = false,
        includeUv: Boolean = true,
        includeSecondaryUv: Boolean = false,
        includeTangents: Boolean = false,
        instanced: Boolean = false
    ): BufferGeometry {
        val geometry = BufferGeometry()
        geometry.setAttribute(
            "position",
            BufferAttribute(
                array = floatArrayOf(
                    0f, 0f, 0f,
                    1f, 0f, 0f,
                    0f, 1f, 0f
                ),
                itemSize = 3
            )
        )
        if (includeNormals) {
            geometry.setAttribute(
                "normal",
                BufferAttribute(
                    array = floatArrayOf(
                        0f, 0f, 1f,
                        0f, 0f, 1f,
                        0f, 0f, 1f
                    ),
                    itemSize = 3
                )
            )
        }
        if (includeColors) {
            geometry.setAttribute(
                "color",
                BufferAttribute(
                    array = floatArrayOf(
                        1f, 0f, 0f,
                        0f, 1f, 0f,
                        0f, 0f, 1f
                    ),
                    itemSize = 3
                )
            )
        }
        if (includeUv) {
            geometry.setAttribute(
                "uv",
                BufferAttribute(
                    array = floatArrayOf(
                        0f, 0f,
                        1f, 0f,
                        0f, 1f
                    ),
                    itemSize = 2
                )
            )
        }
        if (includeSecondaryUv) {
            geometry.setAttribute(
                "uv2",
                BufferAttribute(
                    array = floatArrayOf(
                        0f, 0f,
                        1f, 0f,
                        0f, 1f
                    ),
                    itemSize = 2
                )
            )
        }
        if (includeTangents) {
            geometry.setAttribute(
                "tangent",
                BufferAttribute(
                    array = floatArrayOf(
                        1f, 0f, 0f, 1f,
                        1f, 0f, 0f, 1f,
                        1f, 0f, 0f, 1f
                    ),
                    itemSize = 4
                )
            )
        }
        if (instanced) {
            geometry.instanceCount = 2
            geometry.setInstancedAttribute(
                "instanceMatrix",
                BufferAttribute(
                    array = FloatArray(32) { if (it % 5 == 0) 1f else 0f },
                    itemSize = 16
                )
            )
        }
        return geometry
    }

    companion object {
        private val TEST_UNIFORM_BLOCK = MaterialUniformBlock(
            name = "TestUniforms",
            group = 0,
            binding = 0,
            sizeBytes = 64,
            fields = listOf(
                MaterialUniformField("modelMatrix", MaterialUniformType.MAT4, offset = 0)
            )
        )
    }
}
