import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "triangle.js"
            }
            testTask {
                enabled = false
            }
        }
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

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// ============================================================================
// Run Tasks for Examples
// ============================================================================

tasks.register("run", JavaExec::class) {
    group = "examples"
    description = "Run the JVM version of the triangle example"

    dependsOn("jvmMainClasses")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (jvmCompilation.runtimeDependencyFiles ?: files()) + jvmCompilation.output.allOutputs
    mainClass.set("MainKt")

    doFirst {
        println("🎮 Starting KreeKt Triangle Example (JVM)")
        println("Rendering first pixels via Vulkan backend")
    }
}

tasks.register("dev") {
    group = "examples"
    description = "Development mode - continuous build and run"

    dependsOn("wasmJsBrowserDevelopmentRun")

    doFirst {
        println("🔄 Starting development mode with hot reload")
    }
}

tasks.register("wasmJsBrowserRun") {
    group = "examples"
    description = "Run the WebAssembly triangle example in the browser"

    dependsOn("wasmJsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching KreeKt Triangle Example (WebAssembly)")
        println("Opening dev server - ensure a WebGPU capable browser is available")
    }
}
