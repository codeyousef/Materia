package io.kreekt.examples.triangle

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLPreElement

fun main() {
    val scope = MainScope()
    scope.launch {
        val example = TriangleExample()
        val log = example.boot()
        val message = log.pretty()

        println(message)

        val pre = (document.createElement("pre") as HTMLPreElement).apply {
            textContent = message
            style.fontFamily = "monospace"
            style.backgroundColor = "#111"
            style.color = "#50fa7b"
            style.padding = "16px"
            style.margin = "24px"
            style.borderRadius = "8px"
        }

        document.body?.appendChild(pre)
    }
}
