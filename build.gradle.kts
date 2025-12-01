import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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
        group = "io.materia.tools"
        version = rootProject.version
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

group = "io.materia"
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
            kotlin.exclude("io/materia/xr/helpers/**")
            kotlin.exclude("io/materia/xr/ARCoreWrappers.kt")
            kotlin.exclude("io/materia/xr/XRSystem.android.kt")
        }
    }
}

// android {
android {
    val compileSdkVersion = libs.versions.androidCompileSdk.get().toInt()
    val minSdkVersion = libs.versions.androidMinSdk.get().toInt()

    compileSdk = compileSdkVersion
    namespace = "io.materia"

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

abstract class CompileShadersTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val wgslDir: DirectoryProperty

    @get:OutputDirectory
    abstract val spvDir: DirectoryProperty

    @get:Input
    abstract val tintExecutable: Property<String>

    @get:Input
    abstract val nagaExecutable: Property<String>

    init {
        group = "build"
        description = "Compile WGSL shaders to SPIR-V using the Tint or Naga CLI"
        tintExecutable.convention("tint")
        nagaExecutable.convention("naga")
    }

    @TaskAction
    fun compile() {
        data class Compiler(
            val name: String,
            val executable: String,
            val detectionArgs: List<String>,
            val commandBuilder: (File, File) -> List<String>
        )

        fun commandAvailable(executable: String, args: List<String>): Boolean =
            runCatching {
                execOperations.exec {
                    commandLine(listOf(executable) + args)
                    isIgnoreExitValue = true
                }
                true  // Command executed successfully (any exit code means it exists)
            }.getOrDefault(false)

        val wgslRoot = wgslDir.get().asFile
        val spvRoot = spvDir.get().asFile

        val tintExec = tintExecutable.get()
        val nagaExec = nagaExecutable.get()
        val homeDir = System.getProperty("user.home")

        val nagaCandidates = buildList {
            add(nagaExec)
            if (homeDir != null) {
                add(File(homeDir, ".cargo/bin/naga").absolutePath)
            }
        }.distinct()

        val compiler = buildList {
            add(
                Compiler(
                    name = "Tint",
                    executable = tintExec,
                    detectionArgs = listOf("--help")  // Tint doesn't support --version
                ) { input, output ->
                    listOf(
                        tintExec,
                        "--format",
                        "spirv",
                        input.absolutePath,
                        "-o",
                        output.absolutePath
                    )
                }
            )
            nagaCandidates.forEach { executable ->
                add(
                    Compiler(
                        name = "Naga",
                        executable = executable,
                        detectionArgs = listOf("--version")
                    ) { input, output ->
                        listOf(executable, input.absolutePath, output.absolutePath)
                    }
                )
            }
        }.firstOrNull { candidate ->
            commandAvailable(candidate.executable, candidate.detectionArgs)
        } ?: throw GradleException(
            "WGSL compiler not available on PATH. Install Tint (https://dawn.googlesource.com/tint) " +
                    "or run `cargo install naga-cli`, then re-run with -PtintExecutable=/path/to/tint or " +
                    "-PnagaExecutable=/path/to/naga if needed."
        )

        logger.lifecycle("Using ${compiler.name} shader compiler at ${compiler.executable}")

        val wgslFiles = wgslRoot
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("wgsl", ignoreCase = true) }
            .toList()

        if (wgslFiles.isEmpty()) {
            logger.lifecycle("No WGSL shaders found under $wgslRoot. Skipping compilation.")
            return
        }

        spvRoot.mkdirs()

        wgslFiles.forEach { wgslFile ->
            val baseName = wgslFile.nameWithoutExtension
            if (baseName == "basic") {
                logger.lifecycle("Skipping ${wgslFile.name} (multi-entry shader compiled separately).")
                return@forEach
            }
            val outputFile = File(spvRoot, "$baseName.main.spv")
            logger.lifecycle("Compiling ${wgslFile.name} → ${outputFile.name}")

            runCatching {
                execOperations.exec {
                    commandLine(compiler.commandBuilder(wgslFile, outputFile))
                }
            }.onFailure { throwable ->
                logger.error(
                    "Failed to run ${compiler.name} compiler (${compiler.executable}). " +
                            "Ensure the executable is installed and available on PATH."
                )
                throw throwable
            }
        }
    }
}

val tintExecutableProvider = providers.gradleProperty("tintExecutable")
val nagaExecutableProvider = providers.gradleProperty("nagaExecutable")

val compileShaders = tasks.register<CompileShadersTask>("compileShaders") {
    wgslDir.set(layout.projectDirectory.dir("src/commonMain/resources/shaders"))
    spvDir.set(layout.projectDirectory.dir("src/jvmMain/resources/shaders"))

    tintExecutable.set(tintExecutableProvider.orElse("tint"))
    nagaExecutable.set(nagaExecutableProvider.orElse("naga"))
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(compileShaders)
}

val androidShaderAssetsDir = layout.buildDirectory.dir("generated/androidShaders")

val syncAndroidShaders = tasks.register<Sync>("syncAndroidShaders") {
    group = "build"
    description = "Copy compiled SPIR-V shaders into the Android assets directory"

    dependsOn(compileShaders)

    from(layout.projectDirectory.dir("src/jvmMain/resources/shaders")) {
        include("**/*.spv")
    }
    into(androidShaderAssetsDir.get().asFile.resolve("shaders"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

gradle.projectsEvaluated {
    project(":materia-gpu").extensions.configure<LibraryExtension>("android") {
        sourceSets.getByName("main").assets.srcDir(androidShaderAssetsDir)
    }
    project(":materia-gpu").tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(syncAndroidShaders)
    }
}

tasks.register("listExamples") {
    group = "materia"
    description = "List all available Materia examples"

    doLast {
        println(
            """
🚀 Materia Examples Available:

  • Triangle              → JVM `:examples:triangle:runJvm`
                             Web `:examples:triangle:runJs`
                             Android `:examples:triangle:runAndroid`
  • Embedding Galaxy      → JVM `:examples:embedding-galaxy:runJvm`
                             Web `:examples:embedding-galaxy:runJs`
                             Android `:examples:embedding-galaxy:runAndroid`
  • Force Graph           → JVM `:examples:force-graph:runJvm`
                             Web `:examples:force-graph:runJs`
                             Android `:examples:force-graph:runAndroid`
  • VoxelCraft            → JVM `:examples:voxelcraft:runJvm`
                             Web `:examples:voxelcraft:runJs`
                             Android `:examples:voxelcraft:runAndroid`

ℹ️  All `run*` tasks are grouped under Gradle's `run` task group:
    ./gradlew :examples:triangle:tasks --group run

🔧 Utilities:
  ./gradlew build                    - Full build (all targets + checks)
  ./gradlew test                     - Multiplatform unit tests
  ./gradlew koverHtmlReport          - Coverage report
  ./gradlew compileShaders           - WGSL → SPIR-V cache
  ./gradlew dokkaHtml                - API reference docs

📦 Standalone script:
  kotlinc -script examples/simple-demo.kt

🌐 Emulator tip:
  Use an x86_64 Android emulator with Vulkan graphics to exercise the Android demos.
        """.trimIndent()
        )
    }
}

tasks.register("quickStart") {
    group = "materia"
    description = "Quick start - build and run the Triangle JVM example"

    dependsOn("build")
    finalizedBy(":examples:triangle:runJvm")

    doLast {
        println("✅ Materia Quick Start Complete!")
    }
}

// Native tests disabled since native targets are not compiled
// tasks.named("linuxX64Test") {
//     enabled = false
// }
// tasks.named("mingwX64Test") {
//     enabled = false
// }
