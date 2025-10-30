package io.materia.renderer.shader

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.atomicfu.locks.synchronized as atomicSynchronized

/**
 * Descriptor identifying the chunks required to assemble a material shader. The descriptor is used
 * as a cache key for compiled shader sources.
 */
data class MaterialShaderDescriptor(
    val key: String,
    val vertexChunks: List<String>,
    val fragmentChunks: List<String>,
    val replacements: Map<String, String> = emptyMap()
)

fun MaterialShaderDescriptor.withOverrides(overrides: Map<String, String>): MaterialShaderDescriptor {
    if (overrides.isEmpty()) return this
    if (overrides.entries.all { replacements[it.key] == it.value }) {
        return this
    }
    val merged = replacements.toMutableMap()
    overrides.forEach { (key, value) -> merged[key] = value }
    return copy(replacements = merged)
}

/**
 * Fully assembled shader source code for a material.
 */
data class MaterialShaderSource(
    val vertexSource: String,
    val fragmentSource: String
)

/**
 * Compiles shader descriptors into full WGSL/GLSL strings using [ShaderChunkRegistry]. Results are
 * cached by descriptor to avoid redundant string assembly.
 */
object MaterialShaderGenerator {
    private val cache = atomic(persistentMapOf<MaterialShaderDescriptor, MaterialShaderSource>())

    fun compile(descriptor: MaterialShaderDescriptor): MaterialShaderSource {
        MaterialShaderLibrary.ensureBuiltInsRegistered()
        cache.value[descriptor]?.let { return it }

        val vertex = ShaderChunkRegistry.assemble(
            chunkNames = descriptor.vertexChunks,
            stage = ShaderStageType.VERTEX,
            replacements = descriptor.replacements
        )
        val fragment = ShaderChunkRegistry.assemble(
            chunkNames = descriptor.fragmentChunks,
            stage = ShaderStageType.FRAGMENT,
            replacements = descriptor.replacements
        )
        val compiled = MaterialShaderSource(vertex, fragment)

        while (true) {
            val current = cache.value
            current[descriptor]?.let { return it }
            val updated = current.put(descriptor, compiled)
            if (cache.compareAndSet(current, updated)) {
                return compiled
            }
        }
    }

    internal fun clearCacheForTests() {
        cache.value = persistentMapOf()
    }
}

/**
 * Provides high-level material descriptors and handles registration of the built-in shader chunks.
 */
object MaterialShaderLibrary {
    private val initLock = SynchronizedObject()
    private val builtInsRegistered = atomic(false)

    private val basicDescriptor = MaterialShaderDescriptor(
        key = "material.basic",
        vertexChunks = listOf("material.basic.vertex.main"),
        fragmentChunks = listOf("material.basic.fragment.main")
    )

    private val meshStandardDescriptor = MaterialShaderDescriptor(
        key = "material.meshStandard",
        vertexChunks = listOf("material.pbr.vertex.main"),
        fragmentChunks = listOf("material.pbr.fragment.main")
    )

    fun basic(): MaterialShaderDescriptor {
        ensureBuiltInsRegistered()
        return basicDescriptor
    }

    fun meshStandard(): MaterialShaderDescriptor {
        ensureBuiltInsRegistered()
        return meshStandardDescriptor
    }

    internal fun ensureBuiltInsRegistered() {
        if (builtInsRegistered.value) return
        withInitLock {
            if (!builtInsRegistered.value) {
                ShaderChunkRegistry.registerAll(
                    BuiltInMaterialChunks.defaults,
                    replaceExisting = true
                )
                builtInsRegistered.value = true
            }
        }
    }

    internal fun resetForTests() {
        withInitLock {
            builtInsRegistered.value = false
        }
    }

    private inline fun <T> withInitLock(block: () -> T): T = atomicSynchronized(initLock, block)
}

private object BuiltInMaterialChunks {
    val defaults: List<ShaderChunk> = listOf(
        ShaderChunk(
            name = "common.uniforms",
            source = """
                struct Uniforms {
                    projectionMatrix: mat4x4<f32>,
                    viewMatrix: mat4x4<f32>,
                    modelMatrix: mat4x4<f32>,
                    baseColor: vec4<f32>,
                    pbrParams: vec4<f32>,
                    cameraPosition: vec4<f32>,
                    ambientColor: vec4<f32>,
                    fogColor: vec4<f32>,
                    fogParams: vec4<f32>,
                    mainLightDirection: vec4<f32>,
                    mainLightColor: vec4<f32>,
                    morphInfluences0: vec4<f32>,
                    morphInfluences1: vec4<f32>,
                };

                @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.basic.vertex.input",
            stage = ShaderStageType.VERTEX,
            source = """
                struct VertexInput {
                    @location(0) position: vec3<f32>,
                    @location(1) normal: vec3<f32>,
                    @location(2) color: vec3<f32>,
                    {{VERTEX_INPUT_EXTRA}}
                };

                struct BasicVertexOutput {
                    @builtin(position) position: vec4<f32>,
                    @location(0) color: vec3<f32>,
                    {{VERTEX_OUTPUT_EXTRA}}
                };
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.basic.vertex.main",
            stage = ShaderStageType.VERTEX,
            source = """
                #include <common.uniforms>
                #include <material.basic.vertex.input>

                @vertex
                fn vs_main(in: VertexInput) -> BasicVertexOutput {
                    var out: BasicVertexOutput;
                    var position = in.position;
                    var normal = in.normal;
                    var vertexColor = in.color;
                    {{VERTEX_ASSIGN_EXTRA}}
                    let worldPosition = uniforms.modelMatrix * vec4<f32>(position, 1.0);
                    let viewPosition = uniforms.viewMatrix * worldPosition;
                    out.position = uniforms.projectionMatrix * viewPosition;
                    let materialColor = uniforms.baseColor.rgb;
                    out.color = materialColor * vertexColor;
                    return out;
                }
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.basic.fragment.input",
            stage = ShaderStageType.FRAGMENT,
            source = """
                struct BasicFragmentInput {
                    @location(0) color: vec3<f32>,
                    {{FRAGMENT_INPUT_EXTRA}}
                };
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.basic.fragment.main",
            stage = ShaderStageType.FRAGMENT,
            source = """
                #include <common.uniforms>
                #include <material.basic.fragment.input>
                {{FRAGMENT_BINDINGS}}

                @fragment
                fn fs_main(in: BasicFragmentInput) -> @location(0) vec4<f32> {
                    var color = in.color;
                    {{FRAGMENT_INIT_EXTRA}}
                    {{FRAGMENT_EXTRA}}
                    return vec4<f32>(color, uniforms.baseColor.a);
                }
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.vertex.input",
            stage = ShaderStageType.VERTEX,
            source = """
                struct PbrVertexInput {
                    @location(0) position: vec3<f32>,
                    @location(1) normal: vec3<f32>,
                    @location(2) color: vec3<f32>,
                    {{VERTEX_INPUT_EXTRA}}
                };

                struct PbrVertexOutput {
                    @builtin(position) position: vec4<f32>,
                    @location(0) worldNormal: vec3<f32>,
                    @location(1) viewDir: vec3<f32>,
                    @location(2) albedo: vec3<f32>,
                    {{VERTEX_OUTPUT_EXTRA}}
                };
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.vertex.main",
            stage = ShaderStageType.VERTEX,
            source = """
                #include <common.uniforms>
                #include <material.pbr.vertex.input>

                @vertex
                fn vs_main(in: PbrVertexInput) -> PbrVertexOutput {
                    var out: PbrVertexOutput;
                    var position = in.position;
                    var normal = in.normal;
                    var vertexColor = max(in.color, vec3<f32>(1.0));
                    {{VERTEX_ASSIGN_EXTRA}}

                    let worldPosition = uniforms.modelMatrix * vec4<f32>(position, 1.0);
                    let viewPosition = uniforms.viewMatrix * worldPosition;
                    out.position = uniforms.projectionMatrix * viewPosition;

                    let normalMatrix = mat3x3<f32>(
                        uniforms.modelMatrix[0].xyz,
                        uniforms.modelMatrix[1].xyz,
                        uniforms.modelMatrix[2].xyz
                    );
                    out.worldNormal = normalize(normalMatrix * normal);

                    let cameraPos = uniforms.cameraPosition.xyz;
                    out.viewDir = cameraPos - worldPosition.xyz;

                    let materialColor = uniforms.baseColor.rgb;
                    out.albedo = materialColor * vertexColor;
                    return out;
                }
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.fragment.bindings",
            stage = ShaderStageType.FRAGMENT,
            source = """
                @group(2) @binding(0) var prefilterTexture: texture_cube<f32>;
                @group(2) @binding(1) var prefilterSampler: sampler;
                @group(2) @binding(2) var brdfLutTexture: texture_2d<f32>;
                @group(2) @binding(3) var brdfLutSampler: sampler;
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.fragment.functions",
            stage = ShaderStageType.FRAGMENT,
            source = """
                fn roughness_to_mip(roughness: f32, mipCount: f32) -> f32 {
                    if (mipCount <= 1.0) {
                        return 0.0;
                    }
                    let clamped = clamp(roughness, 0.0, 1.0);
                    let perceptual = clamped * clamped;
                    let maxLevel = mipCount - 1.0;
                    return min(maxLevel, perceptual * maxLevel);
                }

                fn mix_vec3(a: vec3<f32>, b: vec3<f32>, factor: f32) -> vec3<f32> {
                    return a * (1.0 - factor) + b * factor;
                }

                fn saturate(value: f32) -> f32 {
                    return clamp(value, 0.0, 1.0);
                }
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.fragment.input",
            stage = ShaderStageType.FRAGMENT,
            source = """
                struct PbrFragmentInput {
                    @location(0) worldNormal: vec3<f32>,
                    @location(1) viewDir: vec3<f32>,
                    @location(2) albedo: vec3<f32>,
                    {{FRAGMENT_INPUT_EXTRA}}
                };
            """.trimIndent()
        ),
        ShaderChunk(
            name = "material.pbr.fragment.main",
            stage = ShaderStageType.FRAGMENT,
            source = """
                #include <common.uniforms>
                #include <material.pbr.fragment.bindings>
                #include <material.pbr.fragment.functions>
                #include <material.pbr.fragment.input>
                {{FRAGMENT_BINDINGS}}

                @fragment
                fn fs_main(in: PbrFragmentInput) -> @location(0) vec4<f32> {
                    var N = normalize(in.worldNormal);
                    let V = normalize(in.viewDir);
                    var baseColor = clamp(in.albedo, vec3<f32>(0.0), vec3<f32>(1.0));
                    {{FRAGMENT_INIT_EXTRA}}

                    let roughness = uniforms.pbrParams.x;
                    let metalness = uniforms.pbrParams.y;
                    let envIntensity = uniforms.pbrParams.z;
                    let mipCount = uniforms.pbrParams.w;

                    var reflection = vec3<f32>(0.0);
                    var NdotV = 0.0;
                    if (length(V) > 0.0) {
                        let R = reflect(-V, N);
                        let lod = roughness_to_mip(roughness, mipCount);
                        let sampled = textureSampleLevel(prefilterTexture, prefilterSampler, R, lod);
                        reflection = sampled.rgb;
                        NdotV = saturate(dot(N, V));
                    }

                    let F0 = mix_vec3(vec3<f32>(0.04), baseColor, metalness);
                    let brdfSample = textureSample(brdfLutTexture, brdfLutSampler, vec2<f32>(NdotV, roughness)).rg;
                    let specular = reflection * (F0 * brdfSample.x + vec3<f32>(brdfSample.y)) * envIntensity;
                    let diffuse = baseColor * (1.0 - metalness);
                    var color = clamp(diffuse + specular, vec3<f32>(0.0), vec3<f32>(1.0));
                    {{FRAGMENT_EXTRA}}
                    return vec4<f32>(color, 1.0);
                }
            """.trimIndent()
        )
    )
}
