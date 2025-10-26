import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    id("maven-publish")
}

// Ensure reliable repositories across all modules (mitigate central timeouts)
allprojects {
    repositories {
        maven("https://cache-redirector.jetbrains.com/maven-central")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Ensure Kotlin/Native commonization is enabled by default to keep IDE import healthy.
    // You can disable it explicitly by passing -PdisableNativeCommonization=true (useful for CI or offline JVM-only work).
    val disableNativeCommonization = (findProperty("disableNativeCommonization") as String?)?.toBoolean() == true
    tasks.matching { it.name == "commonizeNativeDistribution" }.configureEach {
        enabled = !disableNativeCommonization
    }
}

// Apply plugins to all subprojects
subprojects {
    apply(plugin = "org.jetbrains.dokka")

    if (name.startsWith("tools")) {
        group = "io.kreekt.tools"
        version = rootProject.version
    }
}

group = "io.kreekt"
version = "0.1.0-alpha01"

kotlin {
    // JVM Target
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    // JS Target
    js(IR) {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
        browser {
            testTask {
                enabled = false // Disabled: CI environment lacks Chrome/Chromium for headless testing
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
        }
    }

    // Android Target
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    // iOS Targets - Disabled on Windows
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    // macOS Targets - Disabled on Windows
    // macosX64()
    // macosArm64()

    // Windows Target - Disabled (not a primary target, stub implementations only)
    // mingwX64 {
    //     compilerOptions {
    //         freeCompilerArgs.add("-Xexpect-actual-classes")
    //     }
    // }

    // Linux Target - Disabled (not a primary target, stub implementations only)
    // linuxX64 {
    //     compilerOptions {
    //         freeCompilerArgs.add("-Xexpect-actual-classes")
    //     }
    // }

    // Configure source sets
    sourceSets {
        // Common source set
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.atomicfu)

                // Advanced 3D features dependencies
                implementation(libs.kotlinx.datetime)

                // Math and collections
                implementation(libs.kotlinx.collections.immutable)

                // Asset loading and compression
                implementation(libs.okio)

                // Production readiness validation dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.3.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
            }
        }

        // Desktop shared code (JVM)
        val jvmMain by getting {
            dependencies {
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.vulkan)
                implementation(libs.lwjgl.glfw)
                implementation(libs.lwjgl.shaderc)

                // Platform-specific LWJGL natives (T001: Feature 019)
                // Note: LWJGL requires platform natives for JVM execution
                val osName = System.getProperty("os.name").lowercase()
                val lwjglNatives = when {
                    osName.contains("win") -> "natives-windows"
                    osName.contains("linux") -> "natives-linux"
                    osName.contains("mac") || osName.contains("darwin") -> "natives-macos"
                    else -> "natives-linux" // Default fallback
                }

                // Core LWJGL natives
                runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-shaderc::$lwjglNatives")

                // Vulkan natives (note: Vulkan itself is a header-only library in LWJGL)
                // Native libraries are loaded from system (VK_ICD_FILENAMES, vulkan-1.dll, etc.)

                // Future Phase 3+ dependencies (see CLAUDE.md - Advanced Features):
                // Physics: Bullet physics integration (Phase 2-13, Physics section)
                // Asset loading: DRACO mesh compression support
                // Font loading: FreeType for text rendering geometry
            }
        }

    // Web/Browser
    val jsMain by getting {
        dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(npm("@webgpu/types", "0.1.40"))

            // Future Phase 3+ dependencies (see CLAUDE.md - Advanced Features):
            // Physics: Rapier physics engine integration (Phase 2-13, Physics section)
            // Asset loading: DRACO mesh compression support
            // Font loading: OpenType.js for text rendering
            // XR: WebXR polyfill for broad browser support
        }
    }

    val jsTest by getting {
        dependencies {
            implementation(kotlin("test"))
        }
    }

        // Native shared code - Disabled (not primary targets)
        // val nativeMain by creating {
        //     dependsOn(commonMain)
        // }

        // Apple shared code - Disabled on Windows
        // val appleMain by creating {
        //     dependsOn(nativeMain)
        // }

        // val iosX64Main by getting {
        //     dependsOn(appleMain)
        // }
        // val iosArm64Main by getting {
        //     dependsOn(appleMain)
        // }
        // val iosSimulatorArm64Main by getting {
        //     dependsOn(appleMain)
        // }
        // val macosX64Main by getting {
        //     dependsOn(appleMain)
        // }
        // val macosArm64Main by getting {
        //     dependsOn(appleMain)
        // }

        // Linux Target - Disabled
        // val linuxX64Main by getting {
        //     dependsOn(nativeMain)
        // }

        // Windows Target - Disabled
        // val mingwX64Main by getting {
        //     dependsOn(nativeMain)
        // }

        // Mobile shared code
        val androidMain by getting {
            dependencies {
                implementation("com.google.ar:core:1.44.0")
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.activity.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
            }
            kotlin.srcDir("src/androidMain/kotlin")
            kotlin.exclude("io/kreekt/xr/helpers/**")
            kotlin.exclude("io/kreekt/xr/ARCoreWrappers.kt")
            kotlin.exclude("io/kreekt/xr/XRSystem.android.kt")
        }
    }
}

// android {
android {
    val compileSdkVersion = libs.versions.androidCompileSdk.get().toInt()
    val minSdkVersion = libs.versions.androidMinSdk.get().toInt()

    compileSdk = compileSdkVersion
    namespace = "io.kreekt"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = minSdkVersion
        val enableValidation = (project.findProperty("vkEnableValidation") as? String)
            ?.toBooleanStrictOrNull() ?: false
        buildConfigField("boolean", "VK_ENABLE_VALIDATION", enableValidation.toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

// -----------------------------------------------------------------------------
// WGSL → SPIR-V compilation (Tint CLI)
// -----------------------------------------------------------------------------

val tintExecutableProvider = providers.gradleProperty("tintExecutable").orElse("tint")

val compileShaders = tasks.register("compileShaders") {
    group = "build"
    description = "Compile WGSL shaders to SPIR-V using the Tint CLI"

    notCompatibleWithConfigurationCache("Invokes external compiler via Exec")

    val wgslDir = layout.projectDirectory.dir("src/commonMain/resources/shaders")
    val spvDir = layout.projectDirectory.dir("src/jvmMain/resources/shaders")

    inputs.files(
        fileTree(wgslDir) {
            include("**/*.wgsl")
        }
    )
    outputs.dir(spvDir)

    doLast {
        val tintExecutable = tintExecutableProvider.get()
        val wgslFiles = wgslDir.asFile
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("wgsl", ignoreCase = true) }
            .toList()

        if (wgslFiles.isEmpty()) {
            logger.lifecycle("No WGSL shaders found under ${wgslDir.asFile}. Skipping Tint compilation.")
            return@doLast
        }

        spvDir.asFile.mkdirs()

        wgslFiles.forEach { wgslFile ->
            val outputFile = spvDir.file("${wgslFile.nameWithoutExtension}.spv").asFile
            logger.lifecycle("Compiling ${wgslFile.name} → ${outputFile.name}")

            val execResult = runCatching {
                project.exec {
                    commandLine(tintExecutable, "--format", "spirv", wgslFile.absolutePath, "-o", outputFile.absolutePath)
                }
            }

            if (execResult.isFailure) {
                logger.error(
                    """
                        Failed to run Tint compiler.
                        Ensure '$tintExecutable' is installed and available on PATH (override via -PtintExecutable=/path/to/tint).
                    """.trimIndent()
                )
                throw execResult.exceptionOrNull() ?: GradleException("Tint execution failed for ${wgslFile.name}.")
            }
        }
    }
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(compileShaders)
}

val androidShaderAssetsDir = layout.projectDirectory.dir("examples/triangle-android/src/main/assets/shaders")

val syncAndroidShaders = tasks.register<Sync>("syncAndroidShaders") {
    group = "build"
    description = "Copy compiled SPIR-V shaders into the Android assets directory"

    dependsOn(compileShaders)

    from(layout.projectDirectory.dir("src/jvmMain/resources/shaders")) {
        include("**/*.spv")
    }
    into(androidShaderAssetsDir)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ============================================================================
// Custom Tasks for Running Examples
// ============================================================================

tasks.register("runSimpleDemo", JavaExec::class) {
    group = "kreekt"
    description = "Run the simple KreeKt demo script"

    dependsOn("jvmMainClasses")
    classpath = files(
        configurations.getByName("jvmRuntimeClasspath"),
        kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs
    )
    mainClass.set("examples.SimpleMainKt")

    doFirst {
        println("🚀 Running KreeKt Simple Demo...")
        println("This will demonstrate core KreeKt functionality")
    }
}

tasks.register("listExamples") {
    group = "kreekt"
    description = "List all available KreeKt examples"

    doLast {
        println(
            """
🚀 KreeKt Examples Available:

📝 Simple Examples:
  ./gradlew listExamples                       - Show this help
  ./gradlew :examples:basic-scene:runJvm       - Run JVM example with LWJGL
  ./gradlew :examples:basic-scene:runJs        - Run JavaScript example in browser

🔧 Build Tasks:
  ./gradlew build                              - Build entire project
  ./gradlew :examples:basic-scene:build        - Build examples only

📊 Test Tasks:
  ./gradlew test                               - Run all tests
  ./gradlew :examples:basic-scene:test         - Run example tests

🌐 Platform-specific:
  ./gradlew compileKotlinJvm                   - Compile JVM target
  ./gradlew compileKotlinJs                    - Compile JavaScript target
  ./gradlew compileKotlinLinuxX64              - Compile Linux native
  ./gradlew compileKotlinMingwX64              - Compile Windows native

📖 Documentation:
  ./gradlew dokkaHtml                          - Generate API documentation

💡 Quick Start:
  1. ./gradlew build                           - Build everything
  2. ./gradlew :examples:basic-scene:runJvm    - Interactive 3D example
  3. ./gradlew :examples:basic-scene:runJs     - Web browser example

🎯 Direct file execution (requires kotlinc):
  kotlinc -script examples/simple-demo.kt     - Standalone demo script
        """.trimIndent()
        )
    }
}

tasks.register("quickStart") {
    group = "kreekt"
    description = "Quick start - build and run basic scene example"

    dependsOn("build")
    finalizedBy(":examples:basic-scene:runJvm")

    doLast {
        println("✅ KreeKt Quick Start Complete!")
    }
}

// Native tests disabled since native targets are not compiled
// tasks.named("linuxX64Test") {
//     enabled = false
// }
// tasks.named("mingwX64Test") {
//     enabled = false
// }
