package io.kreekt.loader

import io.kreekt.texture.Texture2D

class TGALoader : AssetLoader<Texture2D> {
    override suspend fun load(path: String): Texture2D {
        throw UnsupportedOperationException("TGA decoding is not implemented. Convert textures to PNG or KTX2.")
    }
}
