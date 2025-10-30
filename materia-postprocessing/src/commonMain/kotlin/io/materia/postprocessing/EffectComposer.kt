package io.materia.postprocessing

import io.materia.core.Disposable

import io.materia.core.*
import io.materia.core.math.*
import io.materia.renderer.*
import io.materia.core.scene.Scene
import io.materia.camera.Camera
import kotlinx.coroutines.flow.*

/**
 * EffectComposer manages a chain of post-processing passes applied to rendered scenes.
 * Implements ping-pong buffering for efficient multi-pass rendering.
 *
 * @property renderer The WebGPU/Vulkan renderer instance
 * @property renderTarget Optional custom render target (null = render to screen)
 */
class EffectComposer(
    val renderer: Renderer,
    renderTarget: RenderTarget? = null
) : Disposable {

    private val _passes = mutableListOf<Pass>()
    val passes: List<Pass> get() = _passes.toList()

    var renderTarget: RenderTarget? = renderTarget
        private set

    // Ping-pong buffers for multi-pass rendering
    var writeBuffer: RenderTarget
        private set
    var readBuffer: RenderTarget
        private set

    // Copy pass for final output
    private val copyPass: ShaderPass

    // Render state
    private var renderToScreen = false
    private var maskActive = false

    // Size tracking
    private var _size = Vector2(
        renderer.getSize().x.toFloat(),
        renderer.getSize().y.toFloat()
    )
    val size: Vector2 get() = _size.copy()

    // Performance tracking
    private val _renderTime = MutableStateFlow(0.0)
    val renderTime: StateFlow<Double> = _renderTime.asStateFlow()

    init {
        val width = _size.x.toInt()
        val height = _size.y.toInt()

        // Create render targets for ping-pong rendering
        val renderTargetOptions = RenderTargetOptions(
            minFilter = TextureFilter.Linear,
            magFilter = TextureFilter.Linear,
            format = TextureFormat.RGBA8,
            type = TextureDataType.UnsignedByte,
            depthBuffer = false,
            stencilBuffer = false,
            generateMipmaps = false
        )

        writeBuffer = WebGPURenderTarget(width, height, renderTargetOptions)
        readBuffer = WebGPURenderTarget(width, height, renderTargetOptions)

        // Create copy shader for final output
        copyPass = ShaderPass(CopyShader())
        copyPass.material.blending = Blending.None
    }

    /**
     * Swaps the read and write buffers for ping-pong rendering.
     */
    fun swapBuffers() {
        val temp = readBuffer
        readBuffer = writeBuffer
        writeBuffer = temp
    }

    /**
     * Adds a post-processing pass to the composer.
     * @param pass The pass to add
     */
    fun addPass(pass: Pass) {
        _passes.add(pass)
        pass.setSize(_size.x.toInt(), _size.y.toInt())
    }

    /**
     * Inserts a pass at the specified index.
     * @param pass The pass to insert
     * @param index The index to insert at
     */
    fun insertPass(pass: Pass, index: Int) {
        _passes.add(index, pass)
        pass.setSize(_size.x.toInt(), _size.y.toInt())
    }

    /**
     * Removes a pass from the composer.
     * @param pass The pass to remove
     * @return true if the pass was removed
     */
    fun removePass(pass: Pass): Boolean {
        return _passes.remove(pass)
    }

    /**
     * Removes the pass at the specified index.
     * @param index The index of the pass to remove
     * @return The removed pass
     */
    fun removePassAt(index: Int): Pass {
        return _passes.removeAt(index)
    }

    /**
     * Removes all passes from the composer.
     */
    fun removeAllPasses() {
        _passes.clear()
    }

    /**
     * Checks if the composer contains the given pass.
     * @param pass The pass to check for
     * @return true if the pass is in the composer
     */
    fun containsPass(pass: Pass): Boolean {
        return pass in _passes
    }

    /**
     * Updates the size of the composer and all passes.
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun setSize(width: Int, height: Int) {
        _size.set(width.toFloat(), height.toFloat())

        // Update render target sizes
        writeBuffer.setSize(width, height)
        readBuffer.setSize(width, height)

        // Update all passes
        for (pass in _passes) {
            pass.setSize(width, height)
        }
    }

    /**
     * Updates the pixel ratio for high-DPI displays.
     * @param pixelRatio The device pixel ratio
     */
    fun setPixelRatio(pixelRatio: Float) {
        renderer.setPixelRatio(pixelRatio)
        val size = renderer.getSize()
        setSize(size.x, size.y)
    }

    /**
     * Resets the composer state.
     */
    fun reset() {
        renderToScreen = false
        maskActive = false
        swapBuffers()
    }

    /**
     * Renders the scene through all enabled passes.
     * @param deltaTime Time since last frame in seconds
     */
    fun render(deltaTime: Float = 0.016f) {
        val startTime = performance.now()

        // Store current render target
        val currentRenderTarget = renderer.getRenderTarget()

        renderToScreen = false

        for ((index, pass) in _passes.withIndex()) {
            if (!pass.enabled) continue

            pass.renderToScreen = (renderTarget == null) && (index == _passes.size - 1)

            pass.render(
                renderer = renderer,
                writeBuffer = writeBuffer,
                readBuffer = readBuffer,
                deltaTime = deltaTime,
                maskActive = maskActive
            )

            if (pass.needsSwap) {
                if (maskActive) {
                    val context = renderer.getContext()
                    val state = renderer.state

                    state.buffers.stencil.setLocked(false)
                    state.buffers.stencil.setTest(false)

                    if (pass.renderToScreen) {
                        if (renderTarget != null) {
                            renderer.setRenderTarget(renderTarget)
                            copyPass.render(
                                renderer,
                                writeBuffer,
                                readBuffer,
                                deltaTime,
                                maskActive
                            )
                        }
                    } else {
                        // Copy to read buffer
                        copyPass.render(renderer, writeBuffer, readBuffer, deltaTime, maskActive)
                        swapBuffers()
                    }

                    state.buffers.stencil.setLocked(true)
                } else {
                    if (pass.renderToScreen) {
                        if (renderTarget != null) {
                            renderer.setRenderTarget(renderTarget)
                            copyPass.render(
                                renderer,
                                writeBuffer,
                                readBuffer,
                                deltaTime,
                                maskActive
                            )
                        }
                    } else {
                        swapBuffers()
                    }
                }
            }

            maskActive = pass is MaskPass
        }

        // Restore original render target
        renderer.setRenderTarget(currentRenderTarget)

        _renderTime.value = performance.now() - startTime
    }

    /**
     * Disposes of all resources used by the composer.
     */
    override fun dispose() {
        // Dispose all passes
        for (pass in _passes) {
            pass.dispose()
        }
        _passes.clear()

        // Dispose render targets
        writeBuffer.dispose()
        readBuffer.dispose()

        // Dispose copy pass
        copyPass.dispose()
    }
}

/**
 * Copy shader for final output or buffer copying.
 */
class CopyShader : ShaderMaterial() {
    init {
        vertexShader = """
            struct Uniforms {
                opacity: f32,
            }
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

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
            @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            @group(0) @binding(1) var tDiffuse: texture_2d<f32>;
            @group(0) @binding(2) var tDiffuseSampler: sampler;

            struct FragmentInput {
                @builtin(position) position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }

            @fragment
            fn main(input: FragmentInput) -> @location(0) vec4<f32> {
                let texel = textureSample(tDiffuse, tDiffuseSampler, input.uv);
                return vec4<f32>(texel.rgb, texel.a * uniforms.opacity);
            }
        """

        uniforms["tDiffuse"] = null
        uniforms["opacity"] = 1.0f
    }
}

/**
 * Platform-specific performance helper.
 */
expect object performance {
    fun now(): Double
}