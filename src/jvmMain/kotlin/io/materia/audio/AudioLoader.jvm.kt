package io.materia.audio

actual class AudioLoader {
    actual fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        // JVM audio loading implementation
        try {
            val buffer = AudioBuffer()
            onLoad(buffer)
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Audio loading failed")
        }
    }

    actual suspend fun loadAsync(url: String): Result<AudioBuffer> {
        return try {
            Result.success(AudioBuffer())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}