package io.materia.examples.common

private var lastRendered: List<String> = emptyList()

actual fun platformRender(lines: List<String>) {
    if (lastRendered != lines) {
        lastRendered = lines.toList()
        println("[HUD] ${lines.joinToString(separator = " | ")}")
    }
}