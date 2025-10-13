package io.kreekt.examples.voxelcraft

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * WorldStorage for localStorage persistence
 *
 * Handles saving and loading world state to browser localStorage.
 * Uses JSON serialization + RLE compression for chunk data.
 *
 * Storage key: "voxelcraft_world"
 * Target size: <10MB compressed
 *
 * Contract: storage-api.yaml
 * Research: research.md "LocalStorage Compression"
 */
class WorldStorage {

    /**
     * Save world state to localStorage
     *
     * Serializes world state to JSON and stores in localStorage.
     * Returns result with success flag and size in bytes.
     *
     * @param world VoxelWorld to save
     * @return SaveResult with success status and size
     */
    fun save(world: VoxelWorld): SaveResult {
        return try {
            val worldState = WorldState.from(world)
            val json = Json.encodeToString(worldState)

            // Attempt to save to localStorage
            localStorage[STORAGE_KEY] = json

            val sizeBytes = json.length
            logInfo("ðŸ’¾ World saved: ${sizeBytes / 1024}KB")

            SaveResult(
                success = true,
                sizeBytes = sizeBytes,
                error = null
            )
        } catch (e: Exception) {
            // Handle quota exceeded or other errors
            val errorMsg = when {
                e.message?.contains("quota", ignoreCase = true) == true ->
                    "Storage quota exceeded. Please clear old saves."

                else -> "Save failed: ${e.message}"
            }

            logError("âŒ Save error: $errorMsg")

            SaveResult(
                success = false,
                sizeBytes = 0,
                error = errorMsg
            )
        }
    }

    /**
     * Load world state from localStorage
     *
     * Retrieves saved JSON, deserializes to WorldState, and restores world.
     * Returns null if no save exists or data is corrupted.
     *
     * @return WorldState if found, null otherwise
     */
    fun load(): WorldState? {
        return try {
            val json = localStorage[STORAGE_KEY] ?: return null
            val worldState = Json.decodeFromString<WorldState>(json)

            logInfo("ðŸ“‚ World loaded: seed=${worldState.seed}")

            worldState
        } catch (e: Exception) {
            logError("âš ï¸ Load error: ${e.message}")
            null
        }
    }

    /**
     * Clear saved world state
     *
     * Removes saved data from localStorage.
     */
    fun clear() {
        localStorage.removeItem(STORAGE_KEY)
        logInfo("ðŸ—‘ï¸ World save cleared")
    }

    /**
     * Get current storage size
     *
     * @return StorageInfo with usage statistics
     */
    fun getSize(): StorageInfo {
        val json = localStorage[STORAGE_KEY]
        val usedBytes = json?.length ?: 0

        // localStorage typical limit: 5-10MB
        val availableBytes = STORAGE_LIMIT - usedBytes
        val percentUsed = (usedBytes.toDouble() / STORAGE_LIMIT * 100)

        return StorageInfo(
            usedBytes = usedBytes,
            availableBytes = availableBytes,
            percentUsed = percentUsed
        )
    }

    companion object {
        private const val STORAGE_KEY = "voxelcraft_world"
        private const val STORAGE_LIMIT = 10_000_000 // 10MB typical limit
    }
}

/**
 * Save result with success status and size
 */
data class SaveResult(
    val success: Boolean,
    val sizeBytes: Int,
    val error: String?
)

/**
 * Storage information with usage statistics
 */
data class StorageInfo(
    val usedBytes: Int,
    val availableBytes: Int,
    val percentUsed: Double
)

