package io.materia.layers

/**
 * Layer-based visibility system using a 32-bit bitmask.
 * Objects can be assigned to layers, and cameras/raycasters can filter which layers to render/test.
 * Compatible with Three.js Layers API.
 */
class Layers {
    /**
     * 32-bit bitmask representing which layers are enabled.
     * Each bit represents one layer (0-31).
     */
    var mask: Int = 1 // Default to layer 0

    /**
     * Set the layer mask to only include a single layer.
     *
     * @param layer Layer number (0-31)
     */
    fun set(layer: Int) {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        mask = 1 shl layer
    }

    /**
     * Enable a specific layer in the mask.
     *
     * @param layer Layer number (0-31)
     */
    fun enable(layer: Int) {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        mask = mask or (1 shl layer)
    }

    /**
     * Disable a specific layer in the mask.
     *
     * @param layer Layer number (0-31)
     */
    fun disable(layer: Int) {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        mask = mask and (1 shl layer).inv()
    }

    /**
     * Toggle a specific layer in the mask.
     *
     * @param layer Layer number (0-31)
     */
    fun toggle(layer: Int) {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        mask = mask xor (1 shl layer)
    }

    /**
     * Enable all layers.
     */
    fun enableAll() {
        mask = 0xFFFFFFFF.toInt()
    }

    /**
     * Disable all layers.
     */
    fun disableAll() {
        mask = 0
    }

    /**
     * Test if a specific layer is enabled.
     *
     * @param layer Layer number (0-31)
     * @return true if the layer is enabled
     */
    fun isEnabled(layer: Int): Boolean {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        return (mask and (1 shl layer)) != 0
    }

    /**
     * Test if this layer mask overlaps with another.
     * Used to determine if an object should be rendered by a camera.
     *
     * @param layers Another Layers object to test against
     * @return true if any layers overlap
     */
    fun test(layers: Layers): Boolean {
        return (mask and layers.mask) != 0
    }

    /**
     * Alias for test() - check if this layer mask intersects with another.
     *
     * @param layers Another Layers object to test against
     * @return true if any layers overlap
     */
    fun intersects(layers: Layers): Boolean {
        return test(layers)
    }

    /**
     * Test if a specific layer is enabled.
     *
     * @param layer Layer number (0-31)
     * @return true if the layer is enabled
     */
    fun test(layer: Int): Boolean {
        return testLayer(layer)
    }

    /**
     * Get the index of the first set bit (lowest enabled layer).
     *
     * @return Layer number of the first set bit, or -1 if no layers are enabled
     */
    fun firstSetBit(): Int {
        if (mask == 0) return -1
        return mask.countTrailingZeroBits()
    }

    /**
     * Test if this layer mask overlaps with a specific layer.
     *
     * @param layer Layer number to test (0-31)
     * @return true if the layer overlaps
     */
    fun testLayer(layer: Int): Boolean {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        return (mask and (1 shl layer)) != 0
    }

    /**
     * Set multiple layers at once from a list.
     *
     * @param layers List of layer numbers (0-31)
     */
    fun setFromList(layers: List<Int>) {
        mask = 0
        layers.forEach { enable(it) }
    }

    /**
     * Get a list of all enabled layer numbers.
     *
     * @return List of enabled layer numbers (0-31)
     */
    fun getEnabledLayers(): List<Int> {
        val layers = mutableListOf<Int>()
        for (i in 0..31) {
            if (isEnabled(i)) {
                layers.add(i)
            }
        }
        return layers
    }

    /**
     * Copy the mask from another Layers object.
     *
     * @param layers Source Layers object
     */
    fun copy(layers: Layers): Layers {
        mask = layers.mask
        return this
    }

    /**
     * Clone this Layers object.
     *
     * @return A new Layers object with the same mask
     */
    fun clone(): Layers {
        return Layers().copy(this)
    }

    /**
     * Combine with another layer mask using OR operation.
     *
     * @param layers Layers to combine with
     */
    fun union(layers: Layers): Layers {
        mask = mask or layers.mask
        return this
    }

    /**
     * Intersect with another layer mask using AND operation.
     *
     * @param layers Layers to intersect with
     */
    fun intersect(layers: Layers): Layers {
        mask = mask and layers.mask
        return this
    }

    /**
     * Remove layers from another mask using AND NOT operation.
     *
     * @param layers Layers to remove
     */
    fun subtract(layers: Layers): Layers {
        mask = mask and layers.mask.inv()
        return this
    }

    /**
     * XOR with another layer mask.
     *
     * @param layers Layers to XOR with
     */
    fun symmetricDifference(layers: Layers): Layers {
        mask = mask xor layers.mask
        return this
    }

    /**
     * Check if two layer masks are equal.
     *
     * @param other Another Layers object
     * @return true if masks are identical
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Layers) return false
        return mask == other.mask
    }

    override fun hashCode(): Int = mask

    override fun toString(): String {
        return "Layers(mask=${mask.toString(2).padStart(32, '0')}, enabled=${getEnabledLayers()})"
    }

    companion object {
        /**
         * Common layer presets
         */
        object Presets {
            /** Default visible layer */
            val DEFAULT = Layers().apply { set(0) }

            /** UI elements layer */
            val UI = Layers().apply { set(1) }

            /** Debug visualization layer */
            val DEBUG = Layers().apply { set(2) }

            /** Shadow casters layer */
            val SHADOWS = Layers().apply { set(3) }

            /** Post-processing excluded layer */
            val NO_POST_PROCESS = Layers().apply { set(4) }

            /** Reflection probe layer */
            val REFLECTIONS = Layers().apply { set(5) }

            /** Invisible to main camera */
            val HIDDEN = Layers().apply { disableAll() }

            /** Visible in all layers */
            val ALL = Layers().apply { enableAll() }

            /**
             * Create a custom preset from layer numbers.
             */
            fun custom(vararg layers: Int): Layers {
                return Layers().apply {
                    setFromList(layers.toList())
                }
            }
        }

        /**
         * Test if two layer masks overlap.
         * Static version for convenience.
         */
        fun testLayers(layers1: Layers, layers2: Layers): Boolean {
            return layers1.test(layers2)
        }

        /**
         * Combine multiple layer masks.
         */
        fun combine(vararg layers: Layers): Layers {
            val result = Layers()
            result.mask = layers.fold(0) { acc, layer -> acc or layer.mask }
            return result
        }
    }
}

/**
 * Extension for objects that have layers.
 */
interface LayeredObject {
    val layers: Layers
}

/**
 * Layer filter for rendering and raycasting.
 */
class LayerFilter(
    val layers: Layers = Layers()
) {
    /**
     * Test if an object should be processed based on its layers.
     */
    fun test(obj: LayeredObject): Boolean {
        return layers.test(obj.layers)
    }

    /**
     * Filter a list of objects based on layer visibility.
     */
    fun <T : LayeredObject> filter(objects: List<T>): List<T> {
        return objects.filter { test(it) }
    }
}

/**
 * Utility for managing layer assignments.
 */
object LayerManager {
    private val layerNames = mutableMapOf<Int, String>()
    private val namedLayers = mutableMapOf<String, Int>()

    /**
     * Register a named layer.
     */
    fun registerLayer(layer: Int, name: String) {
        require(layer in 0..31) { "Layer must be between 0 and 31" }
        layerNames[layer] = name
        namedLayers[name] = layer
    }

    /**
     * Get layer number by name.
     */
    fun getLayer(name: String): Int? = namedLayers[name]

    /**
     * Get layer name by number.
     */
    fun getLayerName(layer: Int): String? = layerNames[layer]

    /**
     * Create a Layers object from layer names.
     */
    fun fromNames(vararg names: String): Layers {
        val layers = Layers()
        names.forEach { name ->
            namedLayers[name]?.let { layers.enable(it) }
        }
        return layers
    }

    /**
     * Get all registered layer names.
     */
    fun getAllLayerNames(): Map<Int, String> = layerNames.toMap()

    /**
     * Clear all layer registrations.
     */
    fun clear() {
        layerNames.clear()
        namedLayers.clear()
    }

    init {
        // Register default layer names
        registerLayer(0, "default")
        registerLayer(1, "ui")
        registerLayer(2, "debug")
        registerLayer(3, "shadows")
        registerLayer(4, "no-post-process")
        registerLayer(5, "reflections")
    }
}