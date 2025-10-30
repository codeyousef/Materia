package io.materia.postprocessing

import io.materia.material.ShaderMaterial
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.renderer.*
import kotlin.math.min
import kotlin.math.max

/**
 * BloomPass creates a glow effect for bright areas in the scene.
 * Implements a multi-pass Gaussian blur with threshold extraction.
 *
 * @property strength The intensity of the bloom effect (0.0 - 3.0, default: 1.5)
 * @property radius The blur radius in screen space (0.0 - 1.0, default: 0.5)
 * @property threshold The luminance threshold for bloom (0.0 - 1.0, default: 0.8)
 */
class BloomPass(
    var strength: Float = 1.5f,
    var radius: Float = 0.5f,
    var threshold: Float = 0.8f
) : Pass() {

    // Render targets for multi-pass processing
    private var renderTargetBright: WebGPURenderTarget? = null
    private var renderTargetHorizontal: WebGPURenderTarget? = null
    private var renderTargetVertical: WebGPURenderTarget? = null

    // Shader passes
    private val brightnessPass: ShaderPass
    private val horizontalBlurPass: ShaderPass
    private val verticalBlurPass: ShaderPass
    private val compositePass: ShaderPass

    // Blur kernel size
    private val kernelSize = 9

    init {
        // Create brightness extraction pass
        brightnessPass = ShaderPass(createBrightnessShader())

        // Create horizontal blur pass
        horizontalBlurPass = ShaderPass(createBlurShader(true))

        // Create vertical blur pass
        verticalBlurPass = ShaderPass(createBlurShader(false))

        // Create composite pass
        compositePass = ShaderPass(createCompositeShader())

        // This pass needs buffer swapping
        needsSwap = true
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        // Dispose old render targets
        renderTargetBright?.dispose()
        renderTargetHorizontal?.dispose()
        renderTargetVertical?.dispose()

        // Create new render targets at half resolution for performance
        val reductionFactor = 2
        val reducedWidth = max(1, width / reductionFactor)
        val reducedHeight = max(1, height / reductionFactor)

        val renderTargetOptions = RenderTargetOptions(
            minFilter = TextureFilter.Linear,
            magFilter = TextureFilter.Linear,
            format = TextureFormat.RGBA8,
            type = TextureDataType.UnsignedByte,
            depthBuffer = false,
            stencilBuffer = false,
            generateMipmaps = false
        )

        renderTargetBright = WebGPURenderTarget(reducedWidth, reducedHeight, renderTargetOptions)
        renderTargetHorizontal =
            WebGPURenderTarget(reducedWidth, reducedHeight, renderTargetOptions)
        renderTargetVertical = WebGPURenderTarget(reducedWidth, reducedHeight, renderTargetOptions)

        // Update blur shader uniforms
        updateBlurUniforms()

        // Update passes
        brightnessPass.setSize(reducedWidth, reducedHeight)
        horizontalBlurPass.setSize(reducedWidth, reducedHeight)
        verticalBlurPass.setSize(reducedWidth, reducedHeight)
        compositePass.setSize(width, height)
    }

    private fun updateBlurUniforms() {
        val reducedWidth = renderTargetHorizontal?.width ?: 1
        val reducedHeight = renderTargetHorizontal?.height ?: 1

        // Update horizontal blur uniforms
        horizontalBlurPass.uniforms["h"] = FloatArray(kernelSize).apply {
            for (i in indices) {
                this[i] = radius / reducedWidth.toFloat()
            }
        }

        // Update vertical blur uniforms
        verticalBlurPass.uniforms["v"] = FloatArray(kernelSize).apply {
            for (i in indices) {
                this[i] = radius / reducedHeight.toFloat()
            }
        }
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
            println("Warning: BloomPass not initialized, call setSize() first")
            return
        }
        val horizontalTarget = renderTargetHorizontal ?: return
        val verticalTarget = renderTargetVertical ?: return

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

        // 2. Apply horizontal blur
        horizontalBlurPass.uniforms["tDiffuse"] = brightTarget.texture
        horizontalBlurPass.render(
            renderer,
            horizontalTarget,
            brightTarget,
            deltaTime,
            maskActive
        )

        // 3. Apply vertical blur
        verticalBlurPass.uniforms["tDiffuse"] = horizontalTarget.texture
        verticalBlurPass.render(
            renderer,
            verticalTarget,
            horizontalTarget,
            deltaTime,
            maskActive
        )

        // 4. Composite bloom with original image
        compositePass.uniforms["tDiffuse"] = readBuffer.texture
        compositePass.uniforms["tBloom"] = verticalTarget.texture
        compositePass.uniforms["strength"] = strength
        compositePass.renderToScreen = renderToScreen
        compositePass.render(
            renderer,
            if (renderToScreen) writeBuffer else writeBuffer,
            readBuffer,
            deltaTime,
            maskActive
        )
    }

    override fun dispose() {
        renderTargetBright?.dispose()
        renderTargetHorizontal?.dispose()
        renderTargetVertical?.dispose()
        brightnessPass.dispose()
        horizontalBlurPass.dispose()
        verticalBlurPass.dispose()
        compositePass.dispose()
        super.dispose()
    }

    /**
     * Creates the brightness extraction shader.
     */
    private fun createBrightnessShader(): ShaderMaterial {
        return ShaderMaterial().apply {
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

                    if (luma > threshold) {
                        return texel;
                    } else {
                        return vec4<f32>(0.0, 0.0, 0.0, texel.a);
                    }
                }
            """

            uniforms["threshold"] = 0.8f
            uniforms["tDiffuse"] = null
        }
    }

    /**
     * Creates a Gaussian blur shader.
     * @param horizontal true for horizontal blur, false for vertical
     */
    private fun createBlurShader(horizontal: Boolean): ShaderMaterial {
        val direction = if (horizontal) "h" else "v"
        val coordOffset = if (horizontal) "vec2<f32>(offset, 0.0)" else "vec2<f32>(0.0, offset)"

        return ShaderMaterial().apply {
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
                struct Uniforms {
                    $direction: array<f32, $kernelSize>,
                }

                @group(0) @binding(0) var<uniform> uniforms: Uniforms;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;

                // Gaussian weights for 9-tap filter
                const weights: array<f32, 9> = array<f32, 9>(
                    0.051, 0.0918, 0.12245, 0.1531, 0.1633, 0.1531, 0.12245, 0.0918, 0.051
                );

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    var sum = vec4<f32>(0.0);

                    for (var i = 0; i < $kernelSize; i = i + 1) {
                        let offset = uniforms.$direction[i] * f32(i - 4);
                        let sampleCoord = uv + $coordOffset;
                        sum = sum + textureSample(tDiffuse, tDiffuseSampler, sampleCoord) * weights[i];
                    }

                    return sum;
                }
            """

            uniforms[direction] = FloatArray(kernelSize) { 0.0f }
            uniforms["tDiffuse"] = null
        }
    }

    /**
     * Creates the composite shader that combines bloom with the original image.
     */
    private fun createCompositeShader(): ShaderMaterial {
        return ShaderMaterial().apply {
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
                @group(0) @binding(0) var<uniform> strength: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;
                @group(0) @binding(3) var tBloom: texture_2d<f32>;
                @group(0) @binding(4) var tBloomSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let base = textureSample(tDiffuse, tDiffuseSampler, uv);
                    let bloom = textureSample(tBloom, tBloomSampler, uv);
                    return base + bloom * strength;
                }
            """

            uniforms["strength"] = 1.5f
            uniforms["tDiffuse"] = null
            uniforms["tBloom"] = null
        }
    }
}