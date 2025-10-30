package io.materia.examples.common

import java.util.concurrent.atomic.AtomicReference

private val lastRendered = AtomicReference<List<String>>(emptyList())

actual fun platformRender(lines: List<String>) {
    if (lastRendered.getAndSet(lines) != lines) {
        val joined = lines.joinToString(separator = " | ")
        println("[HUD] $joined")
    }
}
