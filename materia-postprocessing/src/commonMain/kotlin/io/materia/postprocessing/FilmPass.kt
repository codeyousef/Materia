package io.materia.postprocessing

import kotlin.random.Random

/**
 * Film grain and scanline post-processing pass.
 *
 * Simulates the look of analog film with grain noise, scanlines,
 * and optional grayscale/sepia effects. Creates a vintage or
 * cinematic aesthetic.
 *
 * Based on Three.js FilmPass implementation.
 *
 * Usage:
 * ```kotlin
 * val filmPass = FilmPass()
 * filmPass.noiseIntensity = 0.35f
 * filmPass.scanlineIntensity = 0.5f
 * filmPass.scanlineCount = 256
 * filmPass.grayscale = false
 * 
 * composer.addPass(renderPass)
 * composer.addPass(filmPass)
 * composer.addPass(outputPass)
 * ```
 */
class FilmPass(
    /** Intensity of the noise/grain effect (0.0 to 1.0) */
    var noiseIntensity: Float = 0.35f,
    /** Intensity of the scanline effect (0.0 to 1.0) */
    var scanlineIntensity: Float = 0.5f,
    /** Number of scanlines across the screen height */
    var scanlineCount: Int = 256,
    /** Whether to convert output to grayscale */
    var grayscale: Boolean = false
) : Pass() {

    /**
     * Speed of the grain animation (higher = faster flickering).
     */
    var grainSpeed: Float = 1f

    /**
     * Size of the grain particles (higher = larger grain).
     */
    var grainSize: Float = 1f

    /**
     * Whether to apply vignette darkening at edges.
     */
    var vignette: Boolean = false

    /**
     * Intensity of the vignette effect (0.0 to 1.0).
     */
    var vignetteIntensity: Float = 0.5f

    /**
     * Apply sepia toning instead of grayscale.
     */
    var sepia: Boolean = false

    /**
     * Intensity of sepia effect (0.0 to 1.0).
     */
    var sepiaIntensity: Float = 1f

    // Internal state
    private var time: Float = 0f
    private val fullScreenQuad = FullScreenQuad()
    private var material: ShaderMaterial? = null

    init {
        needsSwap = true
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

        // Update time for animated grain
        time += deltaTime * grainSpeed

        // Set uniforms
        mat.setUniform("tDiffuse", readBuffer.texture)
        mat.setUniform("time", time)
        mat.setUniform("noiseIntensity", noiseIntensity)
        mat.setUniform("scanlineIntensity", scanlineIntensity)
        mat.setUniform("scanlineCount", scanlineCount)
        mat.setUniform("grayscale", if (grayscale) 1 else 0)
        mat.setUniform("grainSize", grainSize)
        mat.setUniform("vignette", if (vignette) 1 else 0)
        mat.setUniform("vignetteIntensity", vignetteIntensity)
        mat.setUniform("sepia", if (sepia) 1 else 0)
        mat.setUniform("sepiaIntensity", sepiaIntensity)

        renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)
        if (clear) renderer.clear(true, true, false)
        fullScreenQuad.render(renderer, mat)
    }

    private fun initMaterial() {
        material = ShaderMaterial(
            vertexShader = VERTEX_SHADER,
            fragmentShader = FRAGMENT_SHADER
        )
    }

    override fun dispose() {
        material?.dispose()
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
@group(0) @binding(1) var texSampler: sampler;

struct Uniforms {
    time: f32,
    noiseIntensity: f32,
    scanlineIntensity: f32,
    scanlineCount: i32,
    grayscale: i32,
    grainSize: f32,
    vignette: i32,
    vignetteIntensity: f32,
    sepia: i32,
    sepiaIntensity: f32,
    resolution: vec2f,
}

@group(0) @binding(2) var<uniform> uniforms: Uniforms;

// Hash function for noise generation
fn hash(p: vec2f) -> f32 {
    var p3 = fract(vec3f(p.xyx) * 0.1031);
    p3 = p3 + vec3f(dot(p3, p3.yzx + 33.33));
    return fract((p3.x + p3.y) * p3.z);
}

// Film grain noise
fn filmGrain(uv: vec2f, time: f32, intensity: f32, size: f32) -> f32 {
    let grain = hash(uv * size + vec2f(time * 0.1));
    return (grain - 0.5) * intensity;
}

// Scanline effect
fn scanlines(uv: vec2f, count: i32, intensity: f32) -> f32 {
    let scanline = sin(uv.y * f32(count) * 3.14159265) * 0.5 + 0.5;
    return 1.0 - (1.0 - scanline) * intensity;
}

// Vignette effect
fn vignetteEffect(uv: vec2f, intensity: f32) -> f32 {
    let coord = (uv - 0.5) * 2.0;
    let dist = length(coord);
    return 1.0 - smoothstep(0.5, 1.5, dist) * intensity;
}

// Convert to grayscale using luminance
fn luminance(color: vec3f) -> f32 {
    return dot(color, vec3f(0.2126, 0.7152, 0.0722));
}

// Sepia tone
fn sepiaColor(color: vec3f, intensity: f32) -> vec3f {
    let sepia = vec3f(
        dot(color, vec3f(0.393, 0.769, 0.189)),
        dot(color, vec3f(0.349, 0.686, 0.168)),
        dot(color, vec3f(0.272, 0.534, 0.131))
    );
    return mix(color, sepia, intensity);
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    // Sample original color
    var color = textureSample(tDiffuse, texSampler, vUv).rgb;
    
    // Apply film grain
    let grain = filmGrain(vUv, uniforms.time, uniforms.noiseIntensity, uniforms.grainSize * 512.0);
    color = color + vec3f(grain);
    
    // Apply scanlines
    let scanline = scanlines(vUv, uniforms.scanlineCount, uniforms.scanlineIntensity);
    color = color * scanline;
    
    // Apply vignette
    if (uniforms.vignette == 1) {
        let vignetteFactor = vignetteEffect(vUv, uniforms.vignetteIntensity);
        color = color * vignetteFactor;
    }
    
    // Apply grayscale or sepia
    if (uniforms.grayscale == 1) {
        let lum = luminance(color);
        color = vec3f(lum);
    } else if (uniforms.sepia == 1) {
        color = sepiaColor(color, uniforms.sepiaIntensity);
    }
    
    // Clamp to valid range
    color = clamp(color, vec3f(0.0), vec3f(1.0));
    
    return vec4f(color, 1.0);
}
"""
    }
}
