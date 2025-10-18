package io.kreekt.renderer.material

import io.kreekt.core.scene.Material
import io.kreekt.material.Blending
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.material.MeshStandardMaterial
import io.kreekt.material.BlendMode as StandardBlendMode
import io.kreekt.material.MaterialSide as StandardMaterialSide
import io.kreekt.material.Side
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.shader.MaterialShaderDescriptor
import io.kreekt.renderer.shader.MaterialShaderLibrary
import io.kreekt.renderer.webgpu.BlendComponent
import io.kreekt.renderer.webgpu.BlendFactor
import io.kreekt.renderer.webgpu.BlendOperation
import io.kreekt.renderer.webgpu.BlendState
import io.kreekt.renderer.webgpu.ColorTargetDescriptor
import io.kreekt.renderer.webgpu.ColorWriteMask
import io.kreekt.renderer.webgpu.CompareFunction
import io.kreekt.renderer.webgpu.CullMode
import io.kreekt.renderer.webgpu.FrontFace
import io.kreekt.renderer.webgpu.PrimitiveTopology
import io.kreekt.renderer.webgpu.TextureFormat
import kotlin.reflect.KClass
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized as atomicSynchronized

data class ResolvedMaterialDescriptor(
    val descriptor: MaterialDescriptor,
    val renderState: MaterialRenderState,
    val shaderOverrides: Map<String, String> = emptyMap()
)

/**
 * Describes a field within a material uniform block.
 */
data class MaterialUniformField(
    val name: String,
    val type: MaterialUniformType,
    val offset: Int
)

enum class MaterialUniformType {
    MAT4,
    VEC4
}

/**
 * Describes the layout of a uniform buffer used by a material.
 */
data class MaterialUniformBlock(
    val name: String,
    val group: Int,
    val binding: Int,
    val sizeBytes: Int,
    val fields: List<MaterialUniformField>
)

/**
 * Represents a non-uniform binding required by a material (texture, sampler, etc.).
 */
data class MaterialBinding(
    val name: String,
    val type: MaterialBindingType,
    val group: Int,
    val binding: Int,
    val source: MaterialBindingSource,
    val required: Boolean = true
)

enum class MaterialBindingType {
    TEXTURE_2D,
    TEXTURE_CUBE,
    SAMPLER
}

enum class MaterialBindingSource {
    ENVIRONMENT_PREFILTER,
    ENVIRONMENT_BRDF,
    ALBEDO_MAP,
    NORMAL_MAP,
    ROUGHNESS_MAP,
    METALNESS_MAP
}

/**
 * Describes fixed-function pipeline state for a material.
 */
data class MaterialRenderState(
    val topology: PrimitiveTopology = PrimitiveTopology.TRIANGLE_LIST,
    val cullMode: CullMode = CullMode.BACK,
    val frontFace: FrontFace = FrontFace.CCW,
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
    val depthCompare: CompareFunction = CompareFunction.LESS,
    val depthFormat: TextureFormat = TextureFormat.DEPTH24_PLUS,
    val colorTarget: ColorTargetDescriptor = ColorTargetDescriptor()
)

/**
 * Fully describes how a material should be rendered within the pipeline.
 */
data class MaterialDescriptor(
    val key: String,
    val shader: MaterialShaderDescriptor,
    val uniformBlock: MaterialUniformBlock,
    val bindings: List<MaterialBinding> = emptyList(),
    val renderState: MaterialRenderState = MaterialRenderState(),
    val defines: Map<String, String> = emptyMap(),
    val requiredAttributes: Set<GeometryAttribute> = emptySet(),
    val optionalAttributes: Set<GeometryAttribute> = emptySet()
)

object MaterialDescriptorRegistry {
    private val lock = SynchronizedObject()
    private val defaultsRegistered = atomic(false)
    private val descriptorsByKey = mutableMapOf<String, MaterialDescriptor>()
    private val descriptorsByMaterial = mutableMapOf<KClass<out Material>, MaterialDescriptor>()

    private const val MATERIAL_TEXTURE_GROUP = 1
    private const val ENVIRONMENT_GROUP = 2

    private val BASIC_REQUIRED_ATTRIBUTES = setOf(
        GeometryAttribute.POSITION,
        GeometryAttribute.NORMAL
    )
    private val BASIC_OPTIONAL_ATTRIBUTES = setOf(
        GeometryAttribute.COLOR,
        GeometryAttribute.UV0
    )
    private val STANDARD_REQUIRED_ATTRIBUTES = setOf(
        GeometryAttribute.POSITION,
        GeometryAttribute.NORMAL
    )
    private val STANDARD_OPTIONAL_ATTRIBUTES = setOf(
        GeometryAttribute.COLOR,
        GeometryAttribute.UV0,
        GeometryAttribute.UV1,
        GeometryAttribute.TANGENT
    )

    private val defaultUniformBlock = MaterialUniformBlock(
        name = "Uniforms",
        group = 0,
        binding = 0,
        sizeBytes = 240,
        fields = listOf(
            MaterialUniformField("projectionMatrix", MaterialUniformType.MAT4, offset = 0),
            MaterialUniformField("viewMatrix", MaterialUniformType.MAT4, offset = 64),
            MaterialUniformField("modelMatrix", MaterialUniformType.MAT4, offset = 128),
            MaterialUniformField("baseColor", MaterialUniformType.VEC4, offset = 192),
            MaterialUniformField("pbrParams", MaterialUniformType.VEC4, offset = 208),
            MaterialUniformField("cameraPosition", MaterialUniformType.VEC4, offset = 224)
        )
    )

    /**
     * Registers a descriptor for the provided [materials]. Optionally replaces existing registrations.
     */
    fun register(
        descriptor: MaterialDescriptor,
        materials: List<KClass<out Material>>,
        replaceExisting: Boolean = false
    ) {
        withLock {
            registerUnsafe(descriptor, materials, replaceExisting)
        }
    }

    /**
     * Retrieves a descriptor by material instance.
     */
    fun descriptorFor(material: Material): MaterialDescriptor? {
        ensureDefaultsRegistered()
        return withLock {
            descriptorsByMaterial[material::class]
        }
    }

    /**
     * Retrieves a descriptor by key.
     */
    fun descriptorForKey(key: String): MaterialDescriptor? {
        ensureDefaultsRegistered()
        return withLock {
            descriptorsByKey[key]
        }
    }

    /**
     * Returns the default uniform block used by built-in materials.
     */
    val sharedUniformBlock: MaterialUniformBlock
        get() {
            ensureDefaultsRegistered()
            return defaultUniformBlock
        }

    /**
     * Returns size of the default uniform block (in bytes).
     */
    fun uniformBlockSizeBytes(): Int {
        ensureDefaultsRegistered()
        return defaultUniformBlock.sizeBytes
    }

    fun resolve(material: Material): ResolvedMaterialDescriptor? {
        val descriptor = descriptorFor(material) ?: return null
        return when (material) {
            is MeshBasicMaterial -> resolveBasic(descriptor, material)
            is MeshStandardMaterial -> resolveStandard(descriptor, material)
            else -> null
        }
    }

    private fun registerDescriptor(descriptor: MaterialDescriptor, replaceExisting: Boolean) {
        val existing = descriptorsByKey[descriptor.key]
        if (existing != null && !replaceExisting) {
            error("Material descriptor with key '${descriptor.key}' already registered")
        }
        descriptorsByKey[descriptor.key] = descriptor
    }

    private fun ensureDefaultsRegistered() {
        if (defaultsRegistered.value) return
        MaterialShaderLibrary.ensureBuiltInsRegistered()
        withLock {
            if (defaultsRegistered.value) return
            registerDefaultsLocked()
            defaultsRegistered.value = true
        }
    }

    private fun registerDefaultsLocked() {
        val basicDescriptor = MaterialDescriptor(
            key = "material.basic",
            shader = MaterialShaderLibrary.basic(),
            uniformBlock = defaultUniformBlock,
            bindings = albedoBindings(),
            renderState = MaterialRenderState(),
            requiredAttributes = BASIC_REQUIRED_ATTRIBUTES,
            optionalAttributes = BASIC_OPTIONAL_ATTRIBUTES
        )
        registerUnsafe(
            descriptor = basicDescriptor,
            materials = listOf(MeshBasicMaterial::class),
            replaceExisting = true
        )

        val standardDescriptor = MaterialDescriptor(
            key = "material.meshStandard",
            shader = MaterialShaderLibrary.meshStandard(),
            uniformBlock = defaultUniformBlock,
            bindings = albedoBindings() + normalBindings() + environmentBindings(),
            renderState = MaterialRenderState(),
            requiredAttributes = STANDARD_REQUIRED_ATTRIBUTES,
            optionalAttributes = STANDARD_OPTIONAL_ATTRIBUTES
        )
        registerUnsafe(
            descriptor = standardDescriptor,
            materials = listOf(MeshStandardMaterial::class),
            replaceExisting = true
        )
    }

    internal fun resetForTests() {
        withLock {
            descriptorsByKey.clear()
            descriptorsByMaterial.clear()
            defaultsRegistered.value = false
        }
    }

    private fun registerUnsafe(
        descriptor: MaterialDescriptor,
        materials: List<KClass<out Material>>,
        replaceExisting: Boolean
    ) {
        if (!replaceExisting) {
            val conflictingMaterial = materials.firstOrNull { descriptorsByMaterial.containsKey(it) }
            if (conflictingMaterial != null) {
                error("Descriptor already registered for ${conflictingMaterial.simpleName}")
            }
        }
        registerDescriptor(descriptor, replaceExisting)
        materials.forEach { materialClass ->
            descriptorsByMaterial[materialClass] = descriptor
        }
    }

    private inline fun <T> withLock(block: () -> T): T = atomicSynchronized(lock, block)

    private fun albedoBindings(): List<MaterialBinding> = listOf(
        MaterialBinding(
            name = "albedoTexture",
            type = MaterialBindingType.TEXTURE_2D,
            group = MATERIAL_TEXTURE_GROUP,
            binding = 0,
            source = MaterialBindingSource.ALBEDO_MAP,
            required = false
        ),
        MaterialBinding(
            name = "albedoSampler",
            type = MaterialBindingType.SAMPLER,
            group = MATERIAL_TEXTURE_GROUP,
            binding = 1,
            source = MaterialBindingSource.ALBEDO_MAP,
            required = false
        )
    )

    private fun normalBindings(): List<MaterialBinding> = listOf(
        MaterialBinding(
            name = "normalTexture",
            type = MaterialBindingType.TEXTURE_2D,
            group = MATERIAL_TEXTURE_GROUP,
            binding = 2,
            source = MaterialBindingSource.NORMAL_MAP,
            required = false
        ),
        MaterialBinding(
            name = "normalSampler",
            type = MaterialBindingType.SAMPLER,
            group = MATERIAL_TEXTURE_GROUP,
            binding = 3,
            source = MaterialBindingSource.NORMAL_MAP,
            required = false
        )
    )

    private fun environmentBindings(): List<MaterialBinding> = listOf(
        MaterialBinding(
            name = "prefilterTexture",
            type = MaterialBindingType.TEXTURE_CUBE,
            group = ENVIRONMENT_GROUP,
            binding = 0,
            source = MaterialBindingSource.ENVIRONMENT_PREFILTER,
            required = true
        ),
        MaterialBinding(
            name = "prefilterSampler",
            type = MaterialBindingType.SAMPLER,
            group = ENVIRONMENT_GROUP,
            binding = 1,
            source = MaterialBindingSource.ENVIRONMENT_PREFILTER,
            required = true
        )
    )

}

internal fun MaterialDescriptor.requiresBinding(source: MaterialBindingSource): Boolean =
    bindings.any { it.source == source && it.required }

internal fun MaterialDescriptor.bindingGroups(source: MaterialBindingSource): Set<Int> =
    bindings.filter { it.source == source }.mapTo(mutableSetOf()) { it.group }

private fun resolveBasic(
    descriptor: MaterialDescriptor,
    material: MeshBasicMaterial
): ResolvedMaterialDescriptor {
    val blendState = blendStateFor(material.blending, material.transparent, material.opacity)
    val state = descriptor.renderState
        .applyCommonOverrides(
            depthTest = material.depthTest,
            depthWrite = material.depthWrite,
            colorWrite = material.colorWrite,
            side = material.side.toCommonSide(),
            hasBlend = blendState != null
        )
        .withBlend(blendState)
    return ResolvedMaterialDescriptor(descriptor, state)
}

private fun resolveStandard(
    descriptor: MaterialDescriptor,
    material: MeshStandardMaterial
): ResolvedMaterialDescriptor {
    val blendState = blendStateFor(material.blending.toCommonBlending(), material.transparent, material.opacity)
    val state = descriptor.renderState
        .applyCommonOverrides(
            depthTest = material.depthTest,
            depthWrite = material.depthWrite,
            colorWrite = material.colorWrite,
            side = material.side.toCommonSide(),
            hasBlend = blendState != null
        )
        .withBlend(blendState)
    return ResolvedMaterialDescriptor(descriptor, state)
}

private fun MaterialRenderState.applyCommonOverrides(
    depthTest: Boolean,
    depthWrite: Boolean,
    colorWrite: Boolean,
    side: Side,
    hasBlend: Boolean
): MaterialRenderState {
    val cullModeOverride = when (side) {
        Side.FrontSide -> CullMode.BACK
        Side.BackSide -> CullMode.FRONT
        Side.DoubleSide -> CullMode.NONE
    }
    val writeMask = if (colorWrite) ColorWriteMask.ALL else ColorWriteMask.NONE
    return copy(
        cullMode = cullModeOverride,
        depthTest = depthTest,
        depthWrite = if (hasBlend) false else depthWrite,
        colorTarget = colorTarget.copy(writeMask = writeMask)
    )
}

private fun MaterialRenderState.withBlend(blendState: BlendState?): MaterialRenderState =
    copy(colorTarget = colorTarget.copy(blendState = blendState))

private fun blendStateFor(
    mode: Blending,
    transparent: Boolean,
    opacity: Float
): BlendState? {
    if (mode == Blending.NoBlending) {
        return null
    }
    val needsBlend = transparent || opacity < 1f || mode != Blending.NormalBlending
    if (!needsBlend) return null
    return when (mode) {
        Blending.NormalBlending -> alphaBlend()
        Blending.AdditiveBlending -> additiveBlend()
        Blending.SubtractiveBlending -> subtractiveBlend()
        Blending.MultiplyBlending -> multiplyBlend()
        Blending.CustomBlending -> alphaBlend()
        Blending.NoBlending -> null
    }
}

private fun alphaBlend(): BlendState = BlendState(
    color = BlendComponent(
        srcFactor = BlendFactor.SRC_ALPHA,
        dstFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
        operation = BlendOperation.ADD
    ),
    alpha = BlendComponent(
        srcFactor = BlendFactor.ONE,
        dstFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
        operation = BlendOperation.ADD
    )
)

private fun additiveBlend(): BlendState = BlendState(
    color = BlendComponent(
        srcFactor = BlendFactor.SRC_ALPHA,
        dstFactor = BlendFactor.ONE,
        operation = BlendOperation.ADD
    ),
    alpha = BlendComponent(
        srcFactor = BlendFactor.ONE,
        dstFactor = BlendFactor.ONE,
        operation = BlendOperation.ADD
    )
)

private fun subtractiveBlend(): BlendState = BlendState(
    color = BlendComponent(
        srcFactor = BlendFactor.SRC_ALPHA,
        dstFactor = BlendFactor.ONE,
        operation = BlendOperation.REVERSE_SUBTRACT
    ),
    alpha = BlendComponent(
        srcFactor = BlendFactor.ONE,
        dstFactor = BlendFactor.ONE,
        operation = BlendOperation.REVERSE_SUBTRACT
    )
)

private fun multiplyBlend(): BlendState = BlendState(
    color = BlendComponent(
        srcFactor = BlendFactor.DST,
        dstFactor = BlendFactor.ZERO,
        operation = BlendOperation.ADD
    ),
    alpha = BlendComponent(
        srcFactor = BlendFactor.ONE,
        dstFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
        operation = BlendOperation.ADD
    )
)
private fun StandardBlendMode.toCommonBlending(): Blending = when (this) {
    StandardBlendMode.NORMAL -> Blending.NormalBlending
    StandardBlendMode.ADDITIVE -> Blending.AdditiveBlending
    StandardBlendMode.SUBTRACTIVE -> Blending.SubtractiveBlending
    StandardBlendMode.MULTIPLY -> Blending.MultiplyBlending
    StandardBlendMode.CUSTOM -> Blending.CustomBlending
}

private fun StandardMaterialSide.toCommonSide(): Side = when (this) {
    StandardMaterialSide.FRONT -> Side.FrontSide
    StandardMaterialSide.BACK -> Side.BackSide
    StandardMaterialSide.DOUBLE -> Side.DoubleSide
}

private fun Side.toCommonSide(): Side = this
