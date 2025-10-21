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
                implementation(project(":kreekt-gpu"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
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
    mainClass.set("io.kreekt.examples.triangle.MainKt")

    doFirst {
        println("🎮 Starting KreeKt Triangle Example (JVM)")
        println("Bootstrapping GPU abstraction for MVP triangle")
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
