package io.kreekt.examples.forcegraph

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLParagraphElement

fun main() {
    val scope = MainScope()
    scope.launch {
        ForceGraphApp.warmUp()

        val status = ForceGraphApp.statusMessage()
        println(status)

        val paragraph = document.createElement("p") as HTMLParagraphElement
        paragraph.textContent = status
        paragraph.style.fontFamily = "monospace"
        paragraph.style.padding = "16px"
        paragraph.style.backgroundColor = "#111"
        paragraph.style.color = "#f1fa8c"

        document.body?.appendChild(paragraph)

        window.alert(
            "Force Graph MVP placeholder loaded. Force-directed WebGPU renderer lands after GPU scaffolding."
        )
    }
}
