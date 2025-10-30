package io.materia.audio

/**
 * Audio loader for asynchronous audio loading
 */
expect class AudioLoader {
    fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    suspend fun loadAsync(url: String): Result<AudioBuffer>
}