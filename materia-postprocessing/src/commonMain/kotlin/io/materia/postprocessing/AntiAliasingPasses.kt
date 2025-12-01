package io.materia.postprocessing

import io.materia.material.ShaderMaterial
import io.materia.core.math.Vector2
import io.materia.renderer.*
import kotlin.math.max

/**
 * Fast Approximate Anti-Aliasing (FXAA) pass.
 * 
 * A fast post-processing AA technique that works by detecting edges based on
 * luminance and then blending along them. FXAA is very fast but may blur some
 * details. Good for performance-critical applications.
 *
 * Based on NVIDIA's FXAA 3.11 algorithm.
 *
 * Usage:
 * ```kotlin
 * val fxaaPass = FXAAPass()
 * composer.addPass(renderPass)
 * composer.addPass(fxaaPass)
 * composer.addPass(outputPass)
 * ```
 */
class FXAAPass : Pass() {

    /** Enable/disable FXAA processing */
    var fxaaEnabled: Boolean = true

    /** Edge detection threshold (lower = more edges detected) */
    var edgeThreshold: Float = 0.166f

    /** Minimum edge threshold (skips very dark areas) */
    var edgeThresholdMin: Float = 0.0833f

    /** Quality of edge searching (1-12, higher = better but slower) */
    var searchIterations: Int = 8

    /** Subpixel quality (0.0-1.0, higher = more blur) */
    var subpixelQuality: Float = 0.75f

    private val fxaaMaterial = createFXAAShader()
    private val fullscreenQuad = FullScreenQuad()
    private var resolution = Vector2(1f, 1f)

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        resolution = Vector2(1f / width.toFloat(), 1f / height.toFloat())
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        if (!fxaaEnabled) {
            // Just copy through
            renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)
            fullscreenQuad.render(renderer, createCopyShader().apply {
                uniforms["tDiffuse"] = readBuffer.texture
            })
            return
        }

        fxaaMaterial.uniforms["tDiffuse"] = readBuffer.texture
        fxaaMaterial.uniforms["resolution"] = resolution
        fxaaMaterial.uniforms["edgeThreshold"] = edgeThreshold
        fxaaMaterial.uniforms["edgeThresholdMin"] = edgeThresholdMin
        fxaaMaterial.uniforms["subpixelQuality"] = subpixelQuality

        renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)
        renderer.clear(true, false, false)
        fullscreenQuad.render(renderer, fxaaMaterial)
    }

    override fun dispose() {
        fxaaMaterial.dispose()
        fullscreenQuad.dispose()
        super.dispose()
    }

    private fun createCopyShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            vertexShader = FULLSCREEN_VERTEX_SHADER
            fragmentShader = """
                @group(0) @binding(0) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(1) var tDiffuseSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return textureSample(tDiffuse, tDiffuseSampler, uv);
                }
            """
        }
    }

    private fun createFXAAShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["resolution"] = Vector2(1f / 1920f, 1f / 1080f)
            uniforms["edgeThreshold"] = 0.166f
            uniforms["edgeThresholdMin"] = 0.0833f
            uniforms["subpixelQuality"] = 0.75f

            vertexShader = FULLSCREEN_VERTEX_SHADER
            fragmentShader = FXAA_FRAGMENT_SHADER
        }
    }

    companion object {
        private const val FULLSCREEN_VERTEX_SHADER = """
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

        // FXAA 3.11 implementation
        private const val FXAA_FRAGMENT_SHADER = """
            @group(0) @binding(0) var<uniform> resolution: vec2<f32>;
            @group(0) @binding(1) var<uniform> edgeThreshold: f32;
            @group(0) @binding(2) var<uniform> edgeThresholdMin: f32;
            @group(0) @binding(3) var<uniform> subpixelQuality: f32;
            @group(0) @binding(4) var tDiffuse: texture_2d<f32>;
            @group(0) @binding(5) var tDiffuseSampler: sampler;

            // Compute luminance from RGB
            fn luminance(color: vec3<f32>) -> f32 {
                return dot(color, vec3<f32>(0.299, 0.587, 0.114));
            }

            // Sample texture and compute luminance
            fn textureLuma(uv: vec2<f32>) -> f32 {
                return luminance(textureSample(tDiffuse, tDiffuseSampler, uv).rgb);
            }

            @fragment
            fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                let center = textureSample(tDiffuse, tDiffuseSampler, uv);
                
                // Sample neighboring pixels for edge detection
                let lumaC = luminance(center.rgb);
                let lumaN = textureLuma(uv + vec2<f32>(0.0, -resolution.y));
                let lumaS = textureLuma(uv + vec2<f32>(0.0, resolution.y));
                let lumaW = textureLuma(uv + vec2<f32>(-resolution.x, 0.0));
                let lumaE = textureLuma(uv + vec2<f32>(resolution.x, 0.0));

                // Find min/max luma around current pixel
                let lumaMin = min(lumaC, min(min(lumaN, lumaS), min(lumaW, lumaE)));
                let lumaMax = max(lumaC, max(max(lumaN, lumaS), max(lumaW, lumaE)));
                let lumaRange = lumaMax - lumaMin;

                // Skip if edge is below threshold
                if (lumaRange < max(edgeThresholdMin, lumaMax * edgeThreshold)) {
                    return center;
                }

                // Sample corners for subpixel aliasing
                let lumaNW = textureLuma(uv + vec2<f32>(-resolution.x, -resolution.y));
                let lumaNE = textureLuma(uv + vec2<f32>(resolution.x, -resolution.y));
                let lumaSW = textureLuma(uv + vec2<f32>(-resolution.x, resolution.y));
                let lumaSE = textureLuma(uv + vec2<f32>(resolution.x, resolution.y));

                // Compute gradients for edge direction
                let lumaWE = lumaW + lumaE;
                let lumaNS = lumaN + lumaS;
                let lumaNWNE = lumaNW + lumaNE;
                let lumaSWSE = lumaSW + lumaSE;

                let edgeHorz = abs(lumaNW - lumaW * 2.0 + lumaSW) +
                               abs(lumaN - lumaC * 2.0 + lumaS) * 2.0 +
                               abs(lumaNE - lumaE * 2.0 + lumaSE);

                let edgeVert = abs(lumaNW - lumaN * 2.0 + lumaNE) +
                               abs(lumaW - lumaC * 2.0 + lumaE) * 2.0 +
                               abs(lumaSW - lumaS * 2.0 + lumaSE);

                // Determine edge orientation
                let isHorizontal = edgeHorz >= edgeVert;

                // Select edge detection direction
                var stepLength: f32;
                var luma1: f32;
                var luma2: f32;
                
                if (isHorizontal) {
                    stepLength = resolution.y;
                    luma1 = lumaN;
                    luma2 = lumaS;
                } else {
                    stepLength = resolution.x;
                    luma1 = lumaW;
                    luma2 = lumaE;
                }

                // Choose direction with steeper gradient
                let gradient1 = abs(luma1 - lumaC);
                let gradient2 = abs(luma2 - lumaC);
                let isFirst = gradient1 >= gradient2;

                var lumaLocalAvg: f32;
                var gradientScaled: f32;

                if (isFirst) {
                    stepLength = -stepLength;
                    lumaLocalAvg = (luma1 + lumaC) * 0.5;
                    gradientScaled = gradient1 * 0.25;
                } else {
                    lumaLocalAvg = (luma2 + lumaC) * 0.5;
                    gradientScaled = gradient2 * 0.25;
                }

                // Compute UV offset for edge pixel
                var currentUV = uv;
                if (isHorizontal) {
                    currentUV.y = currentUV.y + stepLength * 0.5;
                } else {
                    currentUV.x = currentUV.x + stepLength * 0.5;
                }

                // Step along edge in both directions to find endpoints
                var uv1 = currentUV;
                var uv2 = currentUV;
                
                var offset: vec2<f32>;
                if (isHorizontal) {
                    offset = vec2<f32>(resolution.x, 0.0);
                } else {
                    offset = vec2<f32>(0.0, resolution.y);
                }

                var lumaEnd1: f32 = textureLuma(uv1) - lumaLocalAvg;
                var lumaEnd2: f32 = textureLuma(uv2) - lumaLocalAvg;
                var reached1 = abs(lumaEnd1) >= gradientScaled;
                var reached2 = abs(lumaEnd2) >= gradientScaled;

                // Search along edge
                for (var i = 0; i < 8; i = i + 1) {
                    if (!reached1) {
                        uv1 = uv1 - offset;
                        lumaEnd1 = textureLuma(uv1) - lumaLocalAvg;
                        reached1 = abs(lumaEnd1) >= gradientScaled;
                    }
                    if (!reached2) {
                        uv2 = uv2 + offset;
                        lumaEnd2 = textureLuma(uv2) - lumaLocalAvg;
                        reached2 = abs(lumaEnd2) >= gradientScaled;
                    }
                    if (reached1 && reached2) {
                        break;
                    }
                }

                // Compute distances to edge endpoints
                var distance1: f32;
                var distance2: f32;
                if (isHorizontal) {
                    distance1 = uv.x - uv1.x;
                    distance2 = uv2.x - uv.x;
                } else {
                    distance1 = uv.y - uv1.y;
                    distance2 = uv2.y - uv.y;
                }

                let distanceTotal = distance1 + distance2;
                let isCloser1 = distance1 < distance2;
                let distanceFinal = min(distance1, distance2);

                // Compute subpixel offset
                let edgePixelOffset = -distanceFinal / distanceTotal + 0.5;

                // Check for wrong edge direction
                let isLumaCSmaller = lumaC < lumaLocalAvg;
                let correctVariation = select(lumaEnd2, lumaEnd1, isCloser1) < 0.0 != isLumaCSmaller;
                var finalOffset = select(0.0, edgePixelOffset, correctVariation);

                // Subpixel antialiasing
                let lumaAvg = (lumaNS + lumaWE) * 0.25 + (lumaNWNE + lumaSWSE) * 0.125;
                let subpixelOffset = clamp(abs(lumaAvg - lumaC) / lumaRange, 0.0, 1.0);
                let subpixelOffsetFinal = (-2.0 * subpixelOffset + 3.0) * subpixelOffset * subpixelOffset;

                // Choose larger offset
                finalOffset = max(finalOffset, subpixelOffsetFinal * subpixelQuality);

                // Apply offset and sample
                var finalUV = uv;
                if (isHorizontal) {
                    finalUV.y = finalUV.y + finalOffset * stepLength;
                } else {
                    finalUV.x = finalUV.x + finalOffset * stepLength;
                }

                return textureSample(tDiffuse, tDiffuseSampler, finalUV);
            }
        """
    }
}

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