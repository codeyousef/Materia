package io.kreekt.examples.forcegraph

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("🚀 KreeKt Force Graph (MVP placeholder)")
    println("=======================================")
    println("Bootstrapping harness…")

    ForceGraphApp.warmUp()

    println(ForceGraphApp.statusMessage())
    println()
    println("Next steps:")
    println(" - Connect to the MVP GPU + engine layers")
    println(" - Load baked layouts and apply mode toggles (TF-IDF / Semantic)")
}
