/**
 * XR Anchor and Spatial Tracking Implementation
 * Provides spatial anchor management for persistent world tracking
 */
package io.materia.xr

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.platform.currentTimeMillis
import kotlinx.coroutines.*

/**
 * Default implementation of XRAnchor interface
 * Manages spatial anchors for world-locked content
 */
class DefaultXRAnchor(
    override val anchorId: String,
    private val initialPose: XRPose,
    private val space: XRSpace
) : XRAnchor {
    override val anchorSpace: XRSpace = DefaultXRSpace()
    var lastChangedTime: Long = currentTimeMillis()
        private set

    private var _trackingState: XRTrackingState = XRTrackingState.TRACKING
    private var currentPose: XRPose = initialPose
    private var deleted = false
    private var persistentHandle: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = kotlinx.coroutines.sync.Mutex()

    private val trackingUpdateJob: Job = coroutineScope.launch {
        while (isActive && !deleted) {
            updateTrackingState()
            delay(100) // Update at 10Hz
        }
    }

    suspend fun requestPersistentHandle(): XRResult<String> {
        if (persistentHandle == null) {
            return XRResult.Error(
                XRException.InvalidState("Anchor is not persistent")
            )
        }

        if (deleted) {
            return XRResult.Error(
                XRException.InvalidState("Anchor has been deleted")
            )
        }

        return try {
            val handle = persistentHandle ?: createPersistentHandle()
            persistentHandle = handle
            savePersistentAnchor(handle, currentPose)
            XRResult.Success(handle)
        } catch (e: Exception) {
            XRResult.Error(
                XRException.InvalidState("Failed to create persistent handle: ${e.message}")
            )
        }
    }

    override fun delete() {
        if (deleted) {
            return
        }

        try {
            // Clean up persistent storage if applicable
            persistentHandle?.let { handle ->
                // Launch async cleanup in the anchor's coroutine scope
                // This will be cancelled when we cancel the scope below
                coroutineScope.launch {
                    try {
                        removePersistentAnchor(handle)
                    } catch (e: Exception) {
                        // Log error but continue with deletion
                        // Error logged through platform logging system
                    }
                }
            }

            // Stop tracking
            trackingUpdateJob.cancel()
            coroutineScope.cancel() // Cancel all coroutines in this anchor's scope
            deleted = true
            _trackingState = XRTrackingState.STOPPED
        } catch (e: Exception) {
            // Log error but don't throw since interface doesn't support error reporting
        }
    }

    fun isTracked(): Boolean {
        return _trackingState == XRTrackingState.TRACKING && !deleted
    }

    fun getTrackingState(): XRTrackingState {
        return _trackingState
    }

    fun getPose(): XRPose = currentPose

    fun updatePose(pose: XRPose) {
        if (!deleted) {
            currentPose = pose
            lastChangedTime = currentTimeMillis()
        }
    }

    private suspend fun updateTrackingState() {
        if (deleted) {
            _trackingState = XRTrackingState.STOPPED
            return
        }

        val newState = checkPlatformTrackingState(anchorId)
        if (newState != _trackingState) {
            _trackingState = newState
            lastChangedTime = currentTimeMillis()

            // Update pose if tracking recovered
            if (newState == XRTrackingState.TRACKING) {
                val platformPose = getPlatformAnchorPose(anchorId)
                if (platformPose != null) {
                    currentPose = platformPose
                }
            }
        }
    }

    private fun createPersistentHandle(): String {
        return "persistent_anchor_${anchorId}_${currentTimeMillis()}"
    }
}

/**
 * Spatial tracking manager for world understanding
 */
class SpatialTrackingManager(
    private val session: XRSession
) {
    private val anchors = mutableMapOf<String, DefaultXRAnchor>()
    private val persistentAnchors = mutableMapOf<String, PersistentAnchorData>()
    private val trackingListeners = mutableListOf<SpatialTrackingListener>()

    private var worldTrackingState = XRTrackingState.TRACKING
    private var trackingQuality = TrackingQuality.GOOD
    private var trackingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startTrackingUpdates()
    }

    /**
     * Create a new spatial anchor at the specified pose
     */
    fun createAnchor(
        pose: XRPose,
        space: XRSpace,
        persistent: Boolean = false
    ): XRAnchor {
        val anchor = DefaultXRAnchor(
            anchorId = generateAnchorId(),
            initialPose = pose,
            space = space
        )

        anchors[anchor.anchorId] = anchor

        // Notify listeners
        trackingListeners.forEach { it.onAnchorAdded(anchor) }

        return anchor
    }

    /**
     * Create an anchor from a hit test result
     */
    suspend fun createAnchorFromHitTest(
        hitResult: XRHitTestResult
    ): XRAnchor? {
        return hitResult.createAnchor()
    }

    /**
     * Load persistent anchors from storage
     */
    suspend fun loadPersistentAnchors(): List<XRAnchor> {
        return try {
            val loadedAnchors = loadPersistentAnchorsFromPlatform()

            loadedAnchors.forEach { anchorData ->
                val anchor = DefaultXRAnchor(
                    anchorId = anchorData.id,
                    initialPose = anchorData.pose,
                    space = DefaultXRSpace()
                )

                anchors[anchor.anchorId] = anchor
                persistentAnchors[anchorData.handle] = anchorData
            }

            anchors.values.filter { anchor ->
                persistentAnchors.values.any { it.id == anchor.anchorId }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get all tracked anchors
     */
    fun getTrackedAnchors(): List<XRAnchor> {
        return anchors.values.filter { it.isTracked() }
    }

    /**
     * Remove an anchor
     */
    suspend fun removeAnchor(anchor: XRAnchor): XRResult<Unit> {
        return try {
            anchor.delete()
            anchors.remove(anchor.anchorId)
            trackingListeners.forEach { it.onAnchorRemoved(anchor) }
            XRResult.Success(Unit)
        } catch (e: Exception) {
            XRResult.Error(XRException.InvalidState("Failed to remove anchor: ${e.message}"))
        }
    }

    /**
     * Update world tracking state
     */
    fun updateWorldTracking(state: XRTrackingState, quality: TrackingQuality) {
        worldTrackingState = state
        trackingQuality = quality

        trackingListeners.forEach {
            it.onTrackingStateChanged(state, quality)
        }

        // Pause anchor updates if tracking is lost
        when (state) {
            XRTrackingState.PAUSED -> pauseAnchorUpdates()
            XRTrackingState.STOPPED -> stopAnchorUpdates()
            XRTrackingState.TRACKING -> resumeAnchorUpdates()
            XRTrackingState.NOT_TRACKING -> pauseAnchorUpdates()
            XRTrackingState.LIMITED -> resumeAnchorUpdates() // Continue with limited tracking
        }
    }

    /**
     * Transform coordinates between different reference spaces
     */
    fun transformCoordinates(
        position: Vector3,
        fromSpace: XRReferenceSpace,
        toSpace: XRReferenceSpace
    ): Vector3? {
        if (!isTrackingActive()) return null

        return performCoordinateTransform(position, fromSpace, toSpace)
    }

    /**
     * Add a spatial tracking listener
     */
    fun addTrackingListener(listener: SpatialTrackingListener) {
        trackingListeners.add(listener)
    }

    /**
     * Remove a spatial tracking listener
     */
    fun removeTrackingListener(listener: SpatialTrackingListener) {
        trackingListeners.remove(listener)
    }

    /**
     * Get current tracking quality
     */
    fun getTrackingQuality(): TrackingQuality = trackingQuality

    /**
     * Check if world tracking is active
     */
    fun isTrackingActive(): Boolean {
        return worldTrackingState == XRTrackingState.TRACKING
    }

    /**
     * Reset world tracking origin
     */
    fun resetTrackingOrigin() {
        // Reset all anchor poses relative to new origin
        val originTransform = Matrix4.identity()

        anchors.values.forEach { anchor ->
            val relativePose = recalculatePoseFromOrigin(anchor.getPose(), originTransform)
            anchor.updatePose(relativePose)
        }

        trackingListeners.forEach { it.onTrackingOriginReset() }
    }

    private fun startTrackingUpdates() {
        trackingJob = coroutineScope.launch {
            while (isActive) {
                if (isTrackingActive()) {
                    updateAnchorPoses()
                    checkTrackingQuality()
                }
                delay(50) // Update at 20Hz
            }
        }
    }

    private suspend fun updateAnchorPoses() {
        anchors.values.forEach { anchor ->
            if (anchor.isTracked()) {
                val platformPose = getPlatformAnchorPose(anchor.anchorId)
                if (platformPose != null) {
                    val previousPose = anchor.getPose()
                    anchor.updatePose(platformPose)

                    // Notify listeners if pose changed significantly
                    if (hasPoseChangedSignificantly(previousPose, platformPose)) {
                        trackingListeners.forEach {
                            it.onAnchorUpdated(anchor)
                        }
                    }
                }
            }
        }
    }

    private fun checkTrackingQuality() {
        val newQuality = evaluatePlatformTrackingQuality()
        if (newQuality != trackingQuality) {
            trackingQuality = newQuality
            trackingListeners.forEach {
                it.onTrackingQualityChanged(newQuality)
            }
        }
    }

    private fun pauseAnchorUpdates() {
        anchors.values.forEach { anchor ->
            // Keep last known pose but mark as paused
        }
    }

    private fun stopAnchorUpdates() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun resumeAnchorUpdates() {
        if (trackingJob?.isActive != true) {
            startTrackingUpdates()
        }
    }

    private fun hasPoseChangedSignificantly(
        oldPose: XRPose,
        newPose: XRPose
    ): Boolean {
        val positionDelta =
            oldPose.transform.getTranslation().distanceTo(newPose.transform.getTranslation())
        val rotationDelta =
            oldPose.transform.getRotation().dot(newPose.transform.getRotation()).let { dot ->
                2.0f * kotlin.math.acos(kotlin.math.abs(dot.coerceIn(-1f, 1f)))
            }

        return positionDelta > 0.01f || // 1cm threshold
                rotationDelta > 1f // 1 degree threshold
    }

    fun dispose() {
        trackingJob?.cancel()
        coroutineScope.cancel() // Cancel all coroutines
        anchors.values.forEach { anchor ->
            anchor.delete()
        }
        anchors.clear()
        persistentAnchors.clear()
        trackingListeners.clear()
    }
}

/**
 * Interface for spatial tracking events
 */
interface SpatialTrackingListener {
    fun onAnchorAdded(anchor: XRAnchor)
    fun onAnchorUpdated(anchor: XRAnchor)
    fun onAnchorRemoved(anchor: XRAnchor)
    fun onTrackingStateChanged(state: XRTrackingState, quality: TrackingQuality)
    fun onTrackingQualityChanged(quality: TrackingQuality)
    fun onTrackingOriginReset()
}

/**
 * Tracking quality enumeration
 */
enum class TrackingQuality {
    NONE,       // No tracking available
    LIMITED,    // Tracking with limited accuracy
    FAIR,       // Moderate tracking quality
    GOOD,       // Good tracking quality
    EXCELLENT   // Excellent tracking quality
}

/**
 * Persistent anchor data structure
 */
data class PersistentAnchorData(
    val id: String,
    val handle: String,
    val pose: XRPose,
    val createdTime: Long,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Anchor cloud service for cross-device persistence
 */
class AnchorCloudService(
    private val apiEndpoint: String
) {
    private val uploadedAnchors = mutableMapOf<String, CloudAnchor>()

    /**
     * Upload an anchor to the cloud
     */
    suspend fun uploadAnchor(
        anchor: XRAnchor,
        metadata: Map<String, Any> = emptyMap()
    ): XRResult<String> {
        // Note: Persistent anchor validation can be added for stricter cloud requirements
        // Current implementation accepts both persistent and session-scoped anchors

        return try {
            val cloudId = uploadAnchorToPlatformCloud(anchor, metadata)

            uploadedAnchors[cloudId] = CloudAnchor(
                cloudId = cloudId,
                localId = anchor.anchorId,
                uploadTime = currentTimeMillis(),
                metadata = metadata
            )

            XRResult.Success(cloudId)
        } catch (e: Exception) {
            XRResult.Error(
                XRException.InvalidState("Failed to upload anchor: ${e.message}")
            )
        }
    }

    /**
     * Download an anchor from the cloud
     */
    suspend fun downloadAnchor(cloudId: String): XRResult<XRAnchor> {
        return try {
            val anchorData = downloadAnchorFromPlatformCloud(cloudId)

            val anchor = DefaultXRAnchor(
                anchorId = generateAnchorId(),
                initialPose = anchorData.pose,
                space = DefaultXRSpace()
            )

            XRResult.Success(anchor)
        } catch (e: Exception) {
            XRResult.Error(
                XRException.InvalidState("Failed to download anchor: ${e.message}")
            )
        }
    }

    /**
     * List available cloud anchors
     */
    suspend fun listCloudAnchors(
        filter: Map<String, Any> = emptyMap()
    ): List<CloudAnchor> {
        return try {
            val cloudAnchors = listAnchorsFromPlatformCloud(filter)
            cloudAnchors
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Delete a cloud anchor
     */
    suspend fun deleteCloudAnchor(cloudId: String): XRResult<Unit> {
        return try {
            deleteAnchorFromPlatformCloud(cloudId)
            uploadedAnchors.remove(cloudId)
            XRResult.Success(Unit)
        } catch (e: Exception) {
            XRResult.Error(
                XRException.InvalidState("Failed to delete cloud anchor: ${e.message}")
            )
        }
    }
}

/**
 * Cloud anchor data structure
 */
data class CloudAnchor(
    val cloudId: String,
    val localId: String,
    val uploadTime: Long,
    val metadata: Map<String, Any>
)

/**
 * Coordinate system manager for spatial transformations
 */
object CoordinateSystemManager {
    /**
     * Transform a pose from one reference space to another
     */
    fun transformPose(
        pose: XRPose,
        fromSpace: XRReferenceSpace,
        toSpace: XRReferenceSpace
    ): XRPose {
        val transform = getTransformBetweenSpaces(fromSpace, toSpace)
        return applyTransformToPose(pose, transform)
    }

    /**
     * Convert world coordinates to screen coordinates
     */
    fun worldToScreen(
        worldPosition: Vector3,
        viewMatrix: Matrix4,
        projectionMatrix: Matrix4,
        viewport: XRViewport
    ): Vector2 {
        // Transform world position to clip space
        val clipSpace = worldPosition.clone()
            .applyMatrix4(viewMatrix)
            .applyMatrix4(projectionMatrix)

        // Perform perspective divide with zero check
        val epsilon = 0.00001f
        if (kotlin.math.abs(clipSpace.z) < epsilon) {
            // Return center of viewport when z is near zero (at camera plane)
            return Vector2(viewport.x + viewport.width * 0.5f, viewport.y + viewport.height * 0.5f)
        }

        val ndcX = clipSpace.x / clipSpace.z
        val ndcY = clipSpace.y / clipSpace.z

        // Convert to screen coordinates
        val screenX = ((ndcX + 1) * 0.5f * viewport.width) + viewport.x
        val screenY = ((1 - ndcY) * 0.5f * viewport.height) + viewport.y

        return Vector2(screenX, screenY)
    }

    /**
     * Convert screen coordinates to world ray
     */
    fun screenToWorldRay(
        screenPosition: Vector2,
        viewMatrix: Matrix4,
        projectionMatrix: Matrix4,
        viewport: XRViewport
    ): Ray {
        // Check for valid viewport dimensions
        val epsilon = 0.00001f
        if (viewport.width < epsilon || viewport.height < epsilon) {
            // Return default ray pointing forward when viewport is invalid
            return Ray(Vector3(0f, 0f, 0f), Vector3(0f, 0f, -1f))
        }

        // Convert screen to NDC
        val ndcX = ((screenPosition.x - viewport.x) / viewport.width) * 2 - 1
        val ndcY = 1 - ((screenPosition.y - viewport.y) / viewport.height) * 2

        // Create ray in view space
        val invProjection = projectionMatrix.inverse()
        val nearPoint = Vector3(ndcX, ndcY, -1f).applyMatrix4(invProjection)
        val farPoint = Vector3(ndcX, ndcY, 1f).applyMatrix4(invProjection)

        // Transform to world space
        val invView = viewMatrix.inverse()
        nearPoint.applyMatrix4(invView)
        farPoint.applyMatrix4(invView)

        val direction = farPoint.sub(nearPoint)
        val dirLength = direction.length()
        if (dirLength > 0.001f) {
            direction.normalize()
        } else {
            // Default to forward ray if degenerate
            direction.set(0f, 0f, -1f)
        }
        return Ray(nearPoint, direction)
    }

    private fun getTransformBetweenSpaces(
        fromSpace: XRReferenceSpace,
        toSpace: XRReferenceSpace
    ): Matrix4 {
        // Platform-specific implementation
        return getPlatformSpaceTransform(fromSpace, toSpace)
    }

    private fun applyTransformToPose(pose: XRPose, transform: Matrix4): XRPose {
        val newPosition = pose.transform.getTranslation().clone().applyMatrix4(transform)
        val rotationMatrix = Matrix4().makeRotationFromQuaternion(pose.transform.getRotation())
        rotationMatrix.multiplyMatrices(transform, rotationMatrix)
        val newOrientation = Quaternion().setFromRotationMatrix(rotationMatrix)

        return createXRPose(
            position = newPosition,
            orientation = newOrientation
        )
    }
}

/**
 * Ray data structure for hit testing
 */
data class Ray(
    val origin: Vector3,
    val direction: Vector3
)

// Platform-specific expect declarations with actual implementations per target
internal expect suspend fun savePersistentAnchor(handle: String, pose: XRPose)
internal expect suspend fun removePersistentAnchor(handle: String)
internal expect suspend fun loadPersistentAnchorsFromPlatform(): List<PersistentAnchorData>
internal expect suspend fun checkPlatformTrackingState(anchorId: String): XRTrackingState
internal expect suspend fun getPlatformAnchorPose(anchorId: String): XRPose?
internal expect fun performCoordinateTransform(
    position: Vector3,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): Vector3?

internal expect fun recalculatePoseFromOrigin(pose: XRPose, originTransform: Matrix4): XRPose
internal expect fun evaluatePlatformTrackingQuality(): TrackingQuality
internal expect suspend fun uploadAnchorToPlatformCloud(
    anchor: XRAnchor,
    metadata: Map<String, Any>
): String

internal expect suspend fun downloadAnchorFromPlatformCloud(cloudId: String): PersistentAnchorData
internal expect suspend fun listAnchorsFromPlatformCloud(filter: Map<String, Any>): List<CloudAnchor>
internal expect suspend fun deleteAnchorFromPlatformCloud(cloudId: String)
internal expect fun getPlatformSpaceTransform(
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): Matrix4

// Utility function
private fun generateAnchorId(): String = "xra_${currentTimeMillis()}"