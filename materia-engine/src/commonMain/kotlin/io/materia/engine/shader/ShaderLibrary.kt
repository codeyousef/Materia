/**
 * WGSL Uber Shader Library
 *
 * Contains shader source code as Kotlin string constants for maximum portability.
 * Uses preprocessor-like #define injection for material configuration.
 *
 * This approach avoids file I/O complexity across JS (bundling) and iOS (app bundles).
 */
package io.materia.engine.shader

/**
 * Shader feature flags for uber shader configuration.
 */
enum class ShaderFeature(val define: String) {
    /** Enable base color texture sampling */
    USE_TEXTURE("USE_TEXTURE"),

    /** Enable normal mapping */
    USE_NORMAL_MAP("USE_NORMAL_MAP"),

    /** Enable PBR metallic-roughness textures */
    USE_METALLIC_ROUGHNESS_MAP("USE_METALLIC_ROUGHNESS_MAP"),

    /** Enable ambient occlusion map */
    USE_AO_MAP("USE_AO_MAP"),

    /** Enable emissive map */
    USE_EMISSIVE_MAP("USE_EMISSIVE_MAP"),

    /** Enable directional lighting */
    USE_DIRECTIONAL_LIGHT("USE_DIRECTIONAL_LIGHT"),

    /** Enable point lights */
    USE_POINT_LIGHTS("USE_POINT_LIGHTS"),

    /** Enable vertex colors */
    USE_VERTEX_COLORS("USE_VERTEX_COLORS"),

    /** Enable fog effect */
    USE_FOG("USE_FOG"),

    /** Enable skinning (bone transforms) */
    USE_SKINNING("USE_SKINNING"),

    /** Enable morph targets */
    USE_MORPH_TARGETS("USE_MORPH_TARGETS"),

    /** Enable instancing */
    USE_INSTANCING("USE_INSTANCING"),

    /** Enable alpha cutout/masking */
    USE_ALPHA_CUTOFF("USE_ALPHA_CUTOFF"),

    /** Enable shadow mapping */
    USE_SHADOWS("USE_SHADOWS")
}

/**
 * Shader source code library containing the uber shader.
 *
 * All shader code is stored as Kotlin string constants for cross-platform
 * compatibility. No file I/O required.
 */
object ShaderLibrary {

    /**
     * Generates preprocessor defines string for the given features.
     */
    fun generateDefines(features: Set<ShaderFeature>): String {
        return features.joinToString("\n") { feature ->
            "const ${feature.define}: bool = true;"
        } + "\n" + ShaderFeature.entries.filter { it !in features }.joinToString("\n") { feature ->
            "const ${feature.define}: bool = false;"
        }
    }

    /**
     * Common structures used across shaders.
     */
    val COMMON_STRUCTURES = """
// ============================================================================
// Common Structures
// ============================================================================

struct CameraUniforms {
    viewMatrix: mat4x4<f32>,
    projectionMatrix: mat4x4<f32>,
    viewProjectionMatrix: mat4x4<f32>,
    cameraPosition: vec3<f32>,
    near: f32,
    far: f32,
    _padding: vec3<f32>,
};

struct ModelUniforms {
    modelMatrix: mat4x4<f32>,
    normalMatrix: mat4x4<f32>,
};

struct MaterialUniforms {
    baseColor: vec4<f32>,
    emissive: vec3<f32>,
    metallic: f32,
    roughness: f32,
    alphaCutoff: f32,
    normalScale: f32,
    aoStrength: f32,
};

struct DirectionalLight {
    direction: vec3<f32>,
    intensity: f32,
    color: vec3<f32>,
    _padding: f32,
};

struct PointLight {
    position: vec3<f32>,
    intensity: f32,
    color: vec3<f32>,
    range: f32,
};

struct FogUniforms {
    color: vec3<f32>,
    density: f32,
    near: f32,
    far: f32,
    _padding: vec2<f32>,
};
""".trimIndent()

    /**
     * Standard vertex shader with all features.
     */
    val STANDARD_VERTEX_SHADER = """
// ============================================================================
// Standard Vertex Shader
// ============================================================================

// Bind group 0: Per-frame uniforms
@group(0) @binding(0) var<uniform> camera: CameraUniforms;

// Bind group 1: Per-object uniforms
@group(1) @binding(0) var<uniform> model: ModelUniforms;

// Vertex input
struct VertexInput {
    @location(0) position: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) uv: vec2<f32>,
#ifdef USE_VERTEX_COLORS
    @location(3) color: vec4<f32>,
#endif
#ifdef USE_TANGENT
    @location(4) tangent: vec4<f32>,
#endif
};

// Vertex output
struct VertexOutput {
    @builtin(position) clipPosition: vec4<f32>,
    @location(0) worldPosition: vec3<f32>,
    @location(1) worldNormal: vec3<f32>,
    @location(2) uv: vec2<f32>,
#ifdef USE_VERTEX_COLORS
    @location(3) vertexColor: vec4<f32>,
#endif
#ifdef USE_TANGENT
    @location(4) worldTangent: vec3<f32>,
    @location(5) worldBitangent: vec3<f32>,
#endif
};

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;

    // Transform position to world space
    let worldPosition = (model.modelMatrix * vec4<f32>(input.position, 1.0)).xyz;
    output.worldPosition = worldPosition;

    // Transform to clip space
    output.clipPosition = camera.viewProjectionMatrix * vec4<f32>(worldPosition, 1.0);

    // Transform normal to world space (using normal matrix for non-uniform scaling)
    output.worldNormal = normalize((model.normalMatrix * vec4<f32>(input.normal, 0.0)).xyz);

    // Pass through UV
    output.uv = input.uv;

#ifdef USE_VERTEX_COLORS
    output.vertexColor = input.color;
#endif

#ifdef USE_TANGENT
    output.worldTangent = normalize((model.modelMatrix * vec4<f32>(input.tangent.xyz, 0.0)).xyz);
    output.worldBitangent = cross(output.worldNormal, output.worldTangent) * input.tangent.w;
#endif

    return output;
}
""".trimIndent()

    /**
     * Standard fragment shader with PBR lighting.
     */
    val STANDARD_FRAGMENT_SHADER = """
// ============================================================================
// Standard Fragment Shader (PBR)
// ============================================================================

// Bind group 0: Per-frame uniforms
@group(0) @binding(0) var<uniform> camera: CameraUniforms;
@group(0) @binding(1) var<uniform> directionalLight: DirectionalLight;
#ifdef USE_FOG
@group(0) @binding(2) var<uniform> fog: FogUniforms;
#endif

// Bind group 1: Per-object uniforms
@group(1) @binding(0) var<uniform> model: ModelUniforms;

// Bind group 2: Material uniforms and textures
@group(2) @binding(0) var<uniform> material: MaterialUniforms;
#ifdef USE_TEXTURE
@group(2) @binding(1) var baseColorTexture: texture_2d<f32>;
@group(2) @binding(2) var baseColorSampler: sampler;
#endif
#ifdef USE_NORMAL_MAP
@group(2) @binding(3) var normalTexture: texture_2d<f32>;
@group(2) @binding(4) var normalSampler: sampler;
#endif
#ifdef USE_METALLIC_ROUGHNESS_MAP
@group(2) @binding(5) var metallicRoughnessTexture: texture_2d<f32>;
@group(2) @binding(6) var metallicRoughnessSampler: sampler;
#endif

// Fragment input (from vertex shader)
struct FragmentInput {
    @builtin(position) clipPosition: vec4<f32>,
    @location(0) worldPosition: vec3<f32>,
    @location(1) worldNormal: vec3<f32>,
    @location(2) uv: vec2<f32>,
#ifdef USE_VERTEX_COLORS
    @location(3) vertexColor: vec4<f32>,
#endif
#ifdef USE_TANGENT
    @location(4) worldTangent: vec3<f32>,
    @location(5) worldBitangent: vec3<f32>,
#endif
};

// PBR Constants
const PI: f32 = 3.14159265359;
const DIELECTRIC_F0: vec3<f32> = vec3<f32>(0.04, 0.04, 0.04);

// Normal Distribution Function (GGX/Trowbridge-Reitz)
fn distributionGGX(N: vec3<f32>, H: vec3<f32>, roughness: f32) -> f32 {
    let a = roughness * roughness;
    let a2 = a * a;
    let NdotH = max(dot(N, H), 0.0);
    let NdotH2 = NdotH * NdotH;

    let num = a2;
    var denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return num / denom;
}

// Geometry function (Schlick-GGX)
fn geometrySchlickGGX(NdotV: f32, roughness: f32) -> f32 {
    let r = roughness + 1.0;
    let k = (r * r) / 8.0;

    let num = NdotV;
    let denom = NdotV * (1.0 - k) + k;

    return num / denom;
}

fn geometrySmith(N: vec3<f32>, V: vec3<f32>, L: vec3<f32>, roughness: f32) -> f32 {
    let NdotV = max(dot(N, V), 0.0);
    let NdotL = max(dot(N, L), 0.0);
    let ggx2 = geometrySchlickGGX(NdotV, roughness);
    let ggx1 = geometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

// Fresnel (Schlick approximation)
fn fresnelSchlick(cosTheta: f32, F0: vec3<f32>) -> vec3<f32> {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

@fragment
fn main(input: FragmentInput) -> @location(0) vec4<f32> {
    // Get base color
    var baseColor = material.baseColor;
#ifdef USE_TEXTURE
    baseColor = baseColor * textureSample(baseColorTexture, baseColorSampler, input.uv);
#endif
#ifdef USE_VERTEX_COLORS
    baseColor = baseColor * input.vertexColor;
#endif

    // Alpha cutoff
#ifdef USE_ALPHA_CUTOFF
    if (baseColor.a < material.alphaCutoff) {
        discard;
    }
#endif

    // Get normal
    var N = normalize(input.worldNormal);
#ifdef USE_NORMAL_MAP
    let tangentNormal = textureSample(normalTexture, normalSampler, input.uv).xyz * 2.0 - 1.0;
    let TBN = mat3x3<f32>(
        normalize(input.worldTangent),
        normalize(input.worldBitangent),
        N
    );
    N = normalize(TBN * (tangentNormal * vec3<f32>(material.normalScale, material.normalScale, 1.0)));
#endif

    // Get metallic and roughness
    var metallic = material.metallic;
    var roughness = material.roughness;
#ifdef USE_METALLIC_ROUGHNESS_MAP
    let metallicRoughness = textureSample(metallicRoughnessTexture, metallicRoughnessSampler, input.uv);
    metallic = metallic * metallicRoughness.b;
    roughness = roughness * metallicRoughness.g;
#endif
    roughness = clamp(roughness, 0.04, 1.0);

    // Calculate view direction
    let V = normalize(camera.cameraPosition - input.worldPosition);

    // Calculate F0 (surface reflection at zero incidence)
    let F0 = mix(DIELECTRIC_F0, baseColor.rgb, metallic);

    // Lighting calculation
    var Lo = vec3<f32>(0.0);

#ifdef USE_DIRECTIONAL_LIGHT
    // Directional light contribution
    let L = normalize(-directionalLight.direction);
    let H = normalize(V + L);

    let NDF = distributionGGX(N, H, roughness);
    let G = geometrySmith(N, V, L, roughness);
    let F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    let kS = F;
    let kD = (vec3<f32>(1.0) - kS) * (1.0 - metallic);

    let numerator = NDF * G * F;
    let denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
    let specular = numerator / denominator;

    let NdotL = max(dot(N, L), 0.0);
    let radiance = directionalLight.color * directionalLight.intensity;
    Lo = Lo + (kD * baseColor.rgb / PI + specular) * radiance * NdotL;
#endif

    // Ambient (very simple)
    let ambient = vec3<f32>(0.03) * baseColor.rgb;

    // Emissive
    let emissive = material.emissive;

    // Final color
    var color = ambient + Lo + emissive;

    // HDR tonemapping (Reinhard)
    color = color / (color + vec3<f32>(1.0));

    // Gamma correction
    color = pow(color, vec3<f32>(1.0 / 2.2));

#ifdef USE_FOG
    // Linear fog
    let fogDistance = length(input.worldPosition - camera.cameraPosition);
    let fogFactor = clamp((fog.far - fogDistance) / (fog.far - fog.near), 0.0, 1.0);
    color = mix(fog.color, color, fogFactor);
#endif

    return vec4<f32>(color, baseColor.a);
}
""".trimIndent()

    /**
     * Unlit vertex shader (simple, no lighting).
     */
    val UNLIT_VERTEX_SHADER = """
// ============================================================================
// Unlit Vertex Shader
// ============================================================================

struct Uniforms {
    modelViewProjection: mat4x4<f32>,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

struct VertexInput {
    @location(0) position: vec3<f32>,
    @location(1) color: vec3<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) color: vec3<f32>,
};

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;
    output.position = uniforms.modelViewProjection * vec4<f32>(input.position, 1.0);
    output.color = input.color;
    return output;
}
""".trimIndent()

    /**
     * Unlit fragment shader (simple, no lighting).
     */
    val UNLIT_FRAGMENT_SHADER = """
// ============================================================================
// Unlit Fragment Shader
// ============================================================================

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) color: vec3<f32>,
};

@fragment
fn main(input: VertexOutput) -> @location(0) vec4<f32> {
    return vec4<f32>(input.color, 1.0);
}
""".trimIndent()

    /**
     * Combines defines with shader source.
     */
    fun compileShader(source: String, features: Set<ShaderFeature>): String {
        val defines = generateDefines(features)
        return "$defines\n\n$COMMON_STRUCTURES\n\n$source"
    }
}

/**
 * Precompiled shader variants for common configurations.
 */
object ShaderVariants {
    /** Basic unlit shader with vertex colors */
    val UNLIT_VERTEX_COLOR = ShaderVariant(
        vertex = ShaderLibrary.UNLIT_VERTEX_SHADER,
        fragment = ShaderLibrary.UNLIT_FRAGMENT_SHADER,
        features = emptySet()
    )

    /** Standard PBR with texture */
    val STANDARD_TEXTURED = ShaderVariant(
        vertex = ShaderLibrary.STANDARD_VERTEX_SHADER,
        fragment = ShaderLibrary.STANDARD_FRAGMENT_SHADER,
        features = setOf(
            ShaderFeature.USE_TEXTURE,
            ShaderFeature.USE_DIRECTIONAL_LIGHT
        )
    )

    /** Standard PBR with normal map */
    val STANDARD_NORMAL_MAPPED = ShaderVariant(
        vertex = ShaderLibrary.STANDARD_VERTEX_SHADER,
        fragment = ShaderLibrary.STANDARD_FRAGMENT_SHADER,
        features = setOf(
            ShaderFeature.USE_TEXTURE,
            ShaderFeature.USE_NORMAL_MAP,
            ShaderFeature.USE_DIRECTIONAL_LIGHT
        )
    )
}

/**
 * A compiled shader variant with specific features enabled.
 */
data class ShaderVariant(
    val vertex: String,
    val fragment: String,
    val features: Set<ShaderFeature>
) {
    val compiledVertex: String by lazy {
        ShaderLibrary.compileShader(vertex, features)
    }

    val compiledFragment: String by lazy {
        ShaderLibrary.compileShader(fragment, features)
    }
}
