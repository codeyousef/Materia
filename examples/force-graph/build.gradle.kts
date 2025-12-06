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
                devServer = devServer?.copy(
                    open = false,
                    port = 8082
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
    description = "Run the JVM version of the Force Graph example"

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    
    dependsOn("jvmMainClasses")

    mainClass.set("io.materia.examples.forcegraph.MainKt")
    classpath = files(
        jvmMainCompilation.output.allOutputs,
        jvmMainCompilation.runtimeDependencyFiles
    )
    jvmArgs(
        "-Dorg.lwjgl.system.stackSize=8192",
        "--enable-native-access=ALL-UNNAMED",
        "-Xmx2G",
        "-XX:+UseG1GC"
    )
    
    // wgpu4k requires Java 22+ (FFM API, class file version 66.0)
    val java22Home = file("/usr/lib/jvm/java-22-openjdk")
    if (java22Home.exists()) {
        executable = file("$java22Home/bin/java").absolutePath
    }
    
    // Use jemalloc on Linux to work around wgpu4k memory management issues
    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("linux")) {
        val jemallocPath = "/usr/lib/libjemalloc.so"
        if (file(jemallocPath).exists()) {
            environment("LD_PRELOAD", jemallocPath)
        }
    }

    doFirst {
        println("🚀 Launching Force Graph on JVM")
        println("Bootstrapping EngineRenderer + force-directed scene…")
    }
}

tasks.register("run") {
    group = "run"
    description = "Alias for `runJvm` for backwards-compatible scripts"
    dependsOn("runJvm")
}

tasks.register("runJs") {
    group = "run"
    description = "Run the Force Graph example in the browser"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching Force Graph (Browser / WebGPU)")
        println("Ensure a WebGPU-capable browser is available.")
    }
}

tasks.register("runAndroid") {
    group = "run"
    description = "Install and launch the Force Graph Android demo"
    dependsOn(":examples:force-graph-android:runAndroid")
    notCompatibleWithConfigurationCache("Delegates to Android install task")
}
