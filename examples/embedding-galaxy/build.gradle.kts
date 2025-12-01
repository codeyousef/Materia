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
                devServer = devServer?.copy(
                    open = false,
                    port = 8080
                )
            }
            binaries.executable()
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
    group = "run"
    description = "Run the JVM version of the Embedding Galaxy example"

    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(jvmMain.compileKotlinTaskName)
    // dependsOn(jvmMain.processResourcesTaskName) // Not available in this version of KGP?

    mainClass.set("io.materia.examples.embeddinggalaxy.MainKt")
    jvmArgs("-Dorg.lwjgl.system.stackSize=8192")

    classpath = files(
        jvmMain.output.allOutputs,
        configurations.named("jvmRuntimeClasspath")
    )
    
    doFirst {
        println("🚀 Launching Embedding Galaxy on JVM")
        println("Bootstrapping EngineRenderer + instanced galaxy scene…")
    }
}

tasks.register("run") {
    group = "run"
    description = "Alias for `runJvm` to stay compatible with older scripts"
    dependsOn("runJvm")
}

tasks.register("runJs") {
    group = "run"
    description = "Run the Embedding Galaxy example in the browser"
    dependsOn("jsBrowserDevelopmentRun")
}

tasks.register("runAndroid") {
    group = "run"
    description = "Install and launch the Embedding Galaxy Android demo"
    dependsOn(":examples:embedding-galaxy-android:runAndroid")
    notCompatibleWithConfigurationCache("Delegates to Android install task")
}
