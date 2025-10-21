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

rootProject.name = "kreekt"

// Tool modules - Temporarily disabled on Windows due to compilation issues
// include(":tools:editor")
// include(":tools:profiler")
// include(":tools:tests")
// include(":tools:docs")
// include(":tools:cicd")
// include(":tools:api-server")

// Library modules (future expansion)
// kreekt-postprocessing needs architectural fixes - temporarily disabled
// include(":kreekt-postprocessing")

// Validation module
include(":kreekt-validation")

// Example utilities
include(":kreekt-examples-common")

// Core MVP modules
include(":kreekt-gpu")
include(":kreekt-engine")

// Example projects
include(":examples:triangle")
include(":examples:voxelcraft")
include(":examples:embedding-galaxy")
include(":examples:force-graph")
