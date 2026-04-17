package io.materia.examples.benchmarks

import kotlinx.serialization.json.Json

object BenchmarkJson {
    val codec: Json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    val compactCodec: Json =
        Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
}
