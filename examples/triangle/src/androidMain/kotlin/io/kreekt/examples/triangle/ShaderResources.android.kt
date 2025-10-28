package io.kreekt.examples.triangle

actual suspend fun loadShaderResource(path: String): String {
    throw UnsupportedOperationException(
        "WGSL shader loading is not supported on Android; use precompiled SPIR-V assets instead."
    )
}
