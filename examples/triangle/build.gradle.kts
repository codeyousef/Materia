import org.gradle.api.JavaVersion
import org.gradle.api.tasks.JavaExec
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
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
                outputFileName = "triangle.js"
            }
            testTask {
                enabled = false
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kreekt-gpu"))
                implementation(project(":kreekt-engine"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":"))
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

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🔄 Starting development mode with hot reload")
    }
}

tasks.register("jsBrowserRun") {
    group = "examples"
    description = "Run the browser triangle example"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching KreeKt Triangle Example (Browser)")
        println("Opening dev server - ensure a WebGPU capable browser is available")
    }
}

tasks.register("wasmJsBrowserRun") {
    group = "examples"
    description = "Alias for Web/WASM browser run (dev server) for Triangle example"
    dependsOn("jsBrowserDevelopmentRun")
    doFirst {
        println("🌐 (alias) Starting Triangle example in browser via jsBrowserDevelopmentRun")
    }
}

tasks.register("installDebug") {
    group = "examples"
    description = "Install the Android debug build for the Triangle example"
    dependsOn(":examples:triangle-android:installDebug")
}

android {
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    namespace = "io.kreekt.examples.triangle"

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
