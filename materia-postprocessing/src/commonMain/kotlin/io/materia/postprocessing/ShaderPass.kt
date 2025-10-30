package io.materia.postprocessing

import io.materia.camera.OrthographicCamera
import io.materia.geometry.PlaneGeometry
import io.materia.material.ShaderMaterial
import io.materia.material.Material
import io.materia.material.Blending
import io.materia.core.math.Vector3
import io.materia.renderer.Renderer
import io.materia.renderer.RenderTarget
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.texture.Texture

/**
 * ShaderPass applies a custom shader to a full-screen quad.
 * Used for most post-processing effects that operate on the entire frame.
 *
 * @property shader The shader material or shader definition
 * @property textureID The uniform name for the input texture (default: "tDiffuse")
 */
class ShaderPass : Pass {

    /**
     * The material used for this shader pass.
     */
    val material: ShaderMaterial

    /**
     * The uniform name for the input texture.
     */
    var textureID: String = "tDiffuse"

    /**
     * The uniforms of the shader material for easy access.
     */
    val uniforms: MutableMap<String, Any?>
        get() = material.uniforms

    // Full-screen rendering components
    private val scene: Scene
    private val camera: OrthographicCamera
    private val mesh: Mesh
    private val fsQuad: FullScreenQuad

    /**
     * Creates a ShaderPass from a shader material.
     */
    constructor(shader: ShaderMaterial) : super() {
        material = shader.clone()
        material.uniforms[textureID] = null

        scene = Scene()
        camera = OrthographicCamera(-1f, 1f, 1f, -1f, 0f, 1f)
        scene.add(camera)

        val geometry = PlaneGeometry(2f, 2f)
        mesh = Mesh(geometry, material)
        scene.add(mesh)

        fsQuad = FullScreenQuad(material)
    }

    /**
     * Creates a ShaderPass from vertex and fragment shader code.
     */
    constructor(
        vertexShader: String,
        fragmentShader: String,
        uniforms: Map<String, Any?> = emptyMap()
    ) : this(
        ShaderMaterial().apply {
            this.vertexShader = vertexShader
            this.fragmentShader = fragmentShader
            this.uniforms.putAll(uniforms)
        }
    )

    override fun render(
        renderer: Renderer,
        writeBuffer: RenderTarget,
        readBuffer: RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    ) {
        // Set the input texture uniform
        material.uniforms[textureID] = readBuffer.texture

        // Handle quad rendering mode
        if (useFullScreenQuad()) {
            renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)

            // Clear if requested
            if (clear) {
                renderer.clear()
            }

            fsQuad.render(renderer)
        } else {
            // Legacy scene rendering mode
            renderer.setRenderTarget(if (renderToScreen) null else writeBuffer)

            // Clear if requested
            if (clear) {
                renderer.clear()
            }

            renderer.render(scene, camera)
        }
    }

    /**
     * Determines whether to use the full-screen quad for rendering.
     * Can be overridden for specific shader pass implementations.
     */
    protected open fun useFullScreenQuad(): Boolean = true

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        // Update any size-dependent uniforms
        material.uniforms["resolution"]?.let {
            if (it is FloatArray && it.size >= 2) {
                it[0] = width.toFloat()
                it[1] = height.toFloat()
            }
        }
    }

    override fun dispose() {
        material.dispose()
        geometry?.dispose()
        fsQuad.dispose()
        super.dispose()
    }

    private var geometry: PlaneGeometry? = null
}

/**
 * A full-screen quad used for post-processing shader passes.
 * Efficiently renders a screen-aligned quad without a full scene.
 */
class FullScreenQuad(
    private var material: ShaderMaterial? = null
) {

    private val camera = OrthographicCamera(-1f, 1f, 1f, -1f, 0f, 1f)
    private val geometry = createFullScreenGeometry()
    private var mesh: Mesh? = null

    /**
     * Renders the full-screen quad with the current or specified material.
     */
    fun render(renderer: Renderer, material: ShaderMaterial? = null) {
        val renderMaterial = material ?: this.material
        requireNotNull(renderMaterial) { "No material specified for FullScreenQuad rendering" }

        if (mesh?.material !== renderMaterial) {
            mesh?.dispose()
            mesh = Mesh(geometry, renderMaterial)
        }

        mesh?.let { m ->
            // Direct rendering without scene for efficiency
            renderer.renderObject(m, camera)
        }
    }

    /**
     * Sets the material for this full-screen quad.
     */
    fun setMaterial(material: ShaderMaterial) {
        this.material = material
        mesh?.material = material
    }

    /**
     * Disposes of resources used by the full-screen quad.
     */
    fun dispose() {
        geometry.dispose()
        mesh?.dispose()
        mesh = null
    }

    companion object {
        /**
         * Creates the geometry for a full-screen quad.
         * The quad covers the entire normalized device coordinate space.
         */
        private fun createFullScreenGeometry(): PlaneGeometry {
            return PlaneGeometry(2f, 2f).apply {
                // Ensure the quad is properly positioned for screen coverage
                translate(0f, 0f, 0f)
            }
        }
    }
}

/**
 * Extension function to render a single object without a scene.
 * Used for efficient full-screen quad rendering.
 */
private fun Renderer.renderObject(mesh: Mesh, camera: Camera) {
    // This is a simplified render path for a single object
    // The actual implementation would bypass scene graph traversal
    val tempScene = Scene().apply { add(mesh) }
    render(tempScene, camera)
}