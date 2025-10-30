package io.materia.examples.voxelcraft

import io.materia.core.math.Vector3
import kotlinx.browser.document
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import kotlin.math.cos
import kotlin.math.sin

/**
 * PlayerController for keyboard input and movement
 *
 * Handles WASD movement, spacebar/shift for vertical movement,
 * F key for flight toggle, and mouse clicks for block interaction.
 *
 * Movement is camera-relative (forward = direction player is facing).
 *
 * Data model: data-model.md Section 5 (Player movement)
 */
class PlayerController(
    private val player: Player,
    private val blockInteraction: BlockInteraction? = null
) {

    private val keysPressed = mutableSetOf<String>()
    private var moveSpeed = 5.0 // blocks per second
    private var flySpeed = 10.0 // blocks per second when flying

    init {
        setupKeyboardListeners()
        setupMouseListeners()
    }

    private fun setupKeyboardListeners() {
        document.addEventListener("keydown", { event ->
            (event as? KeyboardEvent)?.let { keyEvent ->
                val key = keyEvent.key.lowercase()
                handleKeyDown(key)
            }
        })

        document.addEventListener("keyup", { event ->
            (event as? KeyboardEvent)?.let { keyEvent ->
                val key = keyEvent.key.lowercase()
                handleKeyUp(key)
            }
        })
    }

    /**
     * Handle key press
     *
     * @param key Key pressed (lowercase)
     */
    fun handleKeyDown(key: String) {
        keysPressed.add(key)

        // Toggle flight with F key
        if (key == "f") {
            player.toggleFlight()
        }
    }

    /**
     * Handle key release
     *
     * @param key Key released (lowercase)
     */
    fun handleKeyUp(key: String) {
        keysPressed.remove(key)
    }

    private fun setupMouseListeners() {
        document.addEventListener("mousedown", { event ->
            (event as? MouseEvent)?.let { mouseEvent ->
                when (mouseEvent.button.toInt()) {
                    0 -> blockInteraction?.handleLeftClick() // Left click
                    2 -> blockInteraction?.handleRightClick() // Right click
                }
            }
        })
    }

    /**
     * Update player position based on input
     *
     * Called every frame with deltaTime.
     *
     * @param deltaTime Time since last frame (seconds)
     */
    fun update(deltaTime: Float) {
        val speed = if (player.isFlying) flySpeed else moveSpeed
        val distance = speed * deltaTime

        val yaw = player.rotation.y.toDouble()
        val forward =
            Vector3((-sin(yaw) * distance).toFloat(), 0.0f, (-cos(yaw) * distance).toFloat())
        val right = Vector3((cos(yaw) * distance).toFloat(), 0.0f, (-sin(yaw) * distance).toFloat())

        // WASD movement
        if (keysPressed.contains("w")) {
            player.move(forward)
        }
        if (keysPressed.contains("s")) {
            player.move(Vector3(-forward.x, 0.0f, -forward.z))
        }
        if (keysPressed.contains("a")) {
            player.move(Vector3(-right.x, 0.0f, -right.z))
        }
        if (keysPressed.contains("d")) {
            player.move(right)
        }

        // Vertical movement (flight or jump)
        if (player.isFlying) {
            if (keysPressed.contains(" ")) { // Spacebar - move up
                player.move(Vector3(0.0f, distance.toFloat(), 0.0f))
            }
            if (keysPressed.contains("shift")) { // Shift - move down
                player.move(Vector3(0.0f, -distance.toFloat(), 0.0f))
            }
        } else {
            // Jump with spacebar
            if (keysPressed.contains(" ")) {
                player.jump()
            }
            // Gravity is now handled in player.update()
        }
    }
}
