/**
 * RenderLoop - Animation loop management utility
 * 
 * Provides a clean API for managing animation loops with:
 * - Frame timing information (delta time, total time, frame count)
 * - Time scaling for slow motion / fast forward effects
 * - Pause/resume functionality
 * - Max delta time clamping to handle lag spikes
 * 
 * Usage:
 * ```kotlin
 * val loop = RenderLoop { frame ->
 *     effect.updateUniforms {
 *         set("time", frame.totalTime)
 *     }
 *     effect.render(context, encoder)
 * }
 * 
 * loop.timeScale = 0.5f  // Slow motion
 * loop.start()
 * ```
 * 
 * Note: The actual requestAnimationFrame integration is platform-specific.
 * Use simulateFrame() for testing or manual frame advancement.
 */
package io.materia.effects

/**
 * Information about the current frame
 */
data class FrameInfo(
    /** Time since last frame in seconds (affected by timeScale and pause) */
    val deltaTime: Float,
    /** Total elapsed time in seconds (affected by timeScale) */
    val totalTime: Float,
    /** Actual elapsed time in seconds (not affected by timeScale) */
    val realTime: Float,
    /** Total number of frames rendered */
    val frameCount: Long
) {
    /** Frames per second based on delta time */
    val fps: Float
        get() = if (deltaTime > 0f) 1f / deltaTime else 0f
}

/**
 * Manages animation loop with timing utilities
 * 
 * @property onFrame Callback invoked each frame with timing information
 */
class RenderLoop(
    private val onFrame: (frame: FrameInfo) -> Unit
) {
    /** Time scale multiplier (1.0 = normal, 0.5 = half speed, 2.0 = double speed) */
    var timeScale: Float = 1.0f
    
    /** Whether the loop is paused (time doesn't advance but frames still render) */
    var isPaused: Boolean = false
        private set
    
    /** Whether the loop is currently running */
    var isRunning: Boolean = false
        private set
    
    /** Maximum delta time to prevent large jumps after lag spikes */
    var maxDeltaTime: Float = 0.1f
    
    // Internal timing state
    private var _totalTime: Float = 0f
    private var _realTime: Float = 0f
    private var _frameCount: Long = 0
    
    /**
     * Start the render loop
     */
    fun start() {
        isRunning = true
    }
    
    /**
     * Stop the render loop
     */
    fun stop() {
        isRunning = false
    }
    
    /**
     * Pause time accumulation (frames still render)
     */
    fun pause() {
        isPaused = true
    }
    
    /**
     * Resume time accumulation
     */
    fun resume() {
        isPaused = false
    }
    
    /**
     * Reset all timing to initial state
     */
    fun reset() {
        _totalTime = 0f
        _realTime = 0f
        _frameCount = 0
    }
    
    /**
     * Simulate a frame with the given real delta time.
     * Useful for testing or manual frame advancement.
     * 
     * @param realDeltaTime Actual time elapsed since last frame in seconds
     */
    fun simulateFrame(realDeltaTime: Float) {
        _frameCount++
        
        // Clamp delta time to prevent huge jumps
        val clampedDelta = minOf(realDeltaTime, maxDeltaTime)
        
        // Update real time (always advances)
        _realTime += clampedDelta
        
        // Calculate scaled delta time
        val scaledDelta = if (isPaused) {
            0f
        } else {
            clampedDelta * timeScale
        }
        
        // Update total time
        _totalTime += scaledDelta
        
        // Create frame info and invoke callback
        val frame = FrameInfo(
            deltaTime = scaledDelta,
            totalTime = _totalTime,
            realTime = _realTime,
            frameCount = _frameCount
        )
        
        onFrame(frame)
    }
    
    /**
     * Get current timing state without advancing
     */
    fun currentFrame(): FrameInfo = FrameInfo(
        deltaTime = 0f,
        totalTime = _totalTime,
        realTime = _realTime,
        frameCount = _frameCount
    )
}
