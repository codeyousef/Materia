pluginManagement {
    repositories {
        // Prefer JetBrains cache redirectors to reduce timeouts and improve reliability
        maven("https://cache-redirector.jetbrains.com/maven-central")
        maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")

        // Upstream repos (kept for fallback/compat)
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        // Prefer JetBrains cache redirectors to reduce timeouts and improve reliability
        maven("https://cache-redirector.jetbrains.com/maven-central")
        maven("https://maven.pkg.jetbrains.com/kotlin/native") // Additional Kotlin mirror (best-effort)
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")

        // Upstream repos (kept for fallback/compat)
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "materia"

// Tool modules - Temporarily disabled on Windows due to compilation issues
// include(":tools:editor")
// include(":tools:profiler")
// include(":tools:tests")
// include(":tools:docs")
// include(":tools:cicd")
// include(":tools:api-server")

// Library modules (future expansion)
// materia-postprocessing needs architectural fixes - temporarily disabled
// include(":materia-postprocessing")

// Validation module
include(":materia-validation")

// Example utilities
include(":materia-examples-common")

// Core MVP modules
include(":materia-gpu")
include(":materia-gpu-android-native")
include(":materia-engine")

// Example projects
include(":examples:triangle")
include(":examples:triangle-android")
include(":examples:embedding-galaxy-android")
include(":examples:voxelcraft")
include(":examples:embedding-galaxy")
include(":examples:force-graph")
include(":examples:force-graph-android")
include(":examples:voxelcraft-android")
