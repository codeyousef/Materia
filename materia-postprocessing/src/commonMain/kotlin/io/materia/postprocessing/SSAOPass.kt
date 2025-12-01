package io.materia.postprocessing

import io.materia.camera.Camera
import io.materia.core.scene.Scene
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Screen-Space Ambient Occlusion (SSAO) post-processing pass.
 * 
 * SSAO approximates ambient occlusion in screen space by sampling the depth
 * buffer around each pixel to determine how "occluded" it is by nearby geometry.
 * This creates realistic soft shadows in corners and crevices.
 *
 * Based on John Chapman's SSAO technique with improvements from Three.js.
 *
 * Usage:
 * ```kotlin
 * val ssaoPass = SSAOPass(scene, camera, width, height)
 * ssaoPass.kernelRadius = 16f
 * ssaoPass.minDistance = 0.005f
 * ssaoPass.maxDistance = 0.1f
 * 
 * composer.addPass(renderPass)
 * composer.addPass(ssaoPass)
 * composer.addPass(outputPass)
 * ```
 */
class SSAOPass(
    var scene: Scene,
    var camera: Camera,
    width: Int = 512,
    height: Int = 512,
    config: SSAOConfig = SSAOConfig()
) : Pass() {

    /**
     * SSAO configuration parameters
     */
    data class SSAOConfig(
        /** Number of samples per pixel (higher = better quality, slower) */
        val kernelSize: Int = 32,
        /** Radius of sampling hemisphere */
        val kernelRadius: Float = 8f,
        /** Minimum distance for occlusion */
        val minDistance: Float = 0.005f,
        /** Maximum distance for occlusion */
        val maxDistance: Float = 0.1f,
        /** Output mode for debugging */
        val output: SSAOOutput = SSAOOutput.Default
    )

    /**
     * SSAO output mode
     */
    enum class SSAOOutput {
        /** Normal SSAO blended with scene */
        Default,
        /** Just the SSAO factor (for debugging) */
        SSAOOnly,
        /** Blur pass output */
        BlurOnly,
        /** Depth buffer visualization */
        Depth,
        /** Normal buffer visualization */
        Normal
    }

    // Configuration
    private var config = config.copy()

    /** Number of samples per pixel */
    var kernelSize: Int
        get() = config.kernelSize
        set(value) {
            config = config.copy(kernelSize = value.coerceIn(1, 64))
            generateKernel()
        }

    /** Sampling hemisphere radius */
    var kernelRadius: Float
        get() = config.kernelRadius
        set(value) {
            config = config.copy(kernelRadius = value)
        }

    /** Minimum occlusion distance */
    var minDistance: Float
        get() = config.minDistance
        set(value) {
            config = config.copy(minDistance = value)
        }

    /** Maximum occlusion distance */
    var maxDistance: Float
        get() = config.maxDistance
        set(value) {
            config = config.copy(maxDistance = value)
        }

    /** Output mode */
    var output: SSAOOutput
        get() = config.output
        set(value) {
            config = config.copy(output = value)
        }

    // Internal render targets
    private var normalRenderTarget: RenderTarget? = null
    private var ssaoRenderTarget: RenderTarget? = null
    private var blurRenderTarget: RenderTarget? = null

    // Sampling kernel
    private var kernel = FloatArray(0)
    private var noiseTexture: Texture? = null

    // Shader materials
    private var ssaoMaterial: ShaderMaterial? = null
    private var normalMaterial: ShaderMaterial? = null
    private var blurMaterial: ShaderMaterial? = null
    private var copyMaterial: ShaderMaterial? = null
    private var depthRenderMaterial: ShaderMaterial? = null

    // Full-screen quad
    private val fsQuad = FullScreenQuad()

    // Original settings to restore
    private var originalClearColor: Int = 0

    init {
        this.width = width
        this.height = height
        
        generateKernel()
        generateNoiseTexture()
        initMaterials()
        initRenderTargets()
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        
        // Recreate render targets at new size
        normalRenderTarget?.setSize(width, height)
        ssaoRenderTarget?.setSize(width, height)
        blurRenderTarget?.setSize(width, height)

        // Update shader uniforms
        ssaoMaterial?.setUniform("resolution", Vector2(width.toFloat(), height.toFloat()))
        blurMaterial?.setUniform("resolution", Vector2(width.toFloat(), height.toFloat()))
    }

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // 1. Render normals to buffer
        renderNormals(renderer)

        // 2. Render SSAO
        renderSSAO(renderer, readBuffer)

        // 3. Blur SSAO result
        renderBlur(renderer)

        // 4. Composite or output
        when (output) {
            SSAOOutput.SSAOOnly -> {
                copyToOutput(renderer, ssaoRenderTarget!!, writeBuffer)
            }
            SSAOOutput.BlurOnly -> {
                copyToOutput(renderer, blurRenderTarget!!, writeBuffer)
            }
            SSAOOutput.Depth -> {
                renderDepth(renderer, writeBuffer)
            }
            SSAOOutput.Normal -> {
                copyToOutput(renderer, normalRenderTarget!!, writeBuffer)
            }
            SSAOOutput.Default -> {
                // Blend SSAO with scene
                compositeSSAO(renderer, readBuffer, writeBuffer)
            }
        }
    }

    private fun renderNormals(renderer: Renderer) {
        val normalTarget = normalRenderTarget ?: return
        val normalMat = normalMaterial ?: return

        renderer.setRenderTarget(normalTarget)
        renderer.clear(true, true, false)

        // Override scene material to render normals
        val originalOverride = scene.overrideMaterial
        scene.overrideMaterial = normalMat

        renderer.render(scene, camera)

        scene.overrideMaterial = originalOverride
    }

    private fun renderSSAO(renderer: Renderer, colorBuffer: RenderTarget) {
        val ssaoTarget = ssaoRenderTarget ?: return
        val mat = ssaoMaterial ?: return

        // Set uniforms
        mat.setUniform("tDiffuse", colorBuffer.texture)
        mat.setUniform("tNormal", normalRenderTarget?.texture)
        mat.setUniform("tDepth", colorBuffer.depthTexture)
        mat.setUniform("tNoise", noiseTexture)
        mat.setUniform("kernel", kernel)
        mat.setUniform("cameraNear", camera.near)
        mat.setUniform("cameraFar", camera.far)
        mat.setUniform("resolution", Vector2(width.toFloat(), height.toFloat()))
        mat.setUniform("cameraProjectionMatrix", camera.projectionMatrix)
        mat.setUniform("cameraInverseProjectionMatrix", camera.projectionMatrixInverse)
        mat.setUniform("kernelRadius", kernelRadius)
        mat.setUniform("minDistance", minDistance)
        mat.setUniform("maxDistance", maxDistance)

        renderer.setRenderTarget(ssaoTarget)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun renderBlur(renderer: Renderer) {
        val blurTarget = blurRenderTarget ?: return
        val mat = blurMaterial ?: return

        mat.setUniform("tDiffuse", ssaoRenderTarget?.texture)
        mat.setUniform("resolution", Vector2(width.toFloat(), height.toFloat()))

        renderer.setRenderTarget(blurTarget)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun renderDepth(renderer: Renderer, target: RenderTarget) {
        val mat = depthRenderMaterial ?: return

        mat.setUniform("tDepth", normalRenderTarget?.depthTexture)
        mat.setUniform("cameraNear", camera.near)
        mat.setUniform("cameraFar", camera.far)

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun copyToOutput(renderer: Renderer, source: RenderTarget, target: RenderTarget) {
        val mat = copyMaterial ?: return

        mat.setUniform("tDiffuse", source.texture)

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    private fun compositeSSAO(renderer: Renderer, colorBuffer: RenderTarget, target: RenderTarget) {
        val mat = copyMaterial ?: return

        // Multiply SSAO with color buffer
        mat.setUniform("tDiffuse", colorBuffer.texture)
        mat.setUniform("tSSAO", blurRenderTarget?.texture)
        mat.setUniform("mode", 1) // Multiply blend

        renderer.setRenderTarget(if (renderToScreen) null else target)
        renderer.clear(true, true, false)
        fsQuad.render(renderer, mat)
    }

    /**
     * Generate sampling kernel with cosine-weighted hemisphere distribution
     */
    private fun generateKernel() {
        val size = config.kernelSize
        kernel = FloatArray(size * 3)

        for (i in 0 until size) {
            // Random direction in hemisphere
            val phi = Random.nextFloat() * 2f * PI.toFloat()
            val cosTheta = Random.nextFloat()
            val sinTheta = kotlin.math.sqrt(1f - cosTheta * cosTheta)

            var x = cos(phi) * sinTheta
            var y = sin(phi) * sinTheta
            var z = cosTheta

            // Scale samples to cluster near origin
            var scale = i.toFloat() / size
            scale = lerp(0.1f, 1f, scale * scale)

            x *= scale
            y *= scale
            z *= scale

            kernel[i * 3] = x
            kernel[i * 3 + 1] = y
            kernel[i * 3 + 2] = z
        }
    }

    /**
     * Generate noise texture for randomizing sampling direction
     */
    private fun generateNoiseTexture() {
        val size = 4 // 4x4 noise texture
        val data = FloatArray(size * size * 4)

        for (i in 0 until size * size) {
            val phi = Random.nextFloat() * 2f * PI.toFloat()
            data[i * 4] = cos(phi)
            data[i * 4 + 1] = sin(phi)
            data[i * 4 + 2] = 0f
            data[i * 4 + 3] = 1f
        }

        noiseTexture = DataTexture(data, size, size)
        noiseTexture?.wrapS = TextureWrap.REPEAT
        noiseTexture?.wrapT = TextureWrap.REPEAT
    }

    private fun initMaterials() {
        // SSAO shader
        ssaoMaterial = ShaderMaterial(
            vertexShader = SSAO_VERTEX_SHADER,
            fragmentShader = SSAO_FRAGMENT_SHADER
        )

        // Normal rendering shader
        normalMaterial = ShaderMaterial(
            vertexShader = NORMAL_VERTEX_SHADER,
            fragmentShader = NORMAL_FRAGMENT_SHADER
        )

        // Blur shader
        blurMaterial = ShaderMaterial(
            vertexShader = BLUR_VERTEX_SHADER,
            fragmentShader = BLUR_FRAGMENT_SHADER
        )

        // Copy shader
        copyMaterial = ShaderMaterial(
            vertexShader = COPY_VERTEX_SHADER,
            fragmentShader = COPY_FRAGMENT_SHADER
        )

        // Depth visualization shader
        depthRenderMaterial = ShaderMaterial(
            vertexShader = COPY_VERTEX_SHADER,
            fragmentShader = DEPTH_FRAGMENT_SHADER
        )
    }

    private fun initRenderTargets() {
        val rtConfig = RenderTargetConfig(
            minFilter = TextureFilter.LINEAR,
            magFilter = TextureFilter.LINEAR,
            format = TextureFormat.RGBA,
            stencilBuffer = false
        )

        normalRenderTarget = RenderTarget(width, height, rtConfig.copy(depthBuffer = true))
        ssaoRenderTarget = RenderTarget(width, height, rtConfig)
        blurRenderTarget = RenderTarget(width, height, rtConfig)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    override fun dispose() {
        normalRenderTarget?.dispose()
        ssaoRenderTarget?.dispose()
        blurRenderTarget?.dispose()

        ssaoMaterial?.dispose()
        normalMaterial?.dispose()
        blurMaterial?.dispose()
        copyMaterial?.dispose()
        depthRenderMaterial?.dispose()

        noiseTexture?.dispose()
        fsQuad.dispose()

        super.dispose()
    }

    companion object {
        // Shader source code

        private const val SSAO_VERTEX_SHADER = """
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        private const val SSAO_FRAGMENT_SHADER = """
            uniform sampler2D tDiffuse;
            uniform sampler2D tNormal;
            uniform sampler2D tDepth;
            uniform sampler2D tNoise;
            uniform vec3 kernel[KERNEL_SIZE];
            uniform vec2 resolution;
            uniform float cameraNear;
            uniform float cameraFar;
            uniform mat4 cameraProjectionMatrix;
            uniform mat4 cameraInverseProjectionMatrix;
            uniform float kernelRadius;
            uniform float minDistance;
            uniform float maxDistance;
            
            varying vec2 vUv;
            
            float getDepth(vec2 screenPosition) {
                return texture2D(tDepth, screenPosition).x;
            }
            
            float getLinearDepth(vec2 screenPosition) {
                float depth = getDepth(screenPosition);
                float z = depth * 2.0 - 1.0;
                return (2.0 * cameraNear * cameraFar) / (cameraFar + cameraNear - z * (cameraFar - cameraNear));
            }
            
            vec3 getViewPosition(vec2 screenPosition, float depth) {
                vec4 clipSpacePosition = vec4(screenPosition * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 viewSpacePosition = cameraInverseProjectionMatrix * clipSpacePosition;
                return viewSpacePosition.xyz / viewSpacePosition.w;
            }
            
            vec3 getViewNormal(vec2 screenPosition) {
                return texture2D(tNormal, screenPosition).xyz * 2.0 - 1.0;
            }
            
            void main() {
                float depth = getDepth(vUv);
                if (depth >= 1.0) {
                    gl_FragColor = vec4(1.0);
                    return;
                }
                
                vec3 viewPosition = getViewPosition(vUv, depth);
                vec3 viewNormal = getViewNormal(vUv);
                
                // Random rotation from noise texture
                vec2 noiseScale = resolution / 4.0;
                vec3 random = texture2D(tNoise, vUv * noiseScale).xyz;
                
                // TBN matrix for hemisphere orientation
                vec3 tangent = normalize(random - viewNormal * dot(random, viewNormal));
                vec3 bitangent = cross(viewNormal, tangent);
                mat3 TBN = mat3(tangent, bitangent, viewNormal);
                
                float occlusion = 0.0;
                
                for (int i = 0; i < KERNEL_SIZE; i++) {
                    vec3 sampleDir = TBN * kernel[i];
                    vec3 samplePos = viewPosition + sampleDir * kernelRadius;
                    
                    // Project sample position to screen space
                    vec4 offset = cameraProjectionMatrix * vec4(samplePos, 1.0);
                    offset.xyz /= offset.w;
                    offset.xyz = offset.xyz * 0.5 + 0.5;
                    
                    // Sample depth at projected position
                    float sampleDepth = getLinearDepth(offset.xy);
                    
                    // Range check and accumulate
                    float rangeCheck = smoothstep(0.0, 1.0, kernelRadius / abs(viewPosition.z - sampleDepth));
                    float delta = samplePos.z - sampleDepth;
                    
                    if (delta > minDistance && delta < maxDistance) {
                        occlusion += rangeCheck;
                    }
                }
                
                occlusion = 1.0 - (occlusion / float(KERNEL_SIZE));
                gl_FragColor = vec4(vec3(occlusion), 1.0);
            }
        """

        private const val NORMAL_VERTEX_SHADER = """
            varying vec3 vNormal;
            void main() {
                vNormal = normalize(normalMatrix * normal);
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        private const val NORMAL_FRAGMENT_SHADER = """
            varying vec3 vNormal;
            void main() {
                gl_FragColor = vec4(normalize(vNormal) * 0.5 + 0.5, 1.0);
            }
        """

        private const val BLUR_VERTEX_SHADER = """
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        private const val BLUR_FRAGMENT_SHADER = """
            uniform sampler2D tDiffuse;
            uniform vec2 resolution;
            varying vec2 vUv;
            
            void main() {
                vec2 texelSize = 1.0 / resolution;
                float result = 0.0;
                
                // 4x4 box blur
                for (int x = -2; x <= 2; x++) {
                    for (int y = -2; y <= 2; y++) {
                        vec2 offset = vec2(float(x), float(y)) * texelSize;
                        result += texture2D(tDiffuse, vUv + offset).r;
                    }
                }
                
                result /= 25.0;
                gl_FragColor = vec4(vec3(result), 1.0);
            }
        """

        private const val COPY_VERTEX_SHADER = """
            varying vec2 vUv;
            void main() {
                vUv = uv;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        private const val COPY_FRAGMENT_SHADER = """
            uniform sampler2D tDiffuse;
            uniform sampler2D tSSAO;
            uniform int mode;
            varying vec2 vUv;
            
            void main() {
                vec4 color = texture2D(tDiffuse, vUv);
                if (mode == 1) {
                    // Multiply with SSAO
                    float ao = texture2D(tSSAO, vUv).r;
                    gl_FragColor = vec4(color.rgb * ao, color.a);
                } else {
                    gl_FragColor = color;
                }
            }
        """

        private const val DEPTH_FRAGMENT_SHADER = """
            uniform sampler2D tDepth;
            uniform float cameraNear;
            uniform float cameraFar;
            varying vec2 vUv;
            
            float linearizeDepth(float depth) {
                float z = depth * 2.0 - 1.0;
                return (2.0 * cameraNear * cameraFar) / (cameraFar + cameraNear - z * (cameraFar - cameraNear));
            }
            
            void main() {
                float depth = texture2D(tDepth, vUv).x;
                float linearDepth = linearizeDepth(depth) / cameraFar;
                gl_FragColor = vec4(vec3(linearDepth), 1.0);
            }
        """
    }
}

// Data texture for noise
class DataTexture(
    val data: FloatArray,
    val width: Int,
    val height: Int
) : Texture {
    override var wrapS: TextureWrap = TextureWrap.CLAMP
    override var wrapT: TextureWrap = TextureWrap.CLAMP
    override var needsUpdate: Boolean = true

    override fun dispose() {}
}
