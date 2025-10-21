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
                outputFileName = "force-graph.js"
            }
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
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

tasks.register("run", JavaExec::class) {
    group = "examples"
    description = "Run the JVM version of the Force Graph example (placeholder)"

    dependsOn("jvmMainClasses")
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = (compilation.runtimeDependencyFiles ?: files()) + compilation.output.allOutputs
    mainClass.set("io.kreekt.examples.forcegraph.MainKt")

    doFirst {
        println("🚀 Launching Force Graph placeholder on JVM")
        println("This stub will be replaced with the full demo during MVP development.")
    }
}

tasks.register("wasmJsBrowserRun") {
    group = "examples"
    description = "Run the Force Graph WebAssembly placeholder in the browser"

    dependsOn("wasmJsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching Force Graph placeholder (WebAssembly)")
        println("Dev server opening — force-directed visualization lands later in the MVP timeline.")
    }
}

tasks.register("dev") {
    group = "examples"
    description = "Development mode for Force Graph placeholder"

    dependsOn("wasmJsBrowserDevelopmentRun")

    doFirst {
        println("🔄 Starting Force Graph dev server (placeholder)")
    }
}
