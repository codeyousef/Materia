package io.materia.postprocessing

/**
 * Afterimage (motion blur/trails) post-processing pass.
 *
 * Creates a trail/echo effect by blending the current frame with
 * previous frames. Useful for motion blur, ghosting effects,
 * or artistic trail effects.
 *
 * Based on Three.js AfterimagePass implementation.
 *
 * Usage:
 * ```kotlin
 * val afterimagePass = AfterimagePass()
 * afterimagePass.damp = 0.96f // Higher = longer trails
 * 
 * composer.addPass(renderPass)
 * composer.addPass(afterimagePass)
 * composer.addPass(outputPass)
 * ```
 */
class AfterimagePass(
    /** Damping factor - how much of the previous frame persists (0.0 to 1.0) */
    var damp: Float = 0.96f
) : Pass() {

    /**
     * Whether to apply exponential decay.
     * When true, uses exponential falloff for smoother trails.
     */
    var exponentialDecay: Boolean = false

    /**
     * Blend mode for combining frames.
     */
    var blendMode: AfterimageBlendMode = AfterimageBlendMode.Normal

    /**
     * Color tint applied to trails.
     */
    var tintColor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f)

    /**
     * Blend modes for afterimage effect
     */
    enum class AfterimageBlendMode(val value: Int) {
        /** Standard alpha blending */
        Normal(0),
        /** Additive blending for glowing trails */
        Additive(1),
        /** Screen blending for softer trails */
        Screen(2),
        /** Multiply for darker trails */
        Multiply(3)
    }

    // Internal state
    private val fullScreenQuad = FullScreenQuad()
    private var afterimageMaterial: ShaderMaterial? = null
    private var blendMaterial: ShaderMaterial? = null
    
    // Ping-pong render targets for frame accumulation
    private var textureComp: RenderTarget? = null
    private var textureOld: RenderTarget? = null

    init {
        needsSwap = false // We handle our own buffer management
        initMaterials()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        // Recreate render targets at new size
        textureComp?.dispose()
        textureOld?.dispose()

        val rtConfig = RenderTargetOptions(
            minFilter = TextureFilter.Linear,
            magFilter = TextureFilter.Linear,
            format = TextureFormat.RGBA8,
            stencilBuffer = false,
            depthBuffer = false
        )

        textureComp = WebGPURenderTarget(width, height, rtConfig)
        textureOld = WebGPURenderTarget(width, height, rtConfig)
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Initialize render targets if needed
        if (textureComp == null || textureOld == null) {
            setSize(
                renderer.getSize().x,
                renderer.getSize().y
            )
        }

        val comp = textureComp ?: return
        val old = textureOld ?: return
        val afterMat = afterimageMaterial ?: return
        val blendMat = blendMaterial ?: return

        // Step 1: Blend new frame with old frame into comp target
        afterMat.setUniform("tOld", old.texture)
        afterMat.setUniform("tNew", readBuffer.texture)
        afterMat.setUniform("damp", damp)
        afterMat.setUniform("exponentialDecay", if (exponentialDecay) 1 else 0)
        afterMat.setUniform("blendMode", blendMode.value)
        afterMat.setUniform("tintColor", floatArrayOf(tintColor.first, tintColor.second, tintColor.third))

        renderer.setRenderTarget(comp)
        if (clear) renderer.clear(true, true, false)
        fullScreenQuad.render(renderer, afterMat)

        // Step 2: Copy comp to output
        blendMat.setUniform("tDiffuse", comp.texture)
        
        renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)
        if (clear) renderer.clear(true, true, false)
        fullScreenQuad.render(renderer, blendMat)

        // Step 3: Copy comp to old for next frame (ping-pong)
        blendMat.setUniform("tDiffuse", comp.texture)
        renderer.setRenderTarget(old)
        fullScreenQuad.render(renderer, blendMat)
    }

    private fun initMaterials() {
        afterimageMaterial = ShaderMaterial(
            vertexShader = VERTEX_SHADER,
            fragmentShader = AFTERIMAGE_FRAGMENT_SHADER
        )

        blendMaterial = ShaderMaterial(
            vertexShader = VERTEX_SHADER,
            fragmentShader = BLEND_FRAGMENT_SHADER
        )
    }

    /**
     * Clears the afterimage buffer, removing all trails.
     */
    fun clear(renderer: Renderer) {
        textureComp?.clear(renderer)
        textureOld?.clear(renderer)
    }

    override fun dispose() {
        afterimageMaterial?.dispose()
        blendMaterial?.dispose()
        textureComp?.dispose()
        textureOld?.dispose()
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

        private const val AFTERIMAGE_FRAGMENT_SHADER = """
@group(0) @binding(0) var tOld: texture_2d<f32>;
@group(0) @binding(1) var tNew: texture_2d<f32>;
@group(0) @binding(2) var texSampler: sampler;

struct Uniforms {
    damp: f32,
    exponentialDecay: i32,
    blendMode: i32,
    tintColor: vec3f,
}

@group(0) @binding(3) var<uniform> uniforms: Uniforms;

fn blendNormal(base: vec3f, blend: vec3f, opacity: f32) -> vec3f {
    return mix(base, blend, opacity);
}

fn blendAdditive(base: vec3f, blend: vec3f) -> vec3f {
    return min(base + blend, vec3f(1.0));
}

fn blendScreen(base: vec3f, blend: vec3f) -> vec3f {
    return vec3f(1.0) - (vec3f(1.0) - base) * (vec3f(1.0) - blend);
}

fn blendMultiply(base: vec3f, blend: vec3f) -> vec3f {
    return base * blend;
}

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    let newColor = textureSample(tNew, texSampler, vUv);
    var oldColor = textureSample(tOld, texSampler, vUv);
    
    // Apply tint to old color (trails)
    oldColor = vec4f(oldColor.rgb * uniforms.tintColor, oldColor.a);
    
    // Calculate damping
    var damp = uniforms.damp;
    if (uniforms.exponentialDecay == 1) {
        // Exponential decay for smoother trails
        damp = pow(uniforms.damp, 2.2);
    }
    
    // Blend old with damping
    var blendedOld = oldColor.rgb * damp;
    
    // Combine with new frame based on blend mode
    var result: vec3f;
    
    if (uniforms.blendMode == 0) {
        // Normal blend
        result = max(newColor.rgb, blendedOld);
    } else if (uniforms.blendMode == 1) {
        // Additive
        result = blendAdditive(newColor.rgb, blendedOld);
    } else if (uniforms.blendMode == 2) {
        // Screen
        result = blendScreen(newColor.rgb, blendedOld);
    } else {
        // Multiply
        result = blendMultiply(newColor.rgb, blendedOld);
    }
    
    return vec4f(result, max(newColor.a, oldColor.a));
}
"""

        private const val BLEND_FRAGMENT_SHADER = """
@group(0) @binding(0) var tDiffuse: texture_2d<f32>;
@group(0) @binding(1) var texSampler: sampler;

@fragment
fn main(@location(0) vUv: vec2f) -> @location(0) vec4f {
    return textureSample(tDiffuse, texSampler, vUv);
}
"""
    }
}
