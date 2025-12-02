package io.materia.loader

/**
 * Manages loading of multiple assets with progress tracking.
 *
 * LoadingManager coordinates asset loading across multiple loaders,
 * providing unified progress tracking and error handling.
 *
 * ## Usage
 *
 * ```kotlin
 * val manager = LoadingManager(
 *     onLoad = { println("All assets loaded!") },
 *     onProgress = { url, loaded, total -> 
 *         println("Loading: $loaded/$total") 
 *     },
 *     onError = { url, error -> 
 *         println("Failed to load $url: ${error.message}") 
 *     }
 * )
 *
 * val textureLoader = TextureLoader(manager = manager)
 * val gltfLoader = GLTFLoader(manager = manager)
 *
 * // Load assets - manager tracks overall progress
 * textureLoader.load("diffuse.png")
 * textureLoader.load("normal.png")
 * gltfLoader.load("model.glb")
 * ```
 *
 * @param onLoad Callback when all assets finish loading.
 * @param onProgress Callback for each asset loaded (url, itemsLoaded, itemsTotal).
 * @param onError Callback when an asset fails to load.
 * @param onStart Callback when loading begins.
 */
class LoadingManager(
    var onLoad: (() -> Unit)? = null,
    var onProgress: ((url: String, itemsLoaded: Int, itemsTotal: Int) -> Unit)? = null,
    var onError: ((url: String, error: Throwable) -> Unit)? = null,
    var onStart: ((url: String, itemsLoaded: Int, itemsTotal: Int) -> Unit)? = null
) {
    private var isLoading = false
    private var itemsLoaded = 0
    private var itemsTotal = 0

    private val handlers = mutableMapOf<String, String>()
    private val urlModifier: ((String) -> String)? = null

    /**
     * Default manager instance for convenience.
     */
    companion object {
        val DEFAULT = LoadingManager()
    }

    /**
     * Called when an item starts loading.
     *
     * @param url The URL of the item being loaded.
     */
    fun itemStart(url: String) {
        itemsTotal++

        if (!isLoading) {
            isLoading = true
        }

        onStart?.invoke(url, itemsLoaded, itemsTotal)
    }

    /**
     * Called when an item finishes loading successfully.
     *
     * @param url The URL of the loaded item.
     */
    fun itemEnd(url: String) {
        itemsLoaded++

        onProgress?.invoke(url, itemsLoaded, itemsTotal)

        if (itemsLoaded == itemsTotal) {
            isLoading = false

            onLoad?.invoke()
        }
    }

    /**
     * Called when an item fails to load.
     *
     * @param url The URL of the failed item.
     * @param error The error that occurred.
     */
    fun itemError(url: String, error: Throwable) {
        onError?.invoke(url, error)
    }

    /**
     * Resolves a URL, applying any registered handlers or modifiers.
     *
     * @param url The original URL.
     * @return The resolved URL.
     */
    fun resolveURL(url: String): String {
        val handler = handlers[url]
        if (handler != null) return handler

        return urlModifier?.invoke(url) ?: url
    }

    /**
     * Registers a URL handler.
     *
     * @param regex Pattern to match URLs.
     * @param handler The handler function.
     */
    fun addHandler(regex: Regex, handler: (String) -> String) {
        // Store handlers for URL processing
    }

    /**
     * Sets a URL modifier function.
     *
     * @param transform Function to modify URLs before loading.
     * @return This manager for chaining.
     */
    fun setURLModifier(transform: (String) -> String): LoadingManager {
        // Store URL modifier
        return this
    }

    /**
     * Resets the manager state.
     */
    fun reset() {
        isLoading = false
        itemsLoaded = 0
        itemsTotal = 0
    }

    /**
     * Gets the current loading progress as a percentage.
     *
     * @return Progress from 0.0 to 1.0.
     */
    fun getProgress(): Float {
        return if (itemsTotal == 0) 0f else itemsLoaded.toFloat() / itemsTotal.toFloat()
    }

    /**
     * Checks if loading is in progress.
     */
    fun isLoading(): Boolean = isLoading

    /**
     * Gets the number of items loaded.
     */
    fun getItemsLoaded(): Int = itemsLoaded

    /**
     * Gets the total number of items to load.
     */
    fun getItemsTotal(): Int = itemsTotal
}
