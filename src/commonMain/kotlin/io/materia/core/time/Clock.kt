package io.materia.core.time

import io.materia.datetime.currentTimeMillis

/**
 * Lightweight high-resolution clock used by examples and animation helpers.
 */
class Clock {
    private var lastTimeMs: Long = currentTimeMillis()
    private var elapsedMs: Long = 0
    private var running = true

    fun start() {
        running = true
        lastTimeMs = currentTimeMillis()
    }

    fun stop() {
        if (running) {
            update()
            running = false
        }
    }

    fun reset() {
        elapsedMs = 0
        lastTimeMs = currentTimeMillis()
    }

    fun getDeltaSeconds(): Float {
        update()
        val delta = elapsedMs / 1000f
        elapsedMs = 0
        return delta
    }

    private fun update() {
        val now = currentTimeMillis()
        elapsedMs += (now - lastTimeMs)
        lastTimeMs = now
    }
}
