import org.gradle.api.tasks.JavaExec
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
            commonWebpackConfig {
                outputFileName = "embedding-galaxy.js"
            }
            testTask {
                enabled = false
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kreekt-gpu"))
                implementation(project(":kreekt-engine"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.glfw)
                implementation(libs.lwjgl.vulkan)

                val osName = System.getProperty("os.name").lowercase()
                val lwjglNatives = when {
                    osName.contains("win") -> "natives-windows"
                    osName.contains("linux") -> "natives-linux"
                    osName.contains("mac") || osName.contains("darwin") -> "natives-macos"
                    else -> "natives-linux"
                }

                runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.register("run", JavaExec::class) {
    group = "examples"
    description = "Run the JVM version of the Embedding Galaxy example"

    dependsOn("jvmMainClasses")
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (compilation.runtimeDependencyFiles ?: files()) + compilation.output.allOutputs
    mainClass.set("io.kreekt.examples.embeddinggalaxy.MainKt")

    doFirst {
        println("🚀 Launching Embedding Galaxy on JVM")
        println("Bootstrapping EngineRenderer + instanced galaxy scene…")
    }
}

tasks.register("dev") {
    group = "examples"
    description = "Development mode for Embedding Galaxy (browser)"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🔄 Starting Embedding Galaxy dev server (WebGPU)")
    }
}

tasks.register("jsBrowserRun") {
    group = "examples"
    description = "Run the Embedding Galaxy example in the browser"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching Embedding Galaxy (Browser / WebGPU)")
        println("Ensure a WebGPU-capable browser is available.")
    }
}

tasks.register("wasmJsBrowserRun") {
    group = "examples"
    description = "Alias task for Embedding Galaxy browser run (intended wasmJs entry point)"
    dependsOn("jsBrowserDevelopmentRun")
    doFirst {
        println("🌐 (alias) Starting Embedding Galaxy via jsBrowserDevelopmentRun")
    }
}
