package io.materia.postprocessing

import kotlin.math.floor
import kotlin.random.Random

/**
 * Digital glitch post-processing pass.
 *
 * Creates a digital distortion effect with RGB channel separation,
 * scanline noise, and random screen displacement. Useful for
 * cyberpunk aesthetics, damage indicators, or transition effects.
 *
 * Based on Three.js GlitchPass implementation.
 *
 * Usage:
 * ```kotlin
 * val glitchPass = GlitchPass()
 * glitchPass.goWild = false // Enable for constant intense glitching
 * 
 * composer.addPass(renderPass)
 * composer.addPass(glitchPass)
 * composer.addPass(outputPass)
 * ```
 */
class GlitchPass(
    /** Digital glitch step size (higher = more intense) */
    var dtSize: Int = 64
) : Pass() {

    /**
     * When true, glitch effect runs continuously at max intensity.
     * When false, glitches occur randomly at intervals.
     */
    var goWild: Boolean = false

    /**
     * Probability of triggering a glitch per frame (0.0 to 1.0).
     * Only used when goWild is false.
     */
    var triggerProbability: Float = 0.05f

    /**
     * Intensity of the RGB channel separation (0.0 to 1.0).
     */
    var rgbShiftIntensity: Float = 0.02f

    /**
     * Intensity of the scanline effect (0.0 to 1.0).
     */
    var scanlineIntensity: Float = 0.5f

    /**
     * Seed for randomization. Change to get different glitch patterns.
     */
    var seed: Float = Random.nextFloat()

    // Internal state
    private var curF: Int = 0
    private var randX: Float = 0f
    private val fullScreenQuad = FullScreenQuad()
    private var material: ShaderMaterial? = null
    private var perturbationTexture: Texture? = null

    init {
        needsSwap = true
        generatePerturbationTexture()
        initMaterial()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        material?.setUniform("resolution", floatArrayOf(width.toFloat(), height.toFloat()))
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        val mat = material ?: return

        // Update glitch parameters
        updateGlitchState()

        // Set uniforms
        mat.setUniform("tDiffuse", readBuffer.texture)
        mat.setUniform("tDisp", perturbationTexture)
        mat.setUniform("seed", seed)
        mat.setUniform("byp", if (shouldBypass()) 1 else 0)
        mat.setUniform("amount", randX)
        mat.setUniform("angle", Random.nextFloat() * kotlin.math.PI.toFloat() * 2f)
        mat.setUniform("seed_x", seed + Random.nextFloat())
        mat.setUniform("seed_y", seed + Random.nextFloat())
        mat.setUniform("distortion_x", Random.nextFloat() * rgbShiftIntensity)
        mat.setUniform("distortion_y", Random.nextFloat() * rgbShiftIntensity)
        mat.setUniform("col_s", scanlineIntensity)

        renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)
        if (clear) renderer.clear(true, true, false)
        fullScreenQuad.render(renderer, mat)
    }

    private fun updateGlitchState() {
        if (goWild) {
            // Constant wild glitching
            randX = Random.nextFloat() * 0.1f
        } else {
            // Random glitch triggers
            if (Random.nextFloat() < triggerProbability) {
                randX = Random.nextFloat() * 0.05f
                curF = (Random.nextFloat() * 30f).toInt()
            } else if (curF > 0) {
                curF--
                randX = Random.nextFloat() * 0.02f
            } else {
                randX = 0f
            }
        }
    }

    private fun shouldBypass(): Boolean {
        return !goWild && curF == 0
    }

    private fun generatePerturbationTexture() {
        val size = dtSize
        val data = FloatArray(size * size * 4)

        for (i in 0 until size * size) {
            val x = Random.nextFloat()
            data[i * 4] = x
            data[i * 4 + 1] = x
            data[i * 4 + 2] = x
            data[i * 4 + 3] = 1f
        }

        perturbationTexture = DataTexture(
            data = null,
            width = size,
            height = size,
            format = TextureFormat.RGBA8,
            type = TextureDataType.UnsignedByte
        ).apply {
            minFilter = TextureFilter.Nearest
            magFilter = TextureFilter.Nearest
        }
    }

    private fun initMaterial() {
        material = ShaderMaterial(
            vertexShader = VERTEX_SHADER,
            fragmentShader = FRAGMENT_SHADER
        )
    }

    /**
     * Triggers an immediate glitch effect.
     * @param duration Number of frames the glitch should last
     * @param intensity Intensity of the glitch (0.0 to 1.0)
     */
    fun trigger(duration: Int = 30, intensity: Float = 0.1f) {
        curF = duration
        randX = intensity
    }

    override fun dispose() {
        material?.dispose()
        perturbationTexture?.dispose()
        fullScreenQuad.dispose()
        super.dispose()
    }

    companion object {
        private const val VERTEX_SHADER = """
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

        private const val FRAGMENT_SHADER = """
@group(0) @binding(0) var tDiffuse: texture_2d<f32>;
@group(0) @binding(1) var tDisp: texture_2d<f32>;
@group(0) @binding(2) var texSampler: sampler;

struct Uniforms {
    seed: f32,
    byp: i32,
    amount: f32,
    angle: f32,
    seed_x: f32,
    seed_y: f32,
    distortion_x: f32,
    distortion_y: f32,
    col_s: f32,
    resolution: vec2f,
}

@group(0) @binding(3) var<uniform> uniforms: Uniforms;

fn rand(co: vec2f) -> f32 {
    return fract(sin(dot(co.xy, vec2f(12.9898, 78.233))) * 43758.5453);
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    // Bypass mode - no glitch
    if (uniforms.byp == 1) {
        return textureSample(tDiffuse, texSampler, vUv);
    }
    
    var uv = vUv;
    
    // Get displacement from perturbation texture
    let disp = textureSample(tDisp, texSampler, uv);
    
    // Apply displacement
    if (disp.r < uniforms.seed_x && disp.g < uniforms.seed_y) {
        uv.x = fract(uv.x + uniforms.distortion_x);
    }
    
    // Random block displacement
    let blockOffset = floor(uv.y * 20.0) / 20.0;
    if (rand(vec2f(uniforms.seed, blockOffset)) < uniforms.amount * 0.5) {
        uv.x = fract(uv.x + rand(vec2f(uniforms.seed_x, blockOffset)) * 0.1 - 0.05);
    }
    
    // RGB channel separation
    let r = textureSample(tDiffuse, texSampler, uv + vec2f(uniforms.distortion_x, 0.0)).r;
    let g = textureSample(tDiffuse, texSampler, uv).g;
    let b = textureSample(tDiffuse, texSampler, uv + vec2f(-uniforms.distortion_x, 0.0)).b;
    
    // Scanline effect
    let scanline = sin(uv.y * uniforms.resolution.y * 0.5) * uniforms.col_s * uniforms.amount;
    
    var color = vec3f(r, g, b);
    color = color - vec3f(scanline);
    
    // Random noise
    let noise = rand(uv + vec2f(uniforms.seed)) * uniforms.amount * 0.1;
    color = color + vec3f(noise);
    
    return vec4f(color, 1.0);
}
"""
    }
}
