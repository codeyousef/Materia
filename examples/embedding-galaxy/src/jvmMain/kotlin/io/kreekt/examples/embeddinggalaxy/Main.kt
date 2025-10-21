package io.kreekt.examples.embeddinggalaxy

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("🚀 KreeKt Embedding Galaxy (MVP placeholder)")
    println("===========================================")
    println("Preparing scene bootstrap…")

    EmbeddingGalaxyApp.warmUp()

    println(EmbeddingGalaxyApp.statusMessage())
    println()
    println("Next steps:")
    println(" - Wire GPU + engine layers once the MVP core lands")
    println(" - Replace this placeholder with the real instanced points demo")
}
