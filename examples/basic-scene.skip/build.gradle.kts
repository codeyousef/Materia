plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js(IR) {
        browser {
            binaries.executable()
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                // Production readiness validation testing
                implementation(project(":"))
            }
        }

        val jvmMain by getting {
            dependencies {
                // LWJGL for OpenGL/Vulkan
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.glfw)
                implementation(libs.lwjgl.opengl)
                implementation(libs.lwjgl.vulkan)

                // Platform-specific natives
                val lwjglVersion = libs.versions.lwjgl.get()
                val osName = System.getProperty("os.name").lowercase()
                val nativePlatform = when {
                    osName.contains("windows") -> "natives-windows"
                    osName.contains("linux") -> "natives-linux"
                    osName.contains("mac") -> "natives-macos"
                    else -> "natives-linux" // Default fallback
                }

                implementation("org.lwjgl:lwjgl:$lwjglVersion:$nativePlatform")
                implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion:$nativePlatform")
                implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion:$nativePlatform")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// ============================================================================
// Run Tasks for Examples
// ============================================================================

tasks.register("runJvm", JavaExec::class) {
    group = "examples"
    description = "Run the JVM version of the basic scene example"

    dependsOn("jvmMainClasses")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (jvmCompilation.runtimeDependencyFiles ?: files()) + jvmCompilation.output.allOutputs
    mainClass.set("MainKt")

    doFirst {
        println("🎮 Starting Materia Basic Scene Example (JVM)")
        println("This will demonstrate 3D scene creation with LWJGL backend")
    }
}

tasks.register("runSimple", JavaExec::class) {
    group = "examples"
    description = "Run the simple launcher (no complex setup needed)"

    dependsOn("jvmMainClasses")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = jvmCompilation.runtimeDependencyFiles ?: files()
    mainClass.set("SimpleMainKt")

    doFirst {
        println("🚀 Starting Materia Simple Launcher")
        println("This demonstrates core library functionality")
    }
}

tasks.register("runJs") {
    group = "examples"
    description = "Run the JavaScript version in browser"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Starting Materia Basic Scene Example (JavaScript)")
        println("This will open the example in your default browser")
    }
}

tasks.register("dev") {
    group = "examples"
    description = "Development mode - continuous build and run"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🔄 Starting development mode with hot reload")
    }
}