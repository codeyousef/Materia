package io.materia.examples.benchmarks

object BenchmarkConstants {
    const val readmeBeginMarker: String = "<!-- benchmark:begin -->"
    const val readmeEndMarker: String = "<!-- benchmark:end -->"

    val sceneOrder: List<String> = listOf("Triangle", "Embedding Galaxy", "Force Graph")
    val platformOrder: List<String> = listOf("JVM", "Web", "Android")

    val expectedMatrixKeys: Set<String> = sceneOrder.flatMap { scene ->
        platformOrder.map { platform -> "$scene::$platform" }
    }.toSet()
}
