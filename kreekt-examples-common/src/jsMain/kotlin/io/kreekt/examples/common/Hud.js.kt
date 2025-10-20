package io.kreekt.examples.common

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

private var hudElement: HTMLElement? = null

private fun ensureHudElement(): HTMLElement {
    val existing = hudElement
    if (existing != null) return existing

    val div = document.createElement("div") as HTMLElement
    div.id = "kreekt-example-hud"
    div.style.position = "absolute"
    div.style.top = "16px"
    div.style.left = "16px"
    div.style.color = "white"
    div.style.fontFamily = "monospace"
    div.style.fontSize = "12px"
    div.style.asDynamic().pointerEvents = "none"
    div.style.whiteSpace = "pre"
    document.body?.appendChild(div)
    hudElement = div
    return div
}

actual fun platformRender(lines: List<String>) {
    val element = ensureHudElement()
    element.textContent = lines.joinToString("\n")
}
