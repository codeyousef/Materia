package io.kreekt.examples.embeddinggalaxy

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLParagraphElement

fun main() {
    val scope = MainScope()
    scope.launch {
        EmbeddingGalaxyApp.warmUp()

        val status = EmbeddingGalaxyApp.statusMessage()
        println(status)

        val paragraph = document.createElement("p") as HTMLParagraphElement
        paragraph.textContent = status
        paragraph.style.fontFamily = "monospace"
        paragraph.style.padding = "16px"
        paragraph.style.backgroundColor = "#111"
        paragraph.style.color = "#8be9fd"

        document.body?.appendChild(paragraph)

        window.alert(
            "Embedding Galaxy MVP placeholder loaded. WebGPU scene arrives once GPU core is ready."
        )
    }
}
