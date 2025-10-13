package io.kreekt.examples.voxelcraft

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.MouseEvent

/**
 * CameraController for mouse input and camera rotation
 *
 * Uses Pointer Lock API for FPS-style mouse control.
 * Mouse movement rotates camera, with pitch clamped to ±90°.
 *
 * Data model: data-model.md Section 5 (Player rotation)
 * Research: research.md "Input Handling Best Practices"
 */
class CameraController(
    private val player: Player,
    private val canvas: HTMLCanvasElement
) {

    private var mouseSensitivity = 0.0015 // radians per pixel (Minecraft-like feel)
    private var isPointerLocked = false

    init {
        setupPointerLock()
        setupMouseListeners()
    }

    private fun setupPointerLock() {
        // Request pointer lock on canvas click
        canvas.addEventListener("click", {
            canvas.requestPointerLockSafe()
        })

        // Listen for pointer lock changes
        document.addEventListener("pointerlockchange", {
            isPointerLocked = document.asDynamic().pointerLockElement == canvas
        })
    }

    private fun setupMouseListeners() {
        document.addEventListener("mousemove", { event ->
            if (isPointerLocked) {
                (event as? MouseEvent)?.let { handleMouseMove(it) }
            }
        })
    }

    /**
     * Handle mouse movement
     *
     * Updates camera rotation based on mouse delta.
     * Pitch is clamped to ±90° to prevent over-rotation.
     *
     * @param event MouseEvent with movementX and movementY
     */
    private fun handleMouseMove(event: MouseEvent) {
        val movementX = (event.asDynamic().movementX as? Double) ?: 0.0
        val movementY = (event.asDynamic().movementY as? Double) ?: 0.0

        handleMouseMove(movementX, movementY)
    }

    /**
     * Handle mouse movement (public for testing)
     *
     * @param movementX Horizontal mouse movement (pixels)
     * @param movementY Vertical mouse movement (pixels)
     */
    fun handleMouseMove(movementX: Double, movementY: Double) {
        val deltaYaw = -movementX * mouseSensitivity
        val deltaPitch = -movementY * mouseSensitivity  // Negate for standard FPS feel (mouse up = look up)

        player.rotate(deltaPitch, deltaYaw)
    }

    /**
     * Release pointer lock (for UI interactions)
     */
    fun releasePointerLock() {
        document.asDynamic().exitPointerLock()
    }
}
