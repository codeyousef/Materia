package io.materia.xr.helpers

import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.InstantPlacementPoint.TrackingMethod
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper to manage instant placement for AR objects
 */
class InstantPlacementManager {

    /**
     * Represents an instant placement point with metadata
     */
    data class PlacementPoint(
        val id: String,
        val point: InstantPlacementPoint,
        val pose: Pose,
        val distance: Float,
        val trackingMethod: TrackingMethod,
        val trackingState: TrackingState,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _placementPoints = MutableStateFlow<List<PlacementPoint>>(emptyList())
    val placementPoints: StateFlow<List<PlacementPoint>> = _placementPoints.asStateFlow()

    private var placementIdCounter = 0
    private val pointsMap = mutableMapOf<String, InstantPlacementPoint>()

    /**
     * Check if instant placement is enabled
     */
    fun isInstantPlacementEnabled(session: Session): Boolean {
        return try {
            session.config.instantPlacementMode ==
                    com.google.ar.core.Config.InstantPlacementMode.LOCAL_Y_UP
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enable or disable instant placement
     */
    fun setInstantPlacementEnabled(session: Session, enabled: Boolean): Boolean {
        return try {
            val config = session.config
            config.instantPlacementMode = if (enabled) {
                com.google.ar.core.Config.InstantPlacementMode.LOCAL_Y_UP
            } else {
                com.google.ar.core.Config.InstantPlacementMode.DISABLED
            }
            session.configure(config)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create an instant placement point at screen coordinates
     */
    fun createInstantPlacement(
        frame: Frame,
        approximateDistanceMeters: Float,
        screenX: Float,
        screenY: Float
    ): PlacementPoint? {
        return try {
            val hitResults = frame.hitTestInstantPlacement(
                screenX,
                screenY,
                approximateDistanceMeters
            )

            if (hitResults.isNotEmpty()) {
                val hitResult = hitResults[0]
                val instantPlacementPoint = hitResult.trackable as? InstantPlacementPoint
                    ?: return null
                val id = "placement_${++placementIdCounter}"
                pointsMap[id] = instantPlacementPoint

                val placementPoint = PlacementPoint(
                    id = id,
                    point = instantPlacementPoint,
                    pose = instantPlacementPoint.getPose(),
                    distance = approximateDistanceMeters, // Use the provided distance
                    trackingMethod = instantPlacementPoint.getTrackingMethod(),
                    trackingState = instantPlacementPoint.getTrackingState()
                )

                addPlacementPoint(placementPoint)
                placementPoint
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create instant placement from a hit result
     */
    fun createInstantPlacementFromHit(
        hitResult: HitResult,
        approximateDistanceMeters: Float
    ): PlacementPoint? {
        return try {
            val instantPlacementPoint = hitResult.createAnchor()

            if (instantPlacementPoint != null) {
                val id = "placement_${++placementIdCounter}"

                // Note: This method doesn't return an InstantPlacementPoint
                // Return null since we can't create a PlacementPoint without it
                return null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Update all placement points with current tracking information
     */
    fun updatePlacementPoints() {
        val updatedPoints = _placementPoints.value.mapNotNull { placement ->
            try {
                val point = placement.point
                PlacementPoint(
                    id = placement.id,
                    point = point,
                    pose = point.getPose(),
                    distance = placement.distance, // Use stored distance
                    trackingMethod = point.getTrackingMethod(),
                    trackingState = point.getTrackingState(),
                    timestamp = placement.timestamp
                )
            } catch (e: Exception) {
                // Point may have been detached
                null
            }
        }
        _placementPoints.value = updatedPoints
    }

    /**
     * Remove a specific placement point
     */
    fun removePlacementPoint(id: String) {
        val point = pointsMap.remove(id)
        if (point != null) {
            // InstantPlacementPoint anchors are managed internally by ARCore
            _placementPoints.value = _placementPoints.value.filter { it.id != id }
        }
    }

    /**
     * Remove all placement points
     */
    fun clearAllPlacementPoints() {
        // InstantPlacementPoint anchors are managed internally by ARCore
        // No need to detach them manually
        pointsMap.clear()
        _placementPoints.value = emptyList()
    }

    /**
     * Get placement point by ID
     */
    fun getPlacementPoint(id: String): PlacementPoint? {
        return _placementPoints.value.find { it.id == id }
    }

    /**
     * Check if a placement point has converged to full tracking
     */
    fun hasConverged(id: String): Boolean {
        val point = getPlacementPoint(id)
        return point?.trackingMethod == TrackingMethod.FULL_TRACKING
    }

    /**
     * Get all converged placement points
     */
    fun getConvergedPoints(): List<PlacementPoint> {
        return _placementPoints.value.filter {
            it.trackingMethod == TrackingMethod.FULL_TRACKING
        }
    }

    /**
     * Get all placement points that are still approximate
     */
    fun getApproximatePoints(): List<PlacementPoint> {
        return _placementPoints.value.filter {
            it.trackingMethod == TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE
        }
    }

    private fun addPlacementPoint(point: PlacementPoint) {
        _placementPoints.value = _placementPoints.value + point
    }

    // Removed the synthetic InstantPlacementPoint class
    // Using ARCore's actual InstantPlacementPoint instead
}