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
                devServer = devServer?.copy(
                    open = false,
                    port = 8081
                )
            }
            binaries.executable()
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
                implementation(project(":materia-gpu"))
                implementation(project(":materia-engine"))
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

tasks.register<JavaExec>("runJvm") {
    group = "run"
    description = "Run the JVM version of the triangle example"

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    
    dependsOn("jvmMainClasses")

    mainClass.set("io.materia.examples.triangle.MainKt")
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
        println("🎮 Starting Materia Triangle Example (JVM)")
        println("Bootstrapping GPU abstraction for MVP triangle")
    }
}

tasks.register("run") {
    group = "run"
    description = "Alias for `runJvm` to keep legacy scripts working"
    dependsOn("runJvm")
}

tasks.register("runJs") {
    group = "run"
    description = "Run the browser triangle example (WebGPU/WebGL)"

    dependsOn("jsBrowserDevelopmentRun")

    doFirst {
        println("🌐 Launching Materia Triangle Example (Browser)")
        println("Opening dev server - ensure a WebGPU capable browser is available")
    }
}

tasks.register("installDebug") {
    group = "run"
    description = "Install the Android debug build for the Triangle example"
    dependsOn(":examples:triangle-android:installDebug")
}

tasks.register("runAndroid") {
    group = "run"
    description =
        "Install and launch the Android Triangle example (delegates to :examples:triangle-android)"
    dependsOn(":examples:triangle-android:runAndroid")
    notCompatibleWithConfigurationCache("Delegates to Android install task")
}

android {
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    namespace = "io.materia.examples.triangle"

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
