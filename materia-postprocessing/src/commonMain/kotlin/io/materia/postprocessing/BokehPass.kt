package io.materia.postprocessing

import io.materia.camera.Camera
import io.materia.material.ShaderMaterial
import io.materia.core.math.Vector2
import io.materia.renderer.*
import io.materia.core.scene.Scene

/**
 * BokehPass creates depth-of-field effect with realistic bokeh blur.
 *
 * @property scene The scene to render
 * @property camera The camera for depth information
 * @property focusDistance Distance to the focus plane
 * @property aperture Size of the aperture (controls blur amount)
 * @property maxBlur Maximum blur amount in pixels
 */
class BokehPass(
    val scene: Scene,
    val camera: Camera,
    var focusDistance: Float = 10.0f,
    var aperture: Float = 0.025f,
    var maxBlur: Float = 0.01f
) : Pass() {

    private var depthRenderTarget: WebGPURenderTarget? = null
    private val depthPass = RenderPass(scene, camera)
    private val bokehShaderPass = ShaderPass(createBokehShader())

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        depthRenderTarget?.dispose()
        depthRenderTarget = WebGPURenderTarget(
            width, height,
            RenderTargetOptions(
                format = TextureFormat.RGBA16F,
                type = TextureDataType.HalfFloat,
                depthBuffer = true
            )
        )

        depthPass.setSize(width, height)
        bokehShaderPass.setSize(width, height)
        updateUniforms()
    }

    private fun updateUniforms() {
        bokehShaderPass.uniforms["focusDistance"] = focusDistance
        bokehShaderPass.uniforms["aperture"] = aperture
        bokehShaderPass.uniforms["maxBlur"] = maxBlur
        bokehShaderPass.uniforms["aspect"] = width.toFloat() / height.toFloat()
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Ensure render target is initialized
        val depthTarget = depthRenderTarget ?: run {
            println("Warning: BokehPass not initialized, call setSize() first")
            return
        }

        // 1. Render depth
        depthPass.render(renderer, depthTarget, readBuffer, deltaTime, maskActive)

        // 2. Apply bokeh blur based on depth
        bokehShaderPass.uniforms["tDiffuse"] = readBuffer.texture
        bokehShaderPass.uniforms["tDepth"] = depthTarget.texture
        updateUniforms()

        bokehShaderPass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
    }

    override fun dispose() {
        depthRenderTarget?.dispose()
        depthPass.dispose()
        bokehShaderPass.dispose()
        super.dispose()
    }

    private fun createBokehShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["tDepth"] = null
            uniforms["focusDistance"] = 10.0f
            uniforms["aperture"] = 0.025f
            uniforms["maxBlur"] = 0.01f
            uniforms["aspect"] = 1.0f

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
                    focusDistance: f32,
                    aperture: f32,
                    maxBlur: f32,
                    aspect: f32,
                }

                @group(0) @binding(0) var<uniform> uniforms: Uniforms;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;
                @group(0) @binding(3) var tDepth: texture_2d<f32>;
                @group(0) @binding(4) var tDepthSampler: sampler;

                const SAMPLES: i32 = 32;
                const RINGS: i32 = 4;

                fn getBlurSize(depth: f32, focusDistance: f32, aperture: f32) -> f32 {
                    let coc = aperture * abs(depth - focusDistance) / focusDistance;
                    return min(coc, uniforms.maxBlur);
                }

                fn hash(p: vec2<f32>) -> f32 {
                    let h = dot(p, vec2<f32>(127.1, 311.7));
                    return fract(sin(h) * 43758.5453123);
                }

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let depth = textureSample(tDepth, tDepthSampler, uv).r;
                    let blurSize = getBlurSize(depth, uniforms.focusDistance, uniforms.aperture);

                    var color = textureSample(tDiffuse, tDiffuseSampler, uv);

                    if (blurSize > 0.001) {
                        var sum = vec4<f32>(0.0);
                        var weightSum = 0.0;

                        // Bokeh kernel sampling
                        for (var i = 0; i < SAMPLES; i = i + 1) {
                            let ring = f32(i % RINGS) / f32(RINGS);
                            let angle = f32(i) * 3.14159 * 2.0 / f32(SAMPLES);

                            let offset = vec2<f32>(
                                cos(angle) * ring,
                                sin(angle) * ring * uniforms.aspect
                            ) * blurSize;

                            let sampleUV = uv + offset;
                            let sampleDepth = textureSample(tDepth, tDepthSampler, sampleUV).r;
                            let sampleBlur = getBlurSize(sampleDepth, uniforms.focusDistance, uniforms.aperture);

                            // Weight based on circle of confusion
                            let weight = 1.0 / (1.0 + abs(depth - sampleDepth));

                            sum = sum + textureSample(tDiffuse, tDiffuseSampler, sampleUV) * weight;
                            weightSum = weightSum + weight;
                        }

                        if (weightSum > 0.0) {
                            color = sum / weightSum;
                        }
                    }

                    return color;
                }
            """
        }
    }
}