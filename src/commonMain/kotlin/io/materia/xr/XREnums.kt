/**
 * XR Enumerations
 * All XR-related enum types for sessions, features, input, and tracking
 */
package io.materia.xr

/**
 * XR Session Modes
 */
enum class XRSessionMode {
    INLINE,
    IMMERSIVE_VR,
    IMMERSIVE_AR
}

/**
 * XR Session States
 */
enum class XRSessionState {
    IDLE,
    REQUESTING,
    ACTIVE,
    ENDING,
    ENDED
}

/**
 * XR Reference Space Types
 */
enum class XRReferenceSpaceType {
    VIEWER,
    LOCAL,
    LOCAL_FLOOR,
    BOUNDED_FLOOR,
    UNBOUNDED
}

/**
 * XR Features
 */
enum class XRFeature {
    VIEWER,
    LOCAL,
    LOCAL_FLOOR,
    BOUNDED_FLOOR,
    UNBOUNDED,
    ANCHORS,
    HIT_TEST,
    PLANE_DETECTION,
    MESH_DETECTION,
    FACE_TRACKING,
    IMAGE_TRACKING,
    OBJECT_TRACKING,
    HAND_TRACKING,
    EYE_TRACKING,
    DEPTH_SENSING,
    LIGHT_ESTIMATION,
    CAMERA_ACCESS
}

/**
 * Permission States
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    PROMPT
}

/**
 * XR Eye Types
 */
enum class XREye {
    NONE,
    LEFT,
    RIGHT
}

/**
 * XR Handedness
 */
enum class XRHandedness {
    NONE,
    LEFT,
    RIGHT
}

/**
 * XR Target Ray Mode
 */
enum class XRTargetRayMode {
    GAZE,
    TRACKED_POINTER,
    SCREEN
}

/**
 * XR Hand Joints
 */
enum class XRHandJoint {
    WRIST,
    THUMB_METACARPAL,
    THUMB_PHALANX_PROXIMAL,
    THUMB_PHALANX_DISTAL,
    THUMB_TIP,
    INDEX_FINGER_METACARPAL,
    INDEX_FINGER_PHALANX_PROXIMAL,
    INDEX_FINGER_PHALANX_INTERMEDIATE,
    INDEX_FINGER_PHALANX_DISTAL,
    INDEX_FINGER_TIP,
    MIDDLE_FINGER_METACARPAL,
    MIDDLE_FINGER_PHALANX_PROXIMAL,
    MIDDLE_FINGER_PHALANX_INTERMEDIATE,
    MIDDLE_FINGER_PHALANX_DISTAL,
    MIDDLE_FINGER_TIP,
    RING_FINGER_METACARPAL,
    RING_FINGER_PHALANX_PROXIMAL,
    RING_FINGER_PHALANX_INTERMEDIATE,
    RING_FINGER_PHALANX_DISTAL,
    RING_FINGER_TIP,
    PINKY_FINGER_METACARPAL,
    PINKY_FINGER_PHALANX_PROXIMAL,
    PINKY_FINGER_PHALANX_INTERMEDIATE,
    PINKY_FINGER_PHALANX_DISTAL,
    PINKY_FINGER_TIP
}

/**
 * XR Controller Buttons
 */
enum class XRControllerButton {
    TRIGGER,
    SQUEEZE,
    TOUCHPAD,
    THUMBSTICK,
    BUTTON_A,
    BUTTON_B,
    BUTTON_X,
    BUTTON_Y,
    MENU,
    SYSTEM
}

/**
 * XR Controller Axes
 */
enum class XRControllerAxis {
    TOUCHPAD_X,
    TOUCHPAD_Y,
    THUMBSTICK_X,
    THUMBSTICK_Y
}

/**
 * XR Hit Test Entity Types
 */
enum class XRHitTestEntityType {
    PLANE,
    POINT,
    MESH
}

/**
 * Plane Orientations
 */
enum class PlaneOrientation {
    HORIZONTAL_UP,
    HORIZONTAL_DOWN,
    VERTICAL,
    UNKNOWN
}

/**
 * XR Tracking States
 */
enum class XRTrackingState {
    NOT_TRACKING,
    TRACKING,
    LIMITED,
    PAUSED,
    STOPPED
}

/**
 * XR Depth Usage
 */
enum class XRDepthUsage {
    CPU_OPTIMIZED,
    GPU_OPTIMIZED
}

/**
 * XR Depth Data Formats
 */
enum class XRDepthDataFormat {
    LUMINANCE_ALPHA,
    RGBA32F
}

/**
 * XR Hit Test Types
 */
enum class XRHitTestType {
    PLANE,
    POINT,
    MESH,
    FEATURE_POINT
}
