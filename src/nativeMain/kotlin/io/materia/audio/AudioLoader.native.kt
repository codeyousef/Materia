package io.materia.audio

actual class AudioLoader {
    actual fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        // OpenAL audio loading implementation
        val buffer = AudioBuffer()
        onLoad(buffer)
    }

    actual suspend fun loadAsync(url: String): Result<AudioBuffer> {
        return Result.success(AudioBuffer())
    }
}
