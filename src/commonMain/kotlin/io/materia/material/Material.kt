package io.materia.material

import io.materia.core.math.Plane
import kotlin.random.Random
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * Base material class implementing the core Material interface
 * Three.js r180 compatible Material base class
 */
abstract class Material : io.materia.core.scene.Material {

    override val id: Int = nextId.getAndIncrement()
    val uuid: String = generateUUID()
    override var name: String = ""
    override var needsUpdate: Boolean = true
    override var visible: Boolean = true

    abstract val type: String

    // Rendering properties
    open var opacity: Float = 1f
    open var transparent: Boolean = false
    open var vertexColors: Boolean = false
    open var depthWrite: Boolean = true
    open var depthTest: Boolean = true
    open var side: Side = Side.FrontSide

    // Blending properties
    open var blending: Blending = Blending.NormalBlending
    open var blendSrc: BlendingFactor = BlendingFactor.SrcAlphaFactor
    open var blendDst: BlendingFactor = BlendingFactor.OneMinusSrcAlphaFactor
    open var blendEquation: BlendingEquation = BlendingEquation.AddEquation
    open var blendSrcAlpha: BlendingFactor? = null
    open var blendDstAlpha: BlendingFactor? = null
    open var blendEquationAlpha: BlendingEquation? = null
    open var premultipliedAlpha: Boolean = false

    // Depth properties
    open var depthFunc: DepthMode = DepthMode.LessEqualDepth

    // Stencil properties
    open var stencilWrite: Boolean = false
    open var stencilFunc: StencilFunc = StencilFunc.AlwaysStencilFunc
    open var stencilRef: Int = 0
    open var stencilWriteMask: Int = 0xFF
    open var stencilFuncMask: Int = 0xFF
    open var stencilFail: StencilOp = StencilOp.KeepStencilOp
    open var stencilZFail: StencilOp = StencilOp.KeepStencilOp
    open var stencilZPass: StencilOp = StencilOp.KeepStencilOp

    // Rendering control
    open var colorWrite: Boolean = true
    open var polygonOffset: Boolean = false
    open var polygonOffsetFactor: Float = 0f
    open var polygonOffsetUnits: Float = 0f

    // Clipping
    open var clippingPlanes: List<Plane>? = null
    open var clipIntersection: Boolean = false
    open var clipShadows: Boolean = false

    // Alpha properties
    open var alphaTest: Float = 0f
    open var alphaToCoverage: Boolean = false

    // Other properties
    open var dithering: Boolean = false
    open var precision: Precision? = null
    open var toneMapped: Boolean = true
    open var version: Int = 0

    // User data
    val userData: MutableMap<String, Any> = mutableMapOf()

    /**
     * Set material properties from a map
     */
    open fun setValues(values: Map<String, Any>) {
        values.forEach { (key, value) ->
            when (key) {
                "name" -> (value as? String)?.let { name = it }
                "opacity" -> (value as? Float)?.let { opacity = it }
                "transparent" -> (value as? Boolean)?.let { transparent = it }
                "visible" -> (value as? Boolean)?.let { visible = it }
                "side" -> (value as? Side)?.let { side = it }
                "blending" -> (value as? Blending)?.let { blending = it }
                "depthTest" -> (value as? Boolean)?.let { depthTest = it }
                "depthWrite" -> (value as? Boolean)?.let { depthWrite = it }
                "alphaTest" -> (value as? Float)?.let { alphaTest = it }
                "polygonOffset" -> (value as? Boolean)?.let { polygonOffset = it }
                "polygonOffsetFactor" -> (value as? Float)?.let { polygonOffsetFactor = it }
                "polygonOffsetUnits" -> (value as? Float)?.let { polygonOffsetUnits = it }
                "dithering" -> (value as? Boolean)?.let { dithering = it }
                "toneMapped" -> (value as? Boolean)?.let { toneMapped = it }
                // Additional properties can be handled by subclasses
            }
        }
    }

    /**
     * Clone this material (must be implemented by subclasses)
     */
    abstract fun clone(): Material

    /**
     * Copy common properties from another material
     */
    open fun copy(source: Material): Material {
        this.name = source.name
        this.needsUpdate = source.needsUpdate
        this.visible = source.visible
        this.opacity = source.opacity
        this.transparent = source.transparent
        this.vertexColors = source.vertexColors
        this.depthWrite = source.depthWrite
        this.depthTest = source.depthTest
        this.side = source.side
        this.blending = source.blending
        this.blendSrc = source.blendSrc
        this.blendDst = source.blendDst
        this.blendEquation = source.blendEquation
        this.blendSrcAlpha = source.blendSrcAlpha
        this.blendDstAlpha = source.blendDstAlpha
        this.blendEquationAlpha = source.blendEquationAlpha
        this.premultipliedAlpha = source.premultipliedAlpha
        this.depthFunc = source.depthFunc
        this.stencilWrite = source.stencilWrite
        this.stencilFunc = source.stencilFunc
        this.stencilRef = source.stencilRef
        this.stencilWriteMask = source.stencilWriteMask
        this.stencilFuncMask = source.stencilFuncMask
        this.stencilFail = source.stencilFail
        this.stencilZFail = source.stencilZFail
        this.stencilZPass = source.stencilZPass
        this.colorWrite = source.colorWrite
        this.polygonOffset = source.polygonOffset
        this.polygonOffsetFactor = source.polygonOffsetFactor
        this.polygonOffsetUnits = source.polygonOffsetUnits
        this.clippingPlanes = source.clippingPlanes
        this.clipIntersection = source.clipIntersection
        this.clipShadows = source.clipShadows
        this.alphaTest = source.alphaTest
        this.alphaToCoverage = source.alphaToCoverage
        this.dithering = source.dithering
        this.precision = source.precision
        this.toneMapped = source.toneMapped
        return this
    }

    /**
     * Dispose of this material
     */
    open fun dispose() {
        // Override in subclasses to clean up resources
    }

    companion object {
        private val nextId: AtomicInt = atomic(1)

        // Legacy side constants for backward compatibility
        @Deprecated("Use Side.FrontSide instead", ReplaceWith("Side.FrontSide"))
        const val FRONT_SIDE = 0

        @Deprecated("Use Side.BackSide instead", ReplaceWith("Side.BackSide"))
        const val BACK_SIDE = 1

        @Deprecated("Use Side.DoubleSide instead", ReplaceWith("Side.DoubleSide"))
        const val DOUBLE_SIDE = 2

        /**
         * Generate a simple UUID-like string for material identification
         */
        private fun generateUUID(): String {
            val chars = "0123456789abcdef"
            return buildString {
                repeat(8) { append(chars[Random.nextInt(16)]) }
                append('-')
                repeat(4) { append(chars[Random.nextInt(16)]) }
                append('-')
                append('4')
                repeat(3) { append(chars[Random.nextInt(16)]) }
                append('-')
                append(chars[8 + Random.nextInt(4)])
                repeat(3) { append(chars[Random.nextInt(16)]) }
                append('-')
                repeat(12) { append(chars[Random.nextInt(16)]) }
            }
        }
    }
}