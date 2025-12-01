package io.materia.postprocessing

import io.materia.camera.Camera
import io.materia.core.scene.Scene
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Scalable Ambient Obscurance (SAO) post-processing pass.
 *
 * SAO is an improved ambient occlusion technique that uses a scalable
 * sampling pattern and bilateral blur for high-quality results at
 * various resolutions. It is faster than SSAO for large kernel sizes.
 *
 * Based on the paper "Scalable Ambient Obscurance" by McGuire et al.
 * and Three.js SAOPass implementation.
 *
 * Key differences from SSAO:
 * - Uses alchemy-hashing for better sample distribution
 * - Bilateral blur preserves edges better
 * - More scalable for high-resolution rendering
 *
 * Usage:
 * ```kotlin
 * val saoPass = SAOPass(scene, camera, width, height)
 * saoPass.saoBias = 0.5f
 * saoPass.saoIntensity = 0.25f
 * saoPass.saoScale = 1f
 * 
 * composer.addPass(renderPass)
 * composer.addPass(saoPass)
 * composer.addPass(outputPass)
 * ```
 */
class SAOPass(
    var scene: Scene,
    var camera: Camera,
    width: Int = 512,
    height: Int = 512,
    config: SAOConfig = SAOConfig()
) : Pass() {

    /**
     * SAO configuration parameters
     */
    data class SAOConfig(
        /** Bias to prevent self-occlusion (0.0 to 1.0) */
        val saoBias: Float = 0.5f,
        /** Intensity of the ambient occlusion effect */
        val saoIntensity: Float = 0.25f,
        /** Scale of the sampling radius */
        val saoScale: Float = 1f,
        /** Number of samples per pixel */
        val saoKernelRadius: Int = 100,
        /** Minimum resolution for SAO calculation */
        val saoMinResolution: Float = 0f,
        /** Size of the blur kernel */
        val saoBlurRadius: Int = 8,
        /** Enable bilateral blur for edge preservation */
        val saoBlur: Boolean = true,
        /** Standard deviation for bilateral blur depth weighting */
        val saoBlurStdDev: Float = 4f,
        /** Depth cutoff for bilateral blur */
        val saoBlurDepthCutoff: Float = 0.01f,
        /** Output mode for debugging */
        val output: SAOOutput = SAOOutput.Default
    )

    /**
     * SAO output mode
     */
    enum class SAOOutput {
        /** Normal SAO blended with scene */
        Default,
        /** Just the SAO factor */
        SAO,
        /** Depth buffer visualization */
        Depth,
        /** Normal buffer visualization */
        Normal
    }

    // Configuration
    private var config = config.copy()

    /** Bias to prevent self-occlusion */
    var saoBias: Float
        get() = config.saoBias
        set(value) { config = config.copy(saoBias = value) }

    /** Intensity of the effect */
    var saoIntensity: Float
        get() = config.saoIntensity
        set(value) { config = config.copy(saoIntensity = value) }

    /** Scale of sampling radius */
    var saoScale: Float
        get() = config.saoScale
        set(value) { config = config.copy(saoScale = value) }

    /** Number of samples */
    var saoKernelRadius: Int
        get() = config.saoKernelRadius
        set(value) { config = config.copy(saoKernelRadius = value.coerceIn(1, 256)) }

    /** Minimum resolution */
    var saoMinResolution: Float
        get() = config.saoMinResolution
        set(value) { config = config.copy(saoMinResolution = value) }

    /** Blur kernel size */
    var saoBlurRadius: Int
        get() = config.saoBlurRadius
        set(value) { config = config.copy(saoBlurRadius = value.coerceIn(0, 32)) }

    /** Enable blur */
    var saoBlur: Boolean
        get() = config.saoBlur
        set(value) { config = config.copy(saoBlur = value) }

    /** Blur std dev */
    var saoBlurStdDev: Float
        get() = config.saoBlurStdDev
        set(value) { config = config.copy(saoBlurStdDev = value) }

    /** Blur depth cutoff */
    var saoBlurDepthCutoff: Float
        get() = config.saoBlurDepthCutoff
        set(value) { config = config.copy(saoBlurDepthCutoff = value) }

    /** Output mode */
    var output: SAOOutput
        get() = config.output
        set(value) { config = config.copy(output = value) }

    // Internal render targets
    private var depthRenderTarget: RenderTarget? = null
    private var normalRenderTarget: RenderTarget? = null
    private var saoRenderTarget: RenderTarget? = null
    private var blurIntermediateRenderTarget: RenderTarget? = null

    // Shader materials
    private var saoMaterial: ShaderMaterial? = null
    private var normalMaterial: ShaderMaterial? = null
    private var blurMaterial: ShaderMaterial? = null
    private var copyMaterial: ShaderMaterial? = null
    private var depthCopyMaterial: ShaderMaterial? = null

    // Full-screen quad
    private val fsQuad = FullScreenQuad()

    init {
        this.width = width
        this.height = height

        initMaterials()
        initRenderTargets()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        depthRenderTarget?.setSize(width, height)
        normalRenderTarget?.setSize(width, height)
        saoRenderTarget?.setSize(width, height)
        blurIntermediateRenderTarget?.setSize(width, height)

        saoMaterial?.setUniform("size", floatArrayOf(width.toFloat(), height.toFloat()))
        blurMaterial?.setUniform("size", floatArrayOf(width.toFloat(), height.toFloat()))
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // 1. Render depth
        renderDepth(renderer)

        // 2. Render normals
        renderNormals(renderer)

        // 3. Render SAO
        renderSAO(renderer)

        // 4. Blur if enabled
        if (saoBlur) {
            renderBlur(renderer)
        }

        // 5. Output based on mode
        when (output) {
            SAOOutput.SAO -> {
                val source = if (saoBlur) blurIntermediateRenderTarget else saoRenderTarget
                copyToOutput(renderer, source!!, writeBuffer)
            }
            SAOOutput.Depth -> {
                renderDepthVisualization(renderer, writeBuffer)
            }
            SAOOutput.Normal -> {
                copyToOutput(renderer, normalRenderTarget!!, writeBuffer)
            }
            SAOOutput.Default -> {
                compositeSAO(renderer, readBuffer, writeBuffer)
            }
        }
    }

    private fun renderDepth(renderer: Renderer) {
        val target = depthRenderTarget ?: return

        renderer.setRenderTarget(target)
        renderer.clear(true, true, false)
        renderer.render(scene, camera)
    }

    private fun renderNormals(renderer: Renderer) {
        val target = normalRenderTarget ?: return
        val mat = normalMaterial ?: return

        renderer.setRenderTarget(target)
        renderer.clear(true, true, false)

        val originalOverride = scene.overrideMaterial
        scene.overrideMaterial = mat
        renderer.render(scene, camera)
        scene.overrideMaterial = originalOverride
    }

    private fun renderSAO(renderer: Renderer) {
        val target = saoRenderTarget ?: return
        val mat = saoMaterial ?: return

        mat.setUniform("tDepth", depthRenderTarget?.texture)
        mat.setUniform("tNormal", normalRenderTarget?.texture)
        mat.setUniform("size", floatArrayOf(width.toFloat(), height.toFloat()))
        mat.setUniform("cameraNear", camera.near)
        mat.setUniform("cameraFar", camera.far)
        mat.setUniform("cameraProjectionMatrix", camera.projectionMatrix)
        mat.setUniform("cameraInverseProjectionMatrix", camera.projectionMatrixInverse)
        mat.setUniform("scale", saoScale)
        mat.setUniform("intensity", saoIntensity)
        mat.setUniform("bias", saoBias)
        mat.setUniform("kernelRadius", saoKernelRadius)
        mat.setUniform("minResolution", saoMinResolution)

        renderer.setRenderTarget(target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun renderBlur(renderer: Renderer) {
        val blurMat = blurMaterial ?: return
        val target = blurIntermediateRenderTarget ?: return

        // Horizontal blur pass
        blurMat.setUniform("tDiffuse", saoRenderTarget?.texture)
        blurMat.setUniform("tDepth", depthRenderTarget?.texture)
        blurMat.setUniform("size", floatArrayOf(width.toFloat(), height.toFloat()))
        blurMat.setUniform("direction", floatArrayOf(1f, 0f)) // Horizontal
        blurMat.setUniform("blurRadius", saoBlurRadius)
        blurMat.setUniform("blurStdDev", saoBlurStdDev)
        blurMat.setUniform("blurDepthCutoff", saoBlurDepthCutoff)

        renderer.setRenderTarget(target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, blurMat)

        // Vertical blur pass back to SAO target
        blurMat.setUniform("tDiffuse", target.texture)
        blurMat.setUniform("direction", floatArrayOf(0f, 1f)) // Vertical

        renderer.setRenderTarget(saoRenderTarget)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, blurMat)

        // Copy final blurred result
        copyMaterial?.setUniform("tDiffuse", saoRenderTarget?.texture)
        renderer.setRenderTarget(target)
        fsQuad.render(renderer, copyMaterial!!)
    }

    private fun renderDepthVisualization(renderer: Renderer, target: RenderTarget) {
        val mat = depthCopyMaterial ?: return

        mat.setUniform("tDepth", depthRenderTarget?.texture)
        mat.setUniform("cameraNear", camera.near)
        mat.setUniform("cameraFar", camera.far)

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun copyToOutput(renderer: Renderer, source: RenderTarget, target: RenderTarget) {
        val mat = copyMaterial ?: return

        mat.setUniform("tDiffuse", source.texture)

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun compositeSAO(renderer: Renderer, colorBuffer: RenderTarget, target: RenderTarget) {
        val mat = copyMaterial ?: return
        val saoSource = if (saoBlur) blurIntermediateRenderTarget else saoRenderTarget

        mat.setUniform("tDiffuse", colorBuffer.texture)
        mat.setUniform("tSAO", saoSource?.texture)
        mat.setUniform("mode", 1) // Multiply blend

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun initMaterials() {
        saoMaterial = ShaderMaterial(
            vertexShader = SAO_VERTEX_SHADER,
            fragmentShader = SAO_FRAGMENT_SHADER
        )

        normalMaterial = ShaderMaterial(
            vertexShader = NORMAL_VERTEX_SHADER,
            fragmentShader = NORMAL_FRAGMENT_SHADER
        )

        blurMaterial = ShaderMaterial(
            vertexShader = BLUR_VERTEX_SHADER,
            fragmentShader = BLUR_FRAGMENT_SHADER
        )

        copyMaterial = ShaderMaterial(
            vertexShader = COPY_VERTEX_SHADER,
            fragmentShader = COPY_FRAGMENT_SHADER
        )

        depthCopyMaterial = ShaderMaterial(
            vertexShader = COPY_VERTEX_SHADER,
            fragmentShader = DEPTH_COPY_FRAGMENT_SHADER
        )
    }

    private fun initRenderTargets() {
        val rtConfig = RenderTargetOptions(
            minFilter = TextureFilter.Linear,
            magFilter = TextureFilter.Linear,
            format = TextureFormat.RGBA8,
            stencilBuffer = false
        )

        depthRenderTarget = WebGPURenderTarget(width, height, rtConfig.copy(depthBuffer = true))
        normalRenderTarget = WebGPURenderTarget(width, height, rtConfig)
        saoRenderTarget = WebGPURenderTarget(width, height, rtConfig)
        blurIntermediateRenderTarget = WebGPURenderTarget(width, height, rtConfig)
    }

    override fun dispose() {
        depthRenderTarget?.dispose()
        normalRenderTarget?.dispose()
        saoRenderTarget?.dispose()
        blurIntermediateRenderTarget?.dispose()

        saoMaterial?.dispose()
        normalMaterial?.dispose()
        blurMaterial?.dispose()
        copyMaterial?.dispose()
        depthCopyMaterial?.dispose()

        fsQuad.dispose()

        super.dispose()
    }

    companion object {
        private const val SAO_VERTEX_SHADER = """
struct VertexInput {
    @location(0) position: vec3f,
    @location(1) uv: vec2f,
}

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) vUv: vec2f,
}

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;
    output.vUv = input.uv;
    output.position = vec4f(input.position, 1.0);
    return output;
}
"""

        private const val SAO_FRAGMENT_SHADER = """
@group(0) @binding(0) var tDepth: texture_2d<f32>;
@group(0) @binding(1) var tNormal: texture_2d<f32>;
@group(0) @binding(2) var texSampler: sampler;

struct Uniforms {
    size: vec2f,
    cameraNear: f32,
    cameraFar: f32,
    cameraProjectionMatrix: mat4x4f,
    cameraInverseProjectionMatrix: mat4x4f,
    scale: f32,
    intensity: f32,
    bias: f32,
    kernelRadius: i32,
    minResolution: f32,
}

@group(0) @binding(3) var<uniform> uniforms: Uniforms;

const PI: f32 = 3.14159265359;
const NUM_RINGS: i32 = 4;

fn hash(p: vec2f) -> f32 {
    var p3 = fract(vec3f(p.xyx) * 0.1031);
    p3 = p3 + vec3f(dot(p3, p3.yzx + 33.33));
    return fract((p3.x + p3.y) * p3.z);
}

fn getDepth(uv: vec2f) -> f32 {
    return textureSample(tDepth, texSampler, uv).x;
}

fn getLinearDepth(uv: vec2f) -> f32 {
    let depth = getDepth(uv);
    let z = depth * 2.0 - 1.0;
    return (2.0 * uniforms.cameraNear * uniforms.cameraFar) / 
           (uniforms.cameraFar + uniforms.cameraNear - z * (uniforms.cameraFar - uniforms.cameraNear));
}

fn getViewPosition(uv: vec2f, depth: f32) -> vec3f {
    let clipPos = vec4f(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    let viewPos = uniforms.cameraInverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

fn getViewNormal(uv: vec2f) -> vec3f {
    return textureSample(tNormal, texSampler, uv).xyz * 2.0 - 1.0;
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    let depth = getDepth(vUv);
    
    if (depth >= 1.0) {
        return vec4f(1.0);
    }
    
    let viewPosition = getViewPosition(vUv, depth);
    let viewNormal = getViewNormal(vUv);
    
    let radius = uniforms.scale / -viewPosition.z;
    
    var occlusion = 0.0;
    var samples = 0.0;
    
    let kernelRadius = uniforms.kernelRadius;
    
    for (var i = 0; i < kernelRadius; i = i + 1) {
        for (var j = 0; j < NUM_RINGS; j = j + 1) {
            let ringProgress = f32(j + 1) / f32(NUM_RINGS);
            let sampleRadius = radius * ringProgress;
            
            let angle = f32(i) * (PI * 2.0 / f32(kernelRadius)) + hash(vUv + vec2f(f32(i), f32(j)));
            let offset = vec2f(cos(angle), sin(angle)) * sampleRadius / uniforms.size;
            
            let sampleUV = vUv + offset;
            if (sampleUV.x < 0.0 || sampleUV.x > 1.0 || sampleUV.y < 0.0 || sampleUV.y > 1.0) {
                continue;
            }
            
            let sampleDepth = getDepth(sampleUV);
            let samplePosition = getViewPosition(sampleUV, sampleDepth);
            
            let delta = samplePosition - viewPosition;
            let dist = length(delta);
            let direction = delta / dist;
            
            // Calculate occlusion contribution
            let normalDot = max(dot(viewNormal, direction) - uniforms.bias, 0.0);
            let distFalloff = 1.0 / (1.0 + dist * dist);
            
            occlusion = occlusion + normalDot * distFalloff;
            samples = samples + 1.0;
        }
    }
    
    if (samples > 0.0) {
        occlusion = occlusion / samples;
    }
    
    occlusion = clamp(1.0 - occlusion * uniforms.intensity, 0.0, 1.0);
    
    return vec4f(vec3f(occlusion), 1.0);
}
"""

        private const val NORMAL_VERTEX_SHADER = """
struct VertexInput {
    @location(0) position: vec3f,
    @location(1) normal: vec3f,
}

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) vNormal: vec3f,
}

struct Uniforms {
    modelViewMatrix: mat4x4f,
    projectionMatrix: mat4x4f,
    normalMatrix: mat3x3f,
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;
    output.vNormal = normalize(uniforms.normalMatrix * input.normal);
    output.position = uniforms.projectionMatrix * uniforms.modelViewMatrix * vec4f(input.position, 1.0);
    return output;
}
"""

        private const val NORMAL_FRAGMENT_SHADER = """
@fragment
fn main(@location(0) vNormal: vec3f) -> @location(0) vec4f {
    return vec4f(normalize(vNormal) * 0.5 + 0.5, 1.0);
}
"""

        private const val BLUR_VERTEX_SHADER = """
struct VertexInput {
    @location(0) position: vec3f,
    @location(1) uv: vec2f,
}

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) vUv: vec2f,
}

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;
    output.vUv = input.uv;
    output.position = vec4f(input.position, 1.0);
    return output;
}
"""

        private const val BLUR_FRAGMENT_SHADER = """
@group(0) @binding(0) var tDiffuse: texture_2d<f32>;
@group(0) @binding(1) var tDepth: texture_2d<f32>;
@group(0) @binding(2) var texSampler: sampler;

struct Uniforms {
    size: vec2f,
    direction: vec2f,
    blurRadius: i32,
    blurStdDev: f32,
    blurDepthCutoff: f32,
}

@group(0) @binding(3) var<uniform> uniforms: Uniforms;

fn gaussian(x: f32, sigma: f32) -> f32 {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    let texelSize = 1.0 / uniforms.size;
    let centerDepth = textureSample(tDepth, texSampler, vUv).x;
    
    var result = vec4f(0.0);
    var weightSum = 0.0;
    
    let radius = uniforms.blurRadius;
    
    for (var i = -radius; i <= radius; i = i + 1) {
        let offset = uniforms.direction * f32(i) * texelSize;
        let sampleUV = vUv + offset;
        
        let sampleDepth = textureSample(tDepth, texSampler, sampleUV).x;
        let depthDiff = abs(centerDepth - sampleDepth);
        
        // Bilateral weight based on spatial distance and depth difference
        let spatialWeight = gaussian(f32(i), uniforms.blurStdDev);
        let depthWeight = step(depthDiff, uniforms.blurDepthCutoff);
        let weight = spatialWeight * depthWeight;
        
        result = result + textureSample(tDiffuse, texSampler, sampleUV) * weight;
        weightSum = weightSum + weight;
    }
    
    if (weightSum > 0.0) {
        result = result / weightSum;
    }
    
    return result;
}
"""

        private const val COPY_VERTEX_SHADER = """
struct VertexInput {
    @location(0) position: vec3f,
    @location(1) uv: vec2f,
}

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) vUv: vec2f,
}

@vertex
fn main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;
    output.vUv = input.uv;
    output.position = vec4f(input.position, 1.0);
    return output;
}
"""

        private const val COPY_FRAGMENT_SHADER = """
@group(0) @binding(0) var tDiffuse: texture_2d<f32>;
@group(0) @binding(1) var tSAO: texture_2d<f32>;
@group(0) @binding(2) var texSampler: sampler;

struct Uniforms {
    mode: i32,
}

@group(0) @binding(3) var<uniform> uniforms: Uniforms;

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    let color = textureSample(tDiffuse, texSampler, vUv);
    
    if (uniforms.mode == 1) {
        // Multiply with SAO
        let ao = textureSample(tSAO, texSampler, vUv).r;
        return vec4f(color.rgb * ao, color.a);
    }
    
    return color;
}
"""

        private const val DEPTH_COPY_FRAGMENT_SHADER = """
@group(0) @binding(0) var tDepth: texture_2d<f32>;
@group(0) @binding(1) var texSampler: sampler;

struct Uniforms {
    cameraNear: f32,
    cameraFar: f32,
}

@group(0) @binding(2) var<uniform> uniforms: Uniforms;

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    let depth = textureSample(tDepth, texSampler, vUv).x;
    let z = depth * 2.0 - 1.0;
    let linearDepth = (2.0 * uniforms.cameraNear * uniforms.cameraFar) / 
                      (uniforms.cameraFar + uniforms.cameraNear - z * (uniforms.cameraFar - uniforms.cameraNear));
    let normalizedDepth = linearDepth / uniforms.cameraFar;
    
    return vec4f(vec3f(normalizedDepth), 1.0);
}
"""
    }
}
