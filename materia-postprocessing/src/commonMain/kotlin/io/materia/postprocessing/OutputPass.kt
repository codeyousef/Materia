package io.materia.postprocessing

import io.materia.material.ShaderMaterial
import io.materia.renderer.Renderer
import io.materia.renderer.RenderTarget

/**
 * OutputPass performs final color space conversion and tone mapping for display output.
 * This should typically be the last pass in the post-processing pipeline.
 *
 * @property toneMapping The tone mapping algorithm to use
 * @property toneMappingExposure Exposure adjustment for tone mapping
 * @property outputColorSpace The target color space for output
 */
class OutputPass(
    var toneMapping: ToneMapping = ToneMapping.ACESFilmic,
    var toneMappingExposure: Float = 1.0f,
    var outputColorSpace: ColorSpace = ColorSpace.SRGB
) : ShaderPass(createOutputShader()) {

    init {
        needsSwap = false // Output pass doesn't need buffer swap
        updateUniforms()
    }

    /**
     * Updates the shader uniforms based on current settings.
     */
    private fun updateUniforms() {
        uniforms["toneMapping"] = toneMapping.ordinal
        uniforms["toneMappingExposure"] = toneMappingExposure
        uniforms["outputColorSpace"] = outputColorSpace.ordinal
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        updateUniforms()
        super.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
    }

    companion object {
        /**
         * Creates the output shader with color space conversion and tone mapping.
         */
        private fun createOutputShader(): ShaderMaterial {
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
                        toneMapping: i32,
                        toneMappingExposure: f32,
                        outputColorSpace: i32,
                    }

                    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
                    @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                    @group(0) @binding(2) var tDiffuseSampler: sampler;

                    // Tone mapping algorithms
                    fn linearToneMapping(color: vec3<f32>, exposure: f32) -> vec3<f32> {
                        return saturate(color * exposure);
                    }

                    fn reinhardToneMapping(color: vec3<f32>, exposure: f32) -> vec3<f32> {
                        let c = color * exposure;
                        return saturate(c / (vec3<f32>(1.0) + c));
                    }

                    fn cineonToneMapping(color: vec3<f32>, exposure: f32) -> vec3<f32> {
                        // Optimized filmic operator by Jim Hejl and Richard Burgess-Dawson
                        let c = max(vec3<f32>(0.0), color * exposure - 0.004);
                        return pow((c * (6.2 * c + 0.5)) / (c * (6.2 * c + 1.7) + 0.06), vec3<f32>(2.2));
                    }

                    fn acesFilmicToneMapping(color: vec3<f32>, exposure: f32) -> vec3<f32> {
                        let c = color * exposure;
                        let a = 2.51;
                        let b = 0.03;
                        let c = 2.43;
                        let d = 0.59;
                        let e = 0.14;
                        return saturate((c * (a * c + b)) / (c * (c * c + d) + e));
                    }

                    // Color space conversions
                    fn linearToSRGB(color: vec3<f32>) -> vec3<f32> {
                        return pow(color, vec3<f32>(1.0 / 2.2));
                    }

                    fn linearToRec709(color: vec3<f32>) -> vec3<f32> {
                        // Rec. 709 uses same transfer function as sRGB
                        return linearToSRGB(color);
                    }

                    fn linearToDisplayP3(color: vec3<f32>) -> vec3<f32> {
                        // Display P3 uses sRGB transfer function with different primaries
                        // For simplicity, using sRGB transfer here
                        return linearToSRGB(color);
                    }

                    @fragment
                    fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                        var color = textureSample(tDiffuse, tDiffuseSampler, uv).rgb;

                        // Apply tone mapping
                        switch (uniforms.toneMapping) {
                            case 0: { // None
                                color = linearToneMapping(color, uniforms.toneMappingExposure);
                            }
                            case 1: { // Linear
                                color = linearToneMapping(color, uniforms.toneMappingExposure);
                            }
                            case 2: { // Reinhard
                                color = reinhardToneMapping(color, uniforms.toneMappingExposure);
                            }
                            case 3: { // Cineon
                                color = cineonToneMapping(color, uniforms.toneMappingExposure);
                            }
                            case 4: { // ACESFilmic
                                color = acesFilmicToneMapping(color, uniforms.toneMappingExposure);
                            }
                            default: {
                                color = linearToneMapping(color, uniforms.toneMappingExposure);
                            }
                        }

                        // Apply color space conversion
                        switch (uniforms.outputColorSpace) {
                            case 0: { // Linear
                                // No conversion needed
                            }
                            case 1: { // SRGB
                                color = linearToSRGB(color);
                            }
                            case 2: { // Rec709
                                color = linearToRec709(color);
                            }
                            case 3: { // DisplayP3
                                color = linearToDisplayP3(color);
                            }
                            default: {
                                color = linearToSRGB(color);
                            }
                        }

                        return vec4<f32>(color, 1.0);
                    }
                """

                uniforms["toneMapping"] = 0
                uniforms["toneMappingExposure"] = 1.0f
                uniforms["outputColorSpace"] = 0
                uniforms["tDiffuse"] = null
            }
        }
    }
}

/**
 * Tone mapping algorithms for HDR to LDR conversion.
 */
enum class ToneMapping {
    None,
    Linear,
    Reinhard,
    Cineon,
    ACESFilmic
}

/**
 * Color spaces for output.
 */
enum class ColorSpace {
    Linear,
    SRGB,
    Rec709,
    DisplayP3
}