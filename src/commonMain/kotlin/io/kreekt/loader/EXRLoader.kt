package io.kreekt.loader

import io.kreekt.texture.Texture2D

class EXRLoader : AssetLoader<Texture2D> {
    override suspend fun load(path: String): Texture2D {
        throw UnsupportedOperationException("EXR HDR image decoding is not bundled. Use preprocessed KTX or PNG textures.")
    }
}
