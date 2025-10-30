package io.materia.postprocessing

import io.materia.camera.Camera
import io.materia.core.Object3D
import io.materia.material.ShaderMaterial
import io.materia.material.MeshBasicMaterial
import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.renderer.*
import io.materia.core.scene.Scene

/**
 * OutlinePass renders outlines around selected objects.
 *
 * @property scene The scene to render
 * @property camera The camera to render from
 * @property selectedObjects Objects to outline
 * @property edgeStrength Strength of edge detection (0.0 - 10.0)
 * @property edgeGlow Amount of glow around edges (0.0 - 1.0)
 * @property edgeThickness Thickness of the outline in pixels
 * @property visibleEdgeColor Color for visible edges
 * @property hiddenEdgeColor Color for hidden edges (behind other objects)
 */
class OutlinePass(
    val scene: Scene,
    val camera: Camera,
    val selectedObjects: MutableList<Object3D> = mutableListOf(),
    var edgeStrength: Float = 3.0f,
    var edgeGlow: Float = 0.0f,
    var edgeThickness: Float = 1.0f,
    var visibleEdgeColor: Color = Color(1f, 1f, 1f),
    var hiddenEdgeColor: Color = Color(0.1f, 0.04f, 0.02f)
) : Pass() {

    var usePatternTexture: Boolean = false
    var patternTexture: Texture? = null
    var downSampleRatio: Float = 2.0f
    var pulsePeriod: Float = 0.0f

    private var maskRenderTarget: WebGPURenderTarget? = null
    private var renderTargetEdges: WebGPURenderTarget? = null
    private var renderTargetBlur1: WebGPURenderTarget? = null
    private var renderTargetBlur2: WebGPURenderTarget? = null

    private val prepareMaskPass = RenderPass(scene, camera)
    private val edgeDetectionPass = ShaderPass(createEdgeDetectionShader())
    private val separableBlurPass1 = ShaderPass(createSeparableBlurShader(true))
    private val separableBlurPass2 = ShaderPass(createSeparableBlurShader(false))
    private val overlayPass = ShaderPass(createOverlayShader())

    private val maskMaterial = MeshBasicMaterial().apply {
        color = Color(1f, 1f, 1f)
        side = Material.Side.Double
    }

    init {
        needsSwap = false
        prepareMaskPass.overrideMaterial = maskMaterial
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)

        val resx = (width / downSampleRatio).toInt()
        val resy = (height / downSampleRatio).toInt()

        maskRenderTarget?.dispose()
        renderTargetEdges?.dispose()
        renderTargetBlur1?.dispose()
        renderTargetBlur2?.dispose()

        val rtOptions = RenderTargetOptions(
            format = TextureFormat.RGBA8,
            depthBuffer = false,
            stencilBuffer = false
        )

        maskRenderTarget = WebGPURenderTarget(resx, resy, rtOptions)
        renderTargetEdges = WebGPURenderTarget(resx, resy, rtOptions)
        renderTargetBlur1 = WebGPURenderTarget(resx, resy, rtOptions)
        renderTargetBlur2 = WebGPURenderTarget(resx, resy, rtOptions)

        prepareMaskPass.setSize(resx, resy)
        edgeDetectionPass.setSize(resx, resy)
        separableBlurPass1.setSize(resx, resy)
        separableBlurPass2.setSize(resx, resy)
        overlayPass.setSize(width, height)

        updateUniforms()
    }

    private fun updateUniforms() {
        edgeDetectionPass.uniforms["edgeStrength"] = edgeStrength
        edgeDetectionPass.uniforms["edgeGlow"] = edgeGlow
        edgeDetectionPass.uniforms["edgeThickness"] = edgeThickness

        separableBlurPass1.uniforms["edgeThickness"] = edgeThickness
        separableBlurPass2.uniforms["edgeThickness"] = edgeThickness

        overlayPass.uniforms["visibleEdgeColor"] = visibleEdgeColor.toArray()
        overlayPass.uniforms["hiddenEdgeColor"] = hiddenEdgeColor.toArray()
        overlayPass.uniforms["pulsePeriod"] = pulsePeriod
        overlayPass.uniforms["usePatternTexture"] = usePatternTexture
        overlayPass.uniforms["patternTexture"] = patternTexture
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        if (selectedObjects.isEmpty()) {
            // No objects selected, just copy input
            val copyPass = ShaderPass(CopyShader())
            copyPass.uniforms["tDiffuse"] = readBuffer.texture
            copyPass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
            return
        }

        // Ensure render targets are initialized
        val maskTarget = maskRenderTarget ?: run {
            println("Warning: OutlinePass not initialized, call setSize() first")
            return
        }
        val edgesTarget = renderTargetEdges ?: return
        val blur1Target = renderTargetBlur1 ?: return
        val blur2Target = renderTargetBlur2 ?: return

        // 1. Render selected objects to mask
        val oldBackground = scene.background
        val oldOverrideMaterial = scene.overrideMaterial

        scene.background = null
        scene.overrideMaterial = maskMaterial

        // Hide non-selected objects
        scene.traverse { obj ->
            if (obj !in selectedObjects) {
                obj.visible = false
            }
        }

        prepareMaskPass.render(renderer, maskTarget, readBuffer, deltaTime, maskActive)

        // Restore visibility
        scene.traverse { obj ->
            obj.visible = true
        }

        scene.background = oldBackground
        scene.overrideMaterial = oldOverrideMaterial

        // 2. Edge detection
        edgeDetectionPass.uniforms["tDiffuse"] = maskTarget.texture
        edgeDetectionPass.render(renderer, edgesTarget, maskTarget, deltaTime, maskActive)

        // 3. Blur edges horizontally
        separableBlurPass1.uniforms["tDiffuse"] = edgesTarget.texture
        separableBlurPass1.render(renderer, blur1Target, edgesTarget, deltaTime, maskActive)

        // 4. Blur edges vertically
        separableBlurPass2.uniforms["tDiffuse"] = blur1Target.texture
        separableBlurPass2.render(renderer, blur2Target, blur1Target, deltaTime, maskActive)

        // 5. Overlay edges on original image
        overlayPass.uniforms["tDiffuse"] = readBuffer.texture
        overlayPass.uniforms["tEdges"] = blur2Target.texture
        overlayPass.uniforms["time"] = performance.now()
        updateUniforms()

        overlayPass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
    }

    override fun dispose() {
        maskRenderTarget?.dispose()
        renderTargetEdges?.dispose()
        renderTargetBlur1?.dispose()
        renderTargetBlur2?.dispose()
        prepareMaskPass.dispose()
        edgeDetectionPass.dispose()
        separableBlurPass1.dispose()
        separableBlurPass2.dispose()
        overlayPass.dispose()
        maskMaterial.dispose()
        super.dispose()
    }

    private fun createEdgeDetectionShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["edgeStrength"] = 3.0f
            uniforms["edgeGlow"] = 0.0f
            uniforms["edgeThickness"] = 1.0f

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
                @group(0) @binding(0) var<uniform> edgeStrength: f32;
                @group(0) @binding(1) var<uniform> edgeGlow: f32;
                @group(0) @binding(2) var<uniform> edgeThickness: f32;
                @group(0) @binding(3) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(4) var tDiffuseSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let texelSize = vec2<f32>(edgeThickness) / vec2<f32>(textureDimensions(tDiffuse));

                    // Sobel edge detection
                    var sobelX = 0.0;
                    var sobelY = 0.0;

                    // Sample neighboring pixels
                    let tl = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(-texelSize.x, texelSize.y)).r;
                    let tm = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(0.0, texelSize.y)).r;
                    let tr = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(texelSize.x, texelSize.y)).r;
                    let ml = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(-texelSize.x, 0.0)).r;
                    let mm = textureSample(tDiffuse, tDiffuseSampler, uv).r;
                    let mr = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(texelSize.x, 0.0)).r;
                    let bl = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(-texelSize.x, -texelSize.y)).r;
                    let bm = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(0.0, -texelSize.y)).r;
                    let br = textureSample(tDiffuse, tDiffuseSampler, uv + vec2<f32>(texelSize.x, -texelSize.y)).r;

                    // Sobel X
                    sobelX = -1.0 * tl + 1.0 * tr +
                             -2.0 * ml + 2.0 * mr +
                             -1.0 * bl + 1.0 * br;

                    // Sobel Y
                    sobelY = -1.0 * tl - 2.0 * tm - 1.0 * tr +
                              1.0 * bl + 2.0 * bm + 1.0 * br;

                    let edge = sqrt(sobelX * sobelX + sobelY * sobelY);
                    let finalEdge = smoothstep(0.0, 1.0, edge * edgeStrength);

                    return vec4<f32>(finalEdge, finalEdge, finalEdge, 1.0);
                }
            """
        }
    }

    private fun createSeparableBlurShader(horizontal: Boolean): ShaderMaterial {
        val direction = if (horizontal) "vec2<f32>(1.0, 0.0)" else "vec2<f32>(0.0, 1.0)"

        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["edgeThickness"] = 1.0f

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
                @group(0) @binding(0) var<uniform> edgeThickness: f32;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;

                const weights: array<f32, 9> = array<f32, 9>(
                    0.051, 0.0918, 0.12245, 0.1531, 0.1633, 0.1531, 0.12245, 0.0918, 0.051
                );

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let texelSize = $direction / vec2<f32>(textureDimensions(tDiffuse));
                    var result = vec4<f32>(0.0);

                    for (var i = 0; i < 9; i = i + 1) {
                        let offset = texelSize * edgeThickness * f32(i - 4);
                        result = result + textureSample(tDiffuse, tDiffuseSampler, uv + offset) * weights[i];
                    }

                    return result;
                }
            """
        }
    }

    private fun createOverlayShader(): ShaderMaterial {
        return ShaderMaterial().apply {
            uniforms["tDiffuse"] = null
            uniforms["tEdges"] = null
            uniforms["visibleEdgeColor"] = floatArrayOf(1f, 1f, 1f)
            uniforms["hiddenEdgeColor"] = floatArrayOf(0.1f, 0.04f, 0.02f)
            uniforms["time"] = 0.0
            uniforms["pulsePeriod"] = 0.0f
            uniforms["usePatternTexture"] = false
            uniforms["patternTexture"] = null

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
                    visibleEdgeColor: vec3<f32>,
                    time: f32,
                    pulsePeriod: f32,
                }

                @group(0) @binding(0) var<uniform> uniforms: Uniforms;
                @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
                @group(0) @binding(2) var tDiffuseSampler: sampler;
                @group(0) @binding(3) var tEdges: texture_2d<f32>;
                @group(0) @binding(4) var tEdgesSampler: sampler;

                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let base = textureSample(tDiffuse, tDiffuseSampler, uv);
                    let edges = textureSample(tEdges, tEdgesSampler, uv).r;

                    var edgeColor = uniforms.visibleEdgeColor;

                    // Apply pulse if enabled
                    if (uniforms.pulsePeriod > 0.0) {
                        let pulse = (sin(uniforms.time * 0.001 / uniforms.pulsePeriod) + 1.0) * 0.5;
                        edgeColor = edgeColor * pulse;
                    }

                    // Blend edges with base
                    return mix(base, vec4<f32>(edgeColor, 1.0), edges);
                }
            """
        }
    }
}

// Extension function to convert Color to array
private fun Color.toArray(): FloatArray = floatArrayOf(r, g, b)