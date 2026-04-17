plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

val hostOs = System.getProperty("os.name")

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

    if (!hostOs.startsWith("Windows", ignoreCase = true)) {
        val iosTargets = listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        )
        val macosTargets = listOf(
            macosX64(),
            macosArm64()
        )
        val appleTargets = iosTargets + macosTargets

        appleTargets.forEach { target ->
            target.binaries.framework {
                baseName = "MateriaTriangle"
                isStatic = false
                export(project(":"))
                export(project(":materia-gpu"))
                export(project(":materia-engine"))
            }
        }

        macosTargets.forEach { target ->
            target.binaries.executable {
                baseName = "materia-triangle"
                entryPoint = "io.materia.examples.triangle.main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":materia-gpu"))
                api(project(":materia-engine"))
                implementation(project(":materia-examples-common"))
                implementation(libs.kotlinx.coroutines.core)
                api(project(":"))
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

        if (!hostOs.startsWith("Windows", ignoreCase = true)) {
            val nativeMain by creating {
                dependsOn(commonMain)
            }

            val appleMain by creating {
                dependsOn(nativeMain)
            }

            val iosMain by creating {
                dependsOn(appleMain)
            }

            val macosMain by creating {
                dependsOn(appleMain)
            }

            val iosX64Main by getting {
                dependsOn(iosMain)
            }

            val iosArm64Main by getting {
                dependsOn(iosMain)
            }

            val iosSimulatorArm64Main by getting {
                dependsOn(iosMain)
            }

            val macosX64Main by getting {
                dependsOn(macosMain)
            }

            val macosArm64Main by getting {
                dependsOn(macosMain)
            }
        }
    }
}

if (hostOs.startsWith("Mac", ignoreCase = true)) {
    val hostArch = System.getProperty("os.arch")
    val macosTargetName = if (hostArch.contains("aarch64") || hostArch.contains("arm64")) {
        "MacosArm64"
    } else {
        "MacosX64"
    }

    tasks.register<Exec>("runMacos") {
        group = "run"
        description = "Run the macOS Apple Native version of the triangle example"
        dependsOn("linkDebugExecutable$macosTargetName")

        val executablePath = layout.buildDirectory.file(
            "bin/$macosTargetName/debugExecutable/materia-triangle.kexe"
        )
        commandLine(executablePath.get().asFile.absolutePath)

        doFirst {
            println("🍎 Launching Materia Triangle Example (macOS Native)")
            println("Bootstrapping MoltenVK through the Apple Native executable path")
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

tasks.register<JavaExec>("benchmarkJvm") {
    group = "benchmark"
    description = "Run measured JVM benchmark captures for the Triangle example"

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    dependsOn("jvmMainClasses")

    mainClass.set("io.materia.examples.triangle.TriangleBenchmarkKt")
    classpath = files(
        jvmMainCompilation.output.allOutputs,
        jvmMainCompilation.runtimeDependencyFiles
    )
    args(rootProject.file("docs/benchmarks/data/raw").absolutePath)
    jvmArgs(
        "-Dorg.lwjgl.system.stackSize=8192",
        "--enable-native-access=ALL-UNNAMED",
        "-Xmx2G",
        "-XX:+UseG1GC"
    )

    val java22Home = file("/usr/lib/jvm/java-22-openjdk")
    if (java22Home.exists()) {
        executable = file("$java22Home/bin/java").absolutePath
    }

    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("linux")) {
        val jemallocPath = "/usr/lib/libjemalloc.so"
        if (file(jemallocPath).exists()) {
            environment("LD_PRELOAD", jemallocPath)
        }
    }
}

tasks.register<Exec>("benchmarkWeb") {
    group = "benchmark"
    description = "Run measured Web benchmark captures for the Triangle example"
    dependsOn("jsBrowserProductionWebpack")
    notCompatibleWithConfigurationCache("Launches Chrome and a local benchmark server")
    doNotTrackState("Hardware-bound benchmark automation")
    commandLine(
        "node",
        rootProject.file("scripts/benchmarks/run_web_benchmark.mjs").absolutePath,
        "--dist-dir", layout.buildDirectory.get().asFile.absolutePath,
        "--raw-dir", rootProject.file("docs/benchmarks/data/raw").absolutePath,
        "--scene", "triangle",
        "--page", "benchmark.html"
    )
}

tasks.register<Exec>("benchmarkWebSmoke") {
    group = "verification"
    description = "Smoke-test Chrome benchmark automation for the Triangle example"
    dependsOn("jsBrowserProductionWebpack")
    notCompatibleWithConfigurationCache("Launches Chrome and a local benchmark server")
    doNotTrackState("Hardware-bound smoke automation")
    commandLine(
        "node",
        rootProject.file("scripts/benchmarks/run_web_benchmark.mjs").absolutePath,
        "--dist-dir", layout.buildDirectory.get().asFile.absolutePath,
        "--raw-dir", rootProject.file("build/tmp/benchmark-smoke").absolutePath,
        "--scene", "triangle-smoke",
        "--repeat-count", "1",
        "--timeout-ms", "120000",
        "--page", "benchmark.html"
    )
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

tasks.register("smokeAndroid") {
    group = "verification"
    description = "Run the Android triangle smoke test (delegates to :examples:triangle-android:smokeAndroid)"
    dependsOn(":examples:triangle-android:smokeAndroid")
    notCompatibleWithConfigurationCache("Delegates to Android smoke task")
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
