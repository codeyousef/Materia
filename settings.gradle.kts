pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
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
include(":examples:embedding-galaxy")
include(":examples:force-graph")
include(":examples:force-graph-android")
