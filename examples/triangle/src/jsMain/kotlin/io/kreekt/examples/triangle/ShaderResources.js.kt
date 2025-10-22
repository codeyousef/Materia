package io.kreekt.examples.triangle

import kotlinx.browser.window
import kotlinx.coroutines.await

actual suspend fun loadShaderResource(path: String): String {
    val response = window.fetch(path).await()
    if (!response.ok) {
        error("Failed to load shader resource '$path' (status ${'$'}{response.status})")
    }
    return response.text().await()
}
