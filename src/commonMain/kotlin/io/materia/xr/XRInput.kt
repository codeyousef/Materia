/**
 * XR Input Implementation - Hand Tracking and Eye Gaze
 * Provides comprehensive hand joint tracking, eye gaze, and gesture recognition
 */
package io.materia.xr

import io.materia.xr.input.*

// Re-export all input types and classes
typealias DefaultXRHand = io.materia.xr.input.DefaultXRHand
typealias DefaultXRGaze = io.materia.xr.input.DefaultXRGaze
typealias HandGestureDetector = io.materia.xr.input.HandGestureDetector
typealias XRInputSourceManager = io.materia.xr.input.XRInputSourceManager
typealias XRInputCallback = io.materia.xr.input.XRInputCallback
typealias HandGesture = io.materia.xr.input.HandGesture
typealias Finger = io.materia.xr.input.Finger
typealias EyeOpenness = io.materia.xr.input.EyeOpenness
typealias PupilDilation = io.materia.xr.input.PupilDilation
typealias HandCalibrationData = io.materia.xr.input.HandCalibrationData
typealias EyeCalibrationData = io.materia.xr.input.EyeCalibrationData
typealias EyeTrackingData = io.materia.xr.input.EyeTrackingData

// All XR input functionality is now organized into focused modules:
// - HandTracking.kt: Hand joint tracking, finger curl calculations, pinch detection
// - EyeTracking.kt: Eye gaze tracking, convergence, openness, and pupil dilation
// - GestureRecognition.kt: Gesture detection algorithms for hands
// - InputSourceManager.kt: Unified input source management and callbacks
