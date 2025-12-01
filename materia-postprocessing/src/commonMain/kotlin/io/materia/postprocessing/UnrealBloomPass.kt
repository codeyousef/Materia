package io.materia.postprocessing

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.material.ShaderMaterial
import io.materia.renderer.*
import kotlin.math.*

/**
 * UnrealBloomPass implements high-quality bloom with multiple resolution levels.
 * Based on Unreal Engine's bloom implementation.
 *
 * @property resolution Resolution of the bloom texture (default: 256)
 * @property strength Overall bloom intensity (default: 1.5)
 * @property radius Bloom radius (default: 0.5)
 * @property threshold Luminance threshold for bloom (default: 0.8)
 */
class UnrealBloomPass(
    var resolution: Int = 256,
    var strength: Float = 1.5f,
    var radius: Float = 0.5f,
    var threshold: Float = 0.8f
) : Pass() {

    companion object {
        private const val MAX_LEVELS = 8
    }

    // Multi-resolution render targets
    private val renderTargetsHorizontal = mutableListOf<WebGPURenderTarget>()
    private val renderTargetsVertical = mutableListOf<WebGPURenderTarget>()
    private var renderTargetBright: WebGPURenderTarget? = null

    // Shader passes for each level
    private val blurPassesH = mutableListOf<ShaderPass>()
    private val blurPassesV = mutableListOf<ShaderPass>()

    // Other passes
    private val brightnessPass: ShaderPass
    private val compositePass: ShaderPass

    // Blur weights for multi-scale composition
    private val bloomWeights = floatArrayOf(
        0.0625f, 0.125f, 0.25f, 0.5f, 1.0f, 1.0f, 1.0f, 1.0f
    )

    init {
        brightnessPass = ShaderPass(createBrightnessShader())
        compositePass = ShaderPass(createCompositeShader())
        needsSwap = true
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        // Clean up old resources
        dispose()

        // Calculate number of mip levels based on resolution
        val levels = min(MAX_LEVELS, ceil(log2(max(width, height).toFloat())).toInt())

        // Create render targets and blur passes for each level
        var resx = resolution
        var resy = resolution

        for (i in 0 until levels) {
            val renderTargetHorizonal = WebGPURenderTarget(
                resx, resy,
                RenderTargetOptions(
                    minFilter = TextureFilter.Linear,
                    magFilter = TextureFilter.Linear,
                    format = TextureFormat.RGBA16F,
                    type = TextureDataType.HalfFloat
                )
            )
            renderTargetsHorizontal.add(renderTargetHorizonal)

            val renderTargetVertical = WebGPURenderTarget(
                resx, resy,
                RenderTargetOptions(
                    minFilter = TextureFilter.Linear,
                    magFilter = TextureFilter.Linear,
                    format = TextureFormat.RGBA16F,
                    type = TextureDataType.HalfFloat
                )
            )
            renderTargetsVertical.add(renderTargetVertical)

            // Create blur passes
            val blurH = ShaderPass(createSeparableBlurShader(resx, true))
            blurH.setSize(resx, resy)
            blurPassesH.add(blurH)

            val blurV = ShaderPass(createSeparableBlurShader(resy, false))
            blurV.setSize(resx, resy)
            blurPassesV.add(blurV)

            // Reduce resolution for next level
            resx = max(1, resx / 2)
            resy = max(1, resy / 2)
        }

        // Create bright pass render target
        renderTargetBright = WebGPURenderTarget(
            resolution, resolution,
            RenderTargetOptions(
                minFilter = TextureFilter.Linear,
                magFilter = TextureFilter.Linear,
                format = TextureFormat.RGBA16F,
                type = TextureDataType.HalfFloat
            )
        )

        // Update passes
        brightnessPass.setSize(resolution, resolution)
        compositePass.setSize(width, height)
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Ensure render targets are initialized
        val brightTarget = renderTargetBright ?: run {
            println("Warning: UnrealBloomPass not initialized, call setSize() first")
            return
        }

        // 1. Extract bright pixels
        brightnessPass.uniforms["threshold"] = threshold
        brightnessPass.uniforms["tDiffuse"] = readBuffer.texture
        brightnessPass.render(
            renderer,
            brightTarget,
            readBuffer,
            deltaTime,
            maskActive
        )

        // 2. Progressive downsampling and blurring
        var inputTexture = brightTarget.texture

        for (i in renderTargetsHorizontal.indices) {
            // Horizontal blur
            blurPassesH[i].uniforms["tDiffuse"] = inputTexture
            blurPassesH[i].uniforms["radius"] = radius
            blurPassesH[i].render(
                renderer,
                renderTargetsHorizontal[i],
                renderTargetsHorizontal[i],
                deltaTime,
                maskActive
            )

            // Vertical blur
            blurPassesV[i].uniforms["tDiffuse"] = renderTargetsHorizontal[i].texture
            blurPassesV[i].uniforms["radius"] = radius
            blurPassesV[i].render(
                renderer,
                renderTargetsVertical[i],
                renderTargetsVertical[i],
                deltaTime,
                maskActive
            )

            inputTexture = renderTargetsVertical[i].texture
        }

        // 3. Progressive upsampling and compositing
        compositePass.uniforms["tDiffuse"] = readBuffer.texture
        compositePass.uniforms["strength"] = strength

        // Set bloom textures and weights
        for (i in renderTargetsVertical.indices) {
            compositePass.uniforms["tBloom$i"] = renderTargetsVertical[i].texture
            compositePass.uniforms["bloomWeight$i"] = bloomWeights.getOrElse(i) { 0f }
        }

        compositePass.renderToScreen = renderToScreen
        compositePass.render(
            renderer,
            writeBuffer,
            readBuffer,
            deltaTime,
            maskActive
        )
    }

    override fun dispose() {
        renderTargetsHorizontal.forEach { it.dispose() }
        renderTargetsVertical.forEach { it.dispose() }
        renderTargetsHorizontal.clear()
        renderTargetsVertical.clear()

        blurPassesH.forEach { it.dispose() }
        blurPassesV.forEach { it.dispose() }
        blurPassesH.clear()
        blurPassesV.clear()

        renderTargetBright?.dispose()
        brightnessPass.dispose()
        compositePass.dispose()

        super.dispose()
    }

    private fun createBrightnessShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["threshold"] = 0.8f
            uniforms["tDiffuse"] = null

            vertexShader = """
                struct VertexOutput {
                    @builtin(position) position: vec4<f32>,
                    @location(0) uv: vec2<f32>,
                }

                @vertex
                fn main(@location(0) position: vec3<f32>, @location(1) uv: vec2<f32>) -> VertexOutput {
                    var output: VertexOutput;
                    output.position = vec4<f32>(position, 1.0);
                    output.uv = uv;
                    return output;
                }
            """

            fragmentShader = """
                @group(0) @binding(0) var<uniform> threshold: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;

                fn luminance(color: vec3<f32>) -> f32 {
                    return dot(color, vec3<f32>(0.299, 0.587, 0.114));
                }

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let texel = textureSample(tDiffuse, tDiffuseSampler, uv);
                    let luma = luminance(texel.rgb);

                    // Soft threshold
                    let soft = smoothstep(threshold - 0.1, threshold + 0.1, luma);
                    return vec4<f32>(texel.rgb * soft, texel.a);
                }
            """
        }
    }

    private fun createSeparableBlurShader(resolution: Int, horizontal: Boolean): ShaderMaterial {
        val direction = if (horizontal) "vec2<f32>(1.0, 0.0)" else "vec2<f32>(0.0, 1.0)"

        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["radius"] = 0.5f
            uniforms["resolution"] = resolution.toFloat()

            vertexShader = """
                struct VertexOutput {
                    @builtin(position) position: vec4<f32>,
                    @location(0) uv: vec2<f32>,
                }

                @vertex
                fn main(@location(0) position: vec3<f32>, @location(1) uv: vec2<f32>) -> VertexOutput {
                    var output: VertexOutput;
                    output.position = vec4<f32>(position, 1.0);
                    output.uv = uv;
                    return output;
                }
            """

            fragmentShader = """
                @group(0) @binding(0) var<uniform> radius: f32;
                @group(0) @binding(1) var<uniform> resolution: f32;
                @group(0) @binding(2) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(3) var tDiffuseSampler: sampler;

                const weights: array<f32, 5> = array<f32, 5>(
                    0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216
                );

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let texelSize = 1.0 / resolution;
                    let direction = $direction;

                    var result = textureSample(tDiffuse, tDiffuseSampler, uv).rgb * weights[0];

                    for (var i = 1; i < 5; i = i + 1) {
                        let offset = direction * texelSize * f32(i) * radius;
                        result = result + textureSample(tDiffuse, tDiffuseSampler, uv + offset).rgb * weights[i];
                        result = result + textureSample(tDiffuse, tDiffuseSampler, uv - offset).rgb * weights[i];
                    }

                    return vec4<f32>(result, 1.0);
                }
            """
        }
    }

    private fun createCompositeShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["strength"] = 1.5f

            // Add bloom level textures and weights
            for (i in 0 until MAX_LEVELS) {
                uniforms["tBloom$i"] = null
                uniforms["bloomWeight$i"] = 0f
            }

            vertexShader = """
                struct VertexOutput {
                    @builtin(position) position: vec4<f32>,
                    @location(0) uv: vec2<f32>,
                }

                @vertex
                fn main(@location(0) position: vec3<f32>, @location(1) uv: vec2<f32>) -> VertexOutput {
                    var output: VertexOutput;
                    output.position = vec4<f32>(position, 1.0);
                    output.uv = uv;
                    return output;
                }
            """

            // Composite shader combining bloom levels with base image
            fragmentShader = """
                @group(0) @binding(0) var<uniform> strength: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;
                @group(0) @binding(3) var tBloom0: texture_2d<f32>;
                @group(0) @binding(4) var tBloom0Sampler: sampler;
                @group(0) @binding(5) var<uniform> bloomWeight0: f32;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let base = textureSample(tDiffuse, tDiffuseSampler, uv);

                    var bloom = vec3<f32>(0.0);
                    bloom = bloom + textureSample(tBloom0, tBloom0Sampler, uv).rgb * bloomWeight0;

                    return vec4<f32>(base.rgb + bloom * strength, base.a);
                }
            """
        }
    }
}