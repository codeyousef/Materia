package io.materia.examples.triangle

import io.materia.io.readTextResource

actual suspend fun loadShaderResource(path: String): String {
    return readTextResource(path)
}