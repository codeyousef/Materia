package io.materia.xr.helpers

import com.google.ar.core.Anchor
import com.google.ar.core.Session
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper to manage cloud anchors for cross-device AR experiences
 */
class CloudAnchorManager {

    /**
     * Represents a cloud anchor operation result
     */
    sealed class CloudAnchorResult {
        data class Success(val cloudAnchorId: String) : CloudAnchorResult()
        data class Error(val message: String, val errorCode: Anchor.CloudAnchorState) :
            CloudAnchorResult()

        object InProgress : CloudAnchorResult()
    }

    /**
     * Cloud anchor state
     */
    data class CloudAnchorState(
        val cloudAnchorId: String,
        val anchor: Anchor,
        val state: Anchor.CloudAnchorState,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _cloudAnchors = MutableStateFlow<List<CloudAnchorState>>(emptyList())
    val cloudAnchors: StateFlow<List<CloudAnchorState>> = _cloudAnchors.asStateFlow()

    private val resultChannel = Channel<CloudAnchorResult>()
    private val pendingAnchors = mutableMapOf<Anchor, String>()

    /**
     * Host an anchor to the cloud
     */
    fun hostCloudAnchor(session: Session, anchor: Anchor, ttlDays: Int = 1): Anchor? {
        return try {
            val cloudAnchor = session.hostCloudAnchorWithTtl(anchor, ttlDays)
            if (cloudAnchor != null) {
                pendingAnchors[cloudAnchor] = ""
                addCloudAnchor(
                    CloudAnchorState(
                        cloudAnchorId = "",
                        anchor = cloudAnchor,
                        state = cloudAnchor.cloudAnchorState
                    )
                )
            }
            cloudAnchor
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve a cloud anchor by its ID
     */
    fun resolveCloudAnchor(session: Session, cloudAnchorId: String): Anchor? {
        return try {
            val cloudAnchor = session.resolveCloudAnchor(cloudAnchorId)
            if (cloudAnchor != null) {
                pendingAnchors[cloudAnchor] = cloudAnchorId
                addCloudAnchor(
                    CloudAnchorState(
                        cloudAnchorId = cloudAnchorId,
                        anchor = cloudAnchor,
                        state = cloudAnchor.cloudAnchorState
                    )
                )
            }
            cloudAnchor
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update cloud anchor states
     */
    fun updateCloudAnchorStates() {
        val updatedAnchors = _cloudAnchors.value.map { anchorState ->
            val currentState = anchorState.anchor.cloudAnchorState
            if (currentState != anchorState.state) {
                val updatedState = anchorState.copy(
                    state = currentState,
                    cloudAnchorId = when (currentState) {
                        Anchor.CloudAnchorState.SUCCESS -> {
                            anchorState.anchor.cloudAnchorId ?: anchorState.cloudAnchorId
                        }

                        else -> anchorState.cloudAnchorId
                    }
                )

                // Send result through channel if state changed
                when (currentState) {
                    Anchor.CloudAnchorState.SUCCESS -> {
                        val cloudId = anchorState.anchor.cloudAnchorId
                        if (cloudId != null) {
                            resultChannel.trySend(CloudAnchorResult.Success(cloudId))
                        }
                    }

                    Anchor.CloudAnchorState.ERROR_INTERNAL,
                    Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED,
                    Anchor.CloudAnchorState.ERROR_SERVICE_UNAVAILABLE,
                    Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED,
                    Anchor.CloudAnchorState.ERROR_HOSTING_DATASET_PROCESSING_FAILED,
                    Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND,
                    Anchor.CloudAnchorState.ERROR_RESOLVING_LOCALIZATION_NO_MATCH,
                    Anchor.CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_OLD,
                    Anchor.CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_NEW -> {
                        resultChannel.trySend(
                            CloudAnchorResult.Error(
                                getErrorMessage(currentState),
                                currentState
                            )
                        )
                    }

                    Anchor.CloudAnchorState.TASK_IN_PROGRESS -> {
                        resultChannel.trySend(CloudAnchorResult.InProgress)
                    }

                    else -> {}
                }

                updatedState
            } else {
                anchorState
            }
        }
        _cloudAnchors.value = updatedAnchors
    }

    /**
     * Clear a specific cloud anchor
     */
    fun clearCloudAnchor(anchor: Anchor) {
        anchor.detach()
        _cloudAnchors.value = _cloudAnchors.value.filter { it.anchor != anchor }
        pendingAnchors.remove(anchor)
    }

    /**
     * Clear all cloud anchors
     */
    fun clearAllCloudAnchors() {
        _cloudAnchors.value.forEach { it.anchor.detach() }
        _cloudAnchors.value = emptyList()
        pendingAnchors.clear()
    }

    /**
     * Get the last result from the result channel
     */
    suspend fun getLastResult(): CloudAnchorResult? {
        return resultChannel.tryReceive().getOrNull()
    }

    private fun addCloudAnchor(state: CloudAnchorState) {
        _cloudAnchors.value = _cloudAnchors.value + state
    }

    private fun getErrorMessage(state: Anchor.CloudAnchorState): String {
        return when (state) {
            Anchor.CloudAnchorState.ERROR_INTERNAL ->
                "Internal error occurred while processing cloud anchor"

            Anchor.CloudAnchorState.ERROR_NOT_AUTHORIZED ->
                "Not authorized to perform cloud anchor operation"

            Anchor.CloudAnchorState.ERROR_SERVICE_UNAVAILABLE ->
                "Cloud anchor service is unavailable"

            Anchor.CloudAnchorState.ERROR_RESOURCE_EXHAUSTED ->
                "Cloud anchor resource quota exhausted"

            Anchor.CloudAnchorState.ERROR_HOSTING_DATASET_PROCESSING_FAILED ->
                "Failed to process dataset for cloud anchor hosting"

            Anchor.CloudAnchorState.ERROR_CLOUD_ID_NOT_FOUND ->
                "Cloud anchor ID not found"

            Anchor.CloudAnchorState.ERROR_RESOLVING_LOCALIZATION_NO_MATCH ->
                "Could not localize to resolve cloud anchor"

            Anchor.CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_OLD ->
                "SDK version too old to resolve this cloud anchor"

            Anchor.CloudAnchorState.ERROR_RESOLVING_SDK_VERSION_TOO_NEW ->
                "SDK version too new, cloud anchor created with newer SDK"

            else -> "Unknown cloud anchor error"
        }
    }

    /**
     * Check if cloud anchors are supported
     */
    fun isCloudAnchorSupported(session: Session): Boolean {
        return try {
            // Try to enable cloud anchors in a test config to check support
            val config = session.config
            config.cloudAnchorMode = com.google.ar.core.Config.CloudAnchorMode.ENABLED
            true // If we got here without exception, it's supported
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enable cloud anchors in session config
     */
    fun enableCloudAnchors(session: Session): Boolean {
        return try {
            val config = session.config
            config.cloudAnchorMode = com.google.ar.core.Config.CloudAnchorMode.ENABLED
            session.configure(config)
            true
        } catch (e: Exception) {
            false
        }
    }
}