package io.materia.postprocessing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Halftone post-processing pass.
 *
 * Creates a CMYK halftone printing effect with configurable dot patterns
 * for each color channel. Simulates traditional newspaper or comic book
 * printing techniques.
 *
 * Based on Three.js HalftonePass implementation.
 *
 * Usage:
 * ```kotlin
 * val halftonePass = HalftonePass()
 * halftonePass.shape = HalftoneShape.Dot
 * halftonePass.radius = 4f
 * halftonePass.scatter = 0f
 * halftonePass.blending = 1f
 * 
 * composer.addPass(renderPass)
 * composer.addPass(halftonePass)
 * composer.addPass(outputPass)
 * ```
 */
class HalftonePass(
    /** Radius of the halftone dots */
    var radius: Float = 4f,
    /** Amount of random scatter applied to dots */
    var scatter: Float = 0f,
    /** Shape of the halftone pattern */
    var shape: HalftoneShape = HalftoneShape.Dot,
    /** Blending between original and halftone (0-1) */
    var blending: Float = 1f
) : Pass() {

    /**
     * Halftone pattern shapes
     */
    enum class HalftoneShape(val value: Int) {
        Dot(1),
        Ellipse(2),
        Line(3),
        Square(4)
    }

    /**
     * Rotation angle for cyan channel in degrees
     */
    var rotateC: Float = 15f

    /**
     * Rotation angle for magenta channel in degrees
     */
    var rotateM: Float = 75f

    /**
     * Rotation angle for yellow channel in degrees
     */
    var rotateY: Float = 0f

    /**
     * Rotation angle for black (key) channel in degrees
     */
    var rotateK: Float = 45f

    /**
     * Whether to render in grayscale
     */
    var grayscale: Boolean = false

    /**
     * Disable halftone effect for specific channels.
     * When true, that channel will not have halftone applied.
     */
    var disableC: Boolean = false
    var disableM: Boolean = false
    var disableY: Boolean = false
    var disableK: Boolean = false

    /**
     * Whether to show halftone dots in actual CMYK colors.
     * When false, uses RGB representation.
     */
    var greyscaleBlack: Boolean = false

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
        mat.setUniform("radius", radius)
        mat.setUniform("scatter", scatter)
        mat.setUniform("shape", shape.value)
        mat.setUniform("blending", blending)
        mat.setUniform("rotateC", rotateC * (PI.toFloat() / 180f))
        mat.setUniform("rotateM", rotateM * (PI.toFloat() / 180f))
        mat.setUniform("rotateY", rotateY * (PI.toFloat() / 180f))
        mat.setUniform("rotateK", rotateK * (PI.toFloat() / 180f))
        mat.setUniform("grayscale", if (grayscale) 1 else 0)
        mat.setUniform("disableC", if (disableC) 1 else 0)
        mat.setUniform("disableM", if (disableM) 1 else 0)
        mat.setUniform("disableY", if (disableY) 1 else 0)
        mat.setUniform("disableK", if (disableK) 1 else 0)
        mat.setUniform("greyscaleBlack", if (greyscaleBlack) 1 else 0)

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
    radius: f32,
    scatter: f32,
    shape: i32,
    blending: f32,
    rotateC: f32,
    rotateM: f32,
    rotateY: f32,
    rotateK: f32,
    grayscale: i32,
    disableC: i32,
    disableM: i32,
    disableY: i32,
    disableK: i32,
    greyscaleBlack: i32,
    resolution: vec2f,
}

@group(0) @binding(2) var<uniform> uniforms: Uniforms;

const PI: f32 = 3.14159265359;

fn hash(p: vec2f) -> f32 {
    var p3 = fract(vec3f(p.xyx) * 0.1031);
    p3 = p3 + vec3f(dot(p3, p3.yzx + 33.33));
    return fract((p3.x + p3.y) * p3.z);
}

// Rotate a 2D point
fn rotate2D(p: vec2f, angle: f32) -> vec2f {
    let s = sin(angle);
    let c = cos(angle);
    return vec2f(c * p.x - s * p.y, s * p.x + c * p.y);
}

// Convert RGB to CMYK
fn rgb2cmyk(rgb: vec3f) -> vec4f {
    let k = 1.0 - max(max(rgb.r, rgb.g), rgb.b);
    if (k >= 1.0) {
        return vec4f(0.0, 0.0, 0.0, 1.0);
    }
    let invK = 1.0 / (1.0 - k);
    return vec4f(
        (1.0 - rgb.r - k) * invK,
        (1.0 - rgb.g - k) * invK,
        (1.0 - rgb.b - k) * invK,
        k
    );
}

// Convert CMYK to RGB
fn cmyk2rgb(cmyk: vec4f) -> vec3f {
    return vec3f(
        (1.0 - cmyk.x) * (1.0 - cmyk.w),
        (1.0 - cmyk.y) * (1.0 - cmyk.w),
        (1.0 - cmyk.z) * (1.0 - cmyk.w)
    );
}

// Generate halftone pattern for a channel
fn halftone(uv: vec2f, angle: f32, value: f32, shape: i32) -> f32 {
    let radius = uniforms.radius;
    let scatter = uniforms.scatter;
    
    // Apply rotation
    var rotatedUV = rotate2D(uv, angle);
    
    // Add scatter
    if (scatter > 0.0) {
        let noise = hash(floor(rotatedUV / radius)) * 2.0 - 1.0;
        rotatedUV = rotatedUV + vec2f(noise * scatter);
    }
    
    // Get cell position
    let cell = floor(rotatedUV / radius);
    let cellUV = fract(rotatedUV / radius) - 0.5;
    
    // Calculate pattern based on shape
    var pattern: f32;
    
    if (shape == 1) {
        // Dot
        pattern = length(cellUV);
    } else if (shape == 2) {
        // Ellipse
        pattern = length(cellUV * vec2f(1.0, 0.5));
    } else if (shape == 3) {
        // Line
        pattern = abs(cellUV.y);
    } else {
        // Square
        pattern = max(abs(cellUV.x), abs(cellUV.y));
    }
    
    // Threshold based on value
    let threshold = sqrt(1.0 - value) * 0.5;
    return step(pattern, threshold);
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    // Sample original color
    let color = textureSample(tDiffuse, texSampler, vUv);
    let uv = vUv * uniforms.resolution;
    
    // Convert to CMYK
    var cmyk = rgb2cmyk(color.rgb);
    
    // Apply halftone to each channel
    var halftoneC = cmyk.x;
    var halftoneM = cmyk.y;
    var halftoneY = cmyk.z;
    var halftoneK = cmyk.w;
    
    if (uniforms.disableC == 0) {
        halftoneC = halftone(uv, uniforms.rotateC, cmyk.x, uniforms.shape);
    }
    if (uniforms.disableM == 0) {
        halftoneM = halftone(uv, uniforms.rotateM, cmyk.y, uniforms.shape);
    }
    if (uniforms.disableY == 0) {
        halftoneY = halftone(uv, uniforms.rotateY, cmyk.z, uniforms.shape);
    }
    if (uniforms.disableK == 0) {
        halftoneK = halftone(uv, uniforms.rotateK, cmyk.w, uniforms.shape);
    }
    
    // Reconstruct color
    var result: vec3f;
    
    if (uniforms.grayscale == 1 || uniforms.greyscaleBlack == 1) {
        // Grayscale mode - use only K channel
        let k = halftoneK;
        result = vec3f(1.0 - k);
    } else {
        // Full CMYK
        let halftoneCMYK = vec4f(halftoneC, halftoneM, halftoneY, halftoneK);
        result = cmyk2rgb(halftoneCMYK);
    }
    
    // Blend with original
    result = mix(color.rgb, result, uniforms.blending);
    
    return vec4f(result, color.a);
}
"""
    }
}
