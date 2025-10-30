package io.materia.xr.input

import io.materia.core.math.Vector3
import io.materia.xr.XRHandJoint

/**
 * Hand gesture recognition system.
 */

/**
 * Hand gesture detector
 */
class HandGestureDetector(
    private val hand: DefaultXRHand
) {
    private val gestureHistory = mutableListOf<HandGesture>()
    private val maxHistorySize = 30

    fun detectCurrentGestures(): List<HandGesture> {
        val gestures = mutableListOf<HandGesture>()

        // Static gestures
        if (isThumbsUp()) gestures.add(HandGesture.THUMBS_UP)
        if (isThumbsDown()) gestures.add(HandGesture.THUMBS_DOWN)
        if (isFist()) gestures.add(HandGesture.FIST)
        if (isOpenPalm()) gestures.add(HandGesture.OPEN_PALM)
        if (isPeaceSign()) gestures.add(HandGesture.PEACE)
        if (isOkSign()) gestures.add(HandGesture.OK)
        if (isPointingIndex()) gestures.add(HandGesture.POINT)
        if (isPinching()) gestures.add(HandGesture.PINCH)

        // Dynamic gestures using history
        updateHistory(gestures)
        if (isWaving()) gestures.add(HandGesture.WAVE)
        if (isSwipingLeft()) gestures.add(HandGesture.SWIPE_LEFT)
        if (isSwipingRight()) gestures.add(HandGesture.SWIPE_RIGHT)
        if (isSwipingUp()) gestures.add(HandGesture.SWIPE_UP)
        if (isSwipingDown()) gestures.add(HandGesture.SWIPE_DOWN)

        return gestures
    }

    private fun isThumbsUp(): Boolean {
        val thumbCurl = hand.getFingerCurl(Finger.THUMB)
        val indexCurl = hand.getFingerCurl(Finger.INDEX)
        val middleCurl = hand.getFingerCurl(Finger.MIDDLE)
        val ringCurl = hand.getFingerCurl(Finger.RING)
        val pinkyCurl = hand.getFingerCurl(Finger.PINKY)

        val thumbTip = hand.getJointPose(XRHandJoint.THUMB_TIP)?.transform?.getTranslation()
        val wrist = hand.getJointPose(XRHandJoint.WRIST)?.transform?.getTranslation()

        if (thumbTip == null || wrist == null) return false

        val thumbUp = thumbTip.y > wrist.y + 0.05f

        return thumbUp && thumbCurl < 0.3f &&
                indexCurl > 0.7f && middleCurl > 0.7f &&
                ringCurl > 0.7f && pinkyCurl > 0.7f
    }

    private fun isThumbsDown(): Boolean {
        val thumbCurl = hand.getFingerCurl(Finger.THUMB)
        val indexCurl = hand.getFingerCurl(Finger.INDEX)
        val middleCurl = hand.getFingerCurl(Finger.MIDDLE)
        val ringCurl = hand.getFingerCurl(Finger.RING)
        val pinkyCurl = hand.getFingerCurl(Finger.PINKY)

        val thumbTip = hand.getJointPose(XRHandJoint.THUMB_TIP)?.transform?.getTranslation()
        val wrist = hand.getJointPose(XRHandJoint.WRIST)?.transform?.getTranslation()

        if (thumbTip == null || wrist == null) return false

        val thumbDown = thumbTip.y < wrist.y - 0.05f

        return thumbDown && thumbCurl < 0.3f &&
                indexCurl > 0.7f && middleCurl > 0.7f &&
                ringCurl > 0.7f && pinkyCurl > 0.7f
    }

    private fun isFist(): Boolean {
        return Finger.values().all { finger ->
            hand.getFingerCurl(finger) > 0.8f
        }
    }

    private fun isOpenPalm(): Boolean {
        return Finger.values().all { finger ->
            hand.getFingerCurl(finger) < 0.2f
        }
    }

    private fun isPeaceSign(): Boolean {
        val indexCurl = hand.getFingerCurl(Finger.INDEX)
        val middleCurl = hand.getFingerCurl(Finger.MIDDLE)
        val ringCurl = hand.getFingerCurl(Finger.RING)
        val pinkyCurl = hand.getFingerCurl(Finger.PINKY)

        return indexCurl < 0.3f && middleCurl < 0.3f &&
                ringCurl > 0.7f && pinkyCurl > 0.7f
    }

    private fun isOkSign(): Boolean {
        val thumbIndexPinch = hand.getPinchStrength(Finger.INDEX)
        val middleCurl = hand.getFingerCurl(Finger.MIDDLE)
        val ringCurl = hand.getFingerCurl(Finger.RING)
        val pinkyCurl = hand.getFingerCurl(Finger.PINKY)

        return thumbIndexPinch > 0.8f &&
                middleCurl < 0.3f && ringCurl < 0.3f && pinkyCurl < 0.3f
    }

    private fun isPointingIndex(): Boolean {
        val indexCurl = hand.getFingerCurl(Finger.INDEX)
        val middleCurl = hand.getFingerCurl(Finger.MIDDLE)
        val ringCurl = hand.getFingerCurl(Finger.RING)
        val pinkyCurl = hand.getFingerCurl(Finger.PINKY)

        return indexCurl < 0.3f &&
                middleCurl > 0.7f && ringCurl > 0.7f && pinkyCurl > 0.7f
    }

    private fun isPinching(): Boolean {
        return hand.getPinchStrength(Finger.INDEX) > 0.8f
    }

    private fun isWaving(): Boolean {
        if (gestureHistory.size < maxHistorySize) return false

        val wristPositions = gestureHistory.mapNotNull {
            hand.getJointPose(XRHandJoint.WRIST)?.transform?.getTranslation()
        }

        if (wristPositions.size < 20) return false

        val xMovements = wristPositions.windowed(2).map { (p1, p2) ->
            p2.x - p1.x
        }

        val directionChanges = xMovements.windowed(2).count { (m1, m2) ->
            m1 * m2 < 0
        }

        return directionChanges >= 3
    }

    private fun isSwipingLeft(): Boolean = isSwipingInDirection(Vector3(-1f, 0f, 0f))
    private fun isSwipingRight(): Boolean = isSwipingInDirection(Vector3(1f, 0f, 0f))
    private fun isSwipingUp(): Boolean = isSwipingInDirection(Vector3(0f, 1f, 0f))
    private fun isSwipingDown(): Boolean = isSwipingInDirection(Vector3(0f, -1f, 0f))

    private fun isSwipingInDirection(direction: Vector3): Boolean {
        if (gestureHistory.size < 10) return false

        val recentPositions = gestureHistory.takeLast(10).mapNotNull {
            hand.getJointPose(XRHandJoint.WRIST)?.transform?.getTranslation()
        }

        if (recentPositions.size < 10) return false

        val firstPos = recentPositions.firstOrNull() ?: return false
        val lastPos = recentPositions.lastOrNull() ?: return false
        val movement = lastPos.clone().sub(firstPos)
        val movementLength = movement.length()
        val speed = movementLength / (10f / 60f)

        if (movementLength > 0.001f) {
            movement.normalize()
            return movement.dot(direction) > 0.7f && speed > 0.3f
        }
        return false
    }

    private fun updateHistory(currentGestures: List<HandGesture>) {
        gestureHistory.addAll(currentGestures)
        while (gestureHistory.size > maxHistorySize) {
            gestureHistory.removeAt(0)
        }
    }
}

/**
 * Hand gesture enumeration
 */
enum class HandGesture {
    FIST,
    OPEN_PALM,
    THUMBS_UP,
    THUMBS_DOWN,
    PEACE,
    OK,
    POINT,
    PINCH,
    WAVE,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    SWIPE_UP,
    SWIPE_DOWN,
    GRAB,
    RELEASE
}
