package io.kreekt.loader

import io.kreekt.texture.Texture2D

class KTX2Loader : AssetLoader<Texture2D> {
    override suspend fun load(path: String): Texture2D {
        throw UnsupportedOperationException("KTX2 decoding requires GPU-specific transcoding and is not available yet.")
    }
}
