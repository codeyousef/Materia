package io.kreekt.examples.common

class Hud {
    private val lines = mutableListOf<String>()

    fun setLine(index: Int, text: String) {
        while (lines.size <= index) {
            lines += ""
        }
        lines[index] = text
        platformRender(lines)
    }

    fun clear() {
        lines.clear()
        platformRender(lines)
    }
}

expect fun platformRender(lines: List<String>)
