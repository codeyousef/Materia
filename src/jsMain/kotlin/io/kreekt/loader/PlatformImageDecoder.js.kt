package io.kreekt.loader

internal actual object PlatformImageDecoder {
    actual fun decode(bytes: ByteArray): DecodedImage {
        // Browser-side image decoding is inherently asynchronous. The loader's
        // core pipeline expects synchronous decoding so, by default, we provide
        // a harmless 1x1 fallback texture. Applications targeting the browser
        // should supply a custom AssetResolver/MaterialFactory that wires actual
        // async image decoding (e.g., HTMLImageElement + texImage2D).
        console.warn("GLTFLoader: Image decoding on JS target uses a 1x1 fallback. Provide a custom decoder for production use.")
        return DecodedImage(
            width = 1,
            height = 1,
            pixels = byteArrayOf(-1, -1, -1, -1)
        )
    }
}
