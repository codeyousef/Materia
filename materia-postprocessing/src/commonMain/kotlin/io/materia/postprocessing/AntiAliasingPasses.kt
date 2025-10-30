package io.materia.postprocessing

import io.materia.material.ShaderMaterial
import io.materia.core.math.Vector2
import io.materia.renderer.*
import kotlin.math.max

/**
 * Temporal Anti-Aliasing pass - uses frame history for high quality antialiasing.
 */
class TAAPass : Pass() {

    var sampleLevel: Int = 0
    var jitterOffsets: Array<Vector2> = generateJitterOffsets()

    private var previousRenderTarget: WebGPURenderTarget? = null
    private val blendPass = ShaderPass(createTAAShader())

    private fun generateJitterOffsets(): Array<Vector2> {
        // Halton sequence for temporal jittering
        return arrayOf(
            Vector2(-0.4f, -0.3f),
            Vector2(0.3f, -0.4f),
            Vector2(-0.3f, 0.4f),
            Vector2(0.4f, 0.3f),
            Vector2(0.1f, -0.2f),
            Vector2(-0.2f, 0.1f),
            Vector2(0.2f, 0.4f),
            Vector2(-0.1f, -0.1f)
        )
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        previousRenderTarget?.dispose()
        previousRenderTarget = WebGPURenderTarget(
            width, height,
            RenderTargetOptions(
                format = TextureFormat.RGBA16F,
                type = TextureDataType.HalfFloat
            )
        )

        blendPass.setSize(width, height)
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Ensure render target is initialized
        val prevTarget = previousRenderTarget ?: run {
            println("Warning: TemporalAAPass not initialized, call setSize() first")
            return
        }

        // Apply jitter to camera
        val jitter = jitterOffsets[sampleLevel % jitterOffsets.size]

        // Blend current frame with history
        blendPass.uniforms["tDiffuse"] = readBuffer.texture
        blendPass.uniforms["tPrevious"] = prevTarget.texture
        blendPass.uniforms["mixRatio"] = 0.9f
        blendPass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)

        // Save current frame as history
        val copyPass = ShaderPass(CopyShader())
        copyPass.uniforms["tDiffuse"] = writeBuffer.texture
        copyPass.render(renderer, prevTarget, writeBuffer, deltaTime, maskActive)

        sampleLevel++
    }

    override fun dispose() {
        previousRenderTarget?.dispose()
        blendPass.dispose()
        super.dispose()
    }

    private fun createTAAShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["tPrevious"] = null
            uniforms["mixRatio"] = 0.9f

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
                @group(0) @binding(0) var<uniform> mixRatio: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;
                @group(0) @binding(3) var tPrevious: texture_2d<f32>;
                @group(0) @binding(4) var tPreviousSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let current = textureSample(tDiffuse, tDiffuseSampler, uv);
                    let previous = textureSample(tPrevious, tPreviousSampler, uv);

                    // Simple temporal blend - production would include motion vectors
                    return mix(current, previous, mixRatio);
                }
            """
        }
    }
}

/**
 * Super-Sampling Anti-Aliasing - renders at higher resolution and downsamples.
 */
class SSAAPass(
    var sampleLevel: Int = 4
) : Pass() {

    private var supersampledRenderTarget: WebGPURenderTarget? = null
    private val downsamplePass = ShaderPass(createDownsampleShader())

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        val ssWidth = width * sampleLevel
        val ssHeight = height * sampleLevel

        supersampledRenderTarget?.dispose()
        supersampledRenderTarget = WebGPURenderTarget(
            ssWidth, ssHeight,
            RenderTargetOptions(
                format = TextureFormat.RGBA16F,
                type = TextureDataType.HalfFloat,
                generateMipmaps = true
            )
        )

        downsamplePass.setSize(width, height)
        downsamplePass.uniforms["sampleLevel"] = sampleLevel.toFloat()
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Render would happen at higher resolution to supersampledRenderTarget
        // Then downsample to output resolution

        downsamplePass.uniforms["tDiffuse"] =
            readBuffer.texture // Would use supersampledRenderTarget
        downsamplePass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
    }

    override fun dispose() {
        supersampledRenderTarget?.dispose()
        downsamplePass.dispose()
        super.dispose()
    }

    private fun createDownsampleShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["sampleLevel"] = 4.0f

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
                @group(0) @binding(0) var<uniform> sampleLevel: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    // Box filter downsample
                    let texelSize = 1.0 / textureSize(tDiffuse, 0);
                    var result = vec4<f32>(0.0);
                    let samples = i32(sampleLevel);

                    for (var x = 0; x < samples; x = x + 1) {
                        for (var y = 0; y < samples; y = y + 1) {
                            let offset = vec2<f32>(f32(x), f32(y)) * texelSize;
                            result = result + textureSample(tDiffuse, tDiffuseSampler, uv + offset);
                        }
                    }

                    return result / (sampleLevel * sampleLevel);
                }
            """
        }
    }
}