package io.materia.core.scene

import io.materia.core.math.*

/**
 * Core Object3D functionality including identity, properties, and basic operations
 */

/**
 * Layer management for selective rendering
 */
data class Layers(var mask: Int = 1) {

    /**
     * Sets which layer this object belongs to
     */
    fun set(layer: Int) {
        mask = 1 shl layer
    }

    /**
     * Enables a layer
     */
    fun enable(layer: Int) {
        mask = mask or (1 shl layer)
    }

    /**
     * Disables a layer
     */
    fun disable(layer: Int) {
        mask = mask and (1 shl layer).inv()
    }

    /**
     * Toggles a layer
     */
    fun toggle(layer: Int) {
        mask = mask xor (1 shl layer)
    }

    /**
     * Tests if a layer is enabled
     */
    fun test(layers: Layers): Boolean {
        return (mask and layers.mask) != 0
    }

    /**
     * Tests if a specific layer is enabled
     */
    fun isEnabled(layer: Int): Boolean {
        return (mask and (1 shl layer)) != 0
    }

    /**
     * Enables all layers
     */
    fun enableAll() {
        mask = 0xffffffff.toInt()
    }

    /**
     * Disables all layers
     */
    fun disableAll() {
        mask = 0
    }
}

/**
 * Event system for Object3D
 */
sealed class Event {
    data class Added(val target: Object3D) : Event()
    data class Removed(val target: Object3D) : Event()
}

/**
 * ID generation for Object3D instances
 */
internal object Object3DIdGenerator {
    private var nextId = 1
    fun generateId(): Int = nextId++
}
