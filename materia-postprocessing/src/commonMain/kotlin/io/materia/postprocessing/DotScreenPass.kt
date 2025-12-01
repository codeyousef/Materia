package io.materia.postprocessing

import kotlin.math.sqrt

/**
 * Dot screen post-processing pass.
 *
 * Creates a dot matrix/halftone pattern effect, simulating
 * newspaper printing or pop art aesthetics. Each pixel is
 * represented by a circular dot whose size varies with brightness.
 *
 * Based on Three.js DotScreenPass implementation.
 *
 * Usage:
 * ```kotlin
 * val dotScreenPass = DotScreenPass()
 * dotScreenPass.scale = 1f
 * dotScreenPass.angle = 1.57f // 90 degrees
 * 
 * composer.addPass(renderPass)
 * composer.addPass(dotScreenPass)
 * composer.addPass(outputPass)
 * ```
 */
class DotScreenPass(
    /** Center of the dot pattern (normalized 0-1 coordinates) */
    var center: Pair<Float, Float> = Pair(0.5f, 0.5f),
    /** Rotation angle of the dot pattern in radians */
    var angle: Float = 1.57f,
    /** Scale of the dot pattern (higher = larger dots) */
    var scale: Float = 1f
) : Pass() {

    /**
     * Tint color for the dots.
     * Default is no tint (white).
     */
    var tintColor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f)

    /**
     * Whether to invert the dot pattern.
     */
    var invert: Boolean = false

    /**
     * Whether to apply grayscale before dot pattern.
     */
    var grayscale: Boolean = true

    // Internal state
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

        // Set uniforms
        mat.setUniform("tDiffuse", readBuffer.texture)
        mat.setUniform("center", floatArrayOf(center.first, center.second))
        mat.setUniform("angle", angle)
        mat.setUniform("scale", scale)
        mat.setUniform("tintColor", floatArrayOf(tintColor.first, tintColor.second, tintColor.third))
        mat.setUniform("invert", if (invert) 1 else 0)
        mat.setUniform("grayscale", if (grayscale) 1 else 0)

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
    center: vec2f,
    angle: f32,
    scale: f32,
    tintColor: vec3f,
    invert: i32,
    grayscale: i32,
    resolution: vec2f,
}

@group(0) @binding(2) var<uniform> uniforms: Uniforms;

const PI: f32 = 3.14159265359;

fn pattern(uv: vec2f) -> f32 {
    let s = sin(uniforms.angle);
    let c = cos(uniforms.angle);
    
    // Transform UV coordinates
    var texPos = uv - uniforms.center;
    let point = vec2f(
        c * texPos.x - s * texPos.y,
        s * texPos.x + c * texPos.y
    ) * uniforms.scale;
    
    // Create dot pattern using sin waves
    let pattern = sin(point.x * PI) * sin(point.y * PI) * 4.0;
    
    return pattern;
}

fn luminance(color: vec3f) -> f32 {
    return dot(color, vec3f(0.2126, 0.7152, 0.0722));
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    // Sample original color
    let color = textureSample(tDiffuse, texSampler, vUv);
    
    // Get luminance for dot size
    var brightness: f32;
    if (uniforms.grayscale == 1) {
        brightness = luminance(color.rgb);
    } else {
        brightness = (color.r + color.g + color.b) / 3.0;
    }
    
    // Get dot pattern
    var p = pattern(vUv * uniforms.resolution / 10.0);
    
    // Apply brightness to pattern
    var result = brightness * 10.0 - 5.0 + p;
    
    // Threshold to create dots
    result = step(0.0, result);
    
    // Invert if needed
    if (uniforms.invert == 1) {
        result = 1.0 - result;
    }
    
    // Apply tint
    var finalColor = vec3f(result) * uniforms.tintColor;
    
    return vec4f(finalColor, color.a);
}
"""
    }
}
