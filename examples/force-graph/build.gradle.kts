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
                outputFileName = "force-graph.js"
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
                implementation(project(":materia-gpu"))
                implementation(project(":materia-engine"))
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

tasks.register<JavaExec>("runJvm") {
    group = "examples"
    description = "Run the JVM version of the Force Graph example"

    dependsOn("jvmMainClasses")
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (compilation.runtimeDependencyFiles ?: files()) + compilation.output.allOutputs
    mainClass.set("io.materia.examples.forcegraph.MainKt")
    jvmArgs("-Dorg.lwjgl.system.stackSize=512")

    doFirst {
        println("🚀 Launching Force Graph on JVM")
        println("Bootstrapping EngineRenderer + force-directed scene…")
    }
}

tasks.register("run") {
    group = "examples"
    description = "Alias for `runJvm` for backwards-compatible scripts"
    dependsOn("runJvm")
}

tasks.register("runJs") {
    group = "examples"
    description = "Run the Force Graph example in the browser"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching Force Graph (Browser / WebGPU)")
        println("Ensure a WebGPU-capable browser is available.")
    }
}

tasks.register("dev") {
    group = "examples"
    description = "Development mode for Force Graph (browser)"

    dependsOn("runJs")

    doFirst {
        println("🔄 Starting Force Graph dev server (WebGPU)")
    }
}

tasks.register("jsBrowserRun") {
    group = "examples"
    description = "Alias task for Force Graph browser run"
    dependsOn("runJs")
}

tasks.register("wasmJsBrowserRun") {
    group = "examples"
    description = "Alias task for Force Graph browser run (intended wasmJs entry point)"
    dependsOn("runJs")
}

tasks.register("runAndroid") {
    group = "examples"
    description = "Android target placeholder – no APK yet"
    doLast {
        println("📱 Force Graph Android build not implemented yet.")
        println("Track roadmap progress in docs/MVP_STATUS.md (Force Graph Android).")
    }
}
