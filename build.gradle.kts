import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.security.MessageDigest
import java.util.*

// Apply version management - sets project.version and project.group from version.properties
apply(from = "version.gradle.kts")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
    id("maven-publish")
    id("signing")
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

// Version and group are set by version.gradle.kts from version.properties

// Load properties from local.properties if it exists
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(localFile.inputStream())
    }
}

// Maven Publishing Configuration
publishing {
    publications.withType<MavenPublication> {
        // Set platform-specific artifact IDs
        when (name) {
            "kotlinMultiplatform" -> artifactId = "materia"
            "jvm" -> artifactId = "materia-jvm"
            "js" -> artifactId = "materia-js"
            "androidRelease", "androidDebug" -> artifactId = "materia-android"
            else -> artifactId = "materia-${name.lowercase()}"
        }
        pom {
            name.set("Materia")
            description.set("Kotlin Multiplatform 3D rendering engine targeting WebGPU, Vulkan, and Metal with Three.js-style ergonomics")
            url.set("https://github.com/codeyousef/Materia")
            
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            
            developers {
                developer {
                    id.set("codeyousef")
                    name.set("Yousef")
                    url.set("https://github.com/codeyousef")
                }
            }
            
            scm {
                url.set("https://github.com/codeyousef/Materia")
                connection.set("scm:git:git://github.com/codeyousef/Materia.git")
                developerConnection.set("scm:git:ssh://git@github.com/codeyousef/Materia.git")
            }
            
            // Fix missing versions in dependencies (required for Maven Central)
            withXml {
                val lwjglVersion = "3.3.6"
                asNode().let { root ->
                    (root["dependencies"] as? groovy.util.NodeList)?.firstOrNull()?.let { deps ->
                        (deps as groovy.util.Node).children().forEach { depNode ->
                            val dep = depNode as groovy.util.Node
                            val groupId = (dep["groupId"] as? groovy.util.NodeList)?.text()
                            val version = (dep["version"] as? groovy.util.NodeList)?.text()
                            
                            // Add version to LWJGL dependencies if missing
                            if (groupId == "org.lwjgl" && version.isNullOrBlank()) {
                                val versionNode = dep["version"] as? groovy.util.NodeList
                                if (versionNode.isNullOrEmpty()) {
                                    dep.appendNode("version", lwjglVersion)
                                } else {
                                    (versionNode.first() as groovy.util.Node).setValue(lwjglVersion)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Signing configuration (required for Maven Central)
signing {
    // Only sign if publishing to Maven Central (not mavenLocal)
    setRequired { gradle.taskGraph.hasTask("publish") }
    
    // Use GPG key from environment or gradle.properties
    // Configure via: ORG_GRADLE_PROJECT_signingKey and ORG_GRADLE_PROJECT_signingPassword
    val signingKey: String? = findProperty("signingKey") as String?
    val signingPassword: String? = findProperty("signingPassword") as String?
    
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    
    sign(publishing.publications)
}

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
                enabled = (project.findProperty("enableJsBrowserTests") as String?)?.toBoolean() == true
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

    val hostOs = System.getProperty("os.name").lowercase()
    if (!hostOs.contains("win")) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()

        macosX64()
        macosArm64()
    }

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

                // wgpu4k for unified graphics backend
                implementation(libs.wgpu4k)

                // korlibs-math for math (Vector3, Matrix4, Quaternion, etc.)
                implementation(libs.korlibs.math)

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

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val appleMain by creating {
            dependsOn(nativeMain)
        }

        val iosMain by creating {
            dependsOn(appleMain)
            kotlin.exclude("io/materia/xr/**")
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

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        val appleTest by creating {
            dependsOn(nativeTest)
        }

        val iosTest by creating {
            dependsOn(appleTest)
        }

        val macosTest by creating {
            dependsOn(appleTest)
        }

        val iosX64Test by getting {
            dependsOn(iosTest)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }
        val macosX64Test by getting {
            dependsOn(macosTest)
        }
        val macosArm64Test by getting {
            dependsOn(macosTest)
        }

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

// NOTE: materia-gpu module removed - shader assets handling moved to wgpu4k-based setup
// The following was removed:
// gradle.projectsEvaluated {
//     project(":materia-gpu").extensions.configure<LibraryExtension>("android") { ... }
//     project(":materia-gpu").tasks.matching { it.name == "preBuild" }.configureEach { ... }
// }

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

val benchmarkModuleTasks = listOf(
    ":examples:triangle:benchmarkJvm",
    ":examples:triangle:benchmarkWeb",
    ":examples:triangle-android:benchmarkAndroid",
    ":examples:embedding-galaxy:benchmarkJvm",
    ":examples:embedding-galaxy:benchmarkWeb",
    ":examples:embedding-galaxy-android:benchmarkAndroid",
    ":examples:force-graph:benchmarkJvm",
    ":examples:force-graph:benchmarkWeb",
    ":examples:force-graph-android:benchmarkAndroid"
)

tasks.register("cleanBenchmarkData") {
    group = "benchmark"
    description = "Remove previously captured benchmark JSON artifacts"
    notCompatibleWithConfigurationCache("Deletes hardware-generated benchmark artifacts")
    doLast {
        delete(fileTree("docs/benchmarks/data/raw") { include("*.json") })
        delete(file("docs/benchmarks/data/latest.json"))
    }
}

tasks.register("benchmarkTriangleJvm") {
    group = "benchmark"
    description = "Run the Triangle JVM benchmark"
    dependsOn(":examples:triangle:benchmarkJvm")
}

tasks.register("benchmarkTriangleWeb") {
    group = "benchmark"
    description = "Run the Triangle Web benchmark"
    dependsOn(":examples:triangle:benchmarkWeb")
}

tasks.register("benchmarkTriangleAndroid") {
    group = "benchmark"
    description = "Run the Triangle Android benchmark"
    dependsOn(":examples:triangle-android:benchmarkAndroid")
}

tasks.register("benchmarkEmbeddingGalaxyJvm") {
    group = "benchmark"
    description = "Run the Embedding Galaxy JVM benchmark"
    dependsOn(":examples:embedding-galaxy:benchmarkJvm")
}

tasks.register("benchmarkEmbeddingGalaxyWeb") {
    group = "benchmark"
    description = "Run the Embedding Galaxy Web benchmark"
    dependsOn(":examples:embedding-galaxy:benchmarkWeb")
}

tasks.register("benchmarkEmbeddingGalaxyAndroid") {
    group = "benchmark"
    description = "Run the Embedding Galaxy Android benchmark"
    dependsOn(":examples:embedding-galaxy-android:benchmarkAndroid")
}

tasks.register("benchmarkForceGraphJvm") {
    group = "benchmark"
    description = "Run the Force Graph JVM benchmark"
    dependsOn(":examples:force-graph:benchmarkJvm")
}

tasks.register("benchmarkForceGraphWeb") {
    group = "benchmark"
    description = "Run the Force Graph Web benchmark"
    dependsOn(":examples:force-graph:benchmarkWeb")
}

tasks.register("benchmarkForceGraphAndroid") {
    group = "benchmark"
    description = "Run the Force Graph Android benchmark"
    dependsOn(":examples:force-graph-android:benchmarkAndroid")
}

tasks.register("benchmarkWebSmoke") {
    group = "verification"
    description = "Smoke-test Chrome benchmark automation"
    dependsOn(":examples:triangle:benchmarkWebSmoke")
}

tasks.register("benchmarkAndroidSmoke") {
    group = "verification"
    description = "Smoke-test Android benchmark automation"
    dependsOn(":examples:triangle-android:benchmarkAndroidSmoke")
}

tasks.register("generateBenchmarkSnapshot") {
    group = "benchmark"
    description = "Aggregate raw benchmark captures and refresh the README benchmark table"
    dependsOn(":materia-examples-common:jvmJar")
    notCompatibleWithConfigurationCache("Aggregates generated benchmark files and rewrites the README")

    doLast {
        val benchmarkCommonProject = project(":materia-examples-common")
        val kotlinExtension = benchmarkCommonProject.extensions.getByType(
            org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java
        )
        val jvmMainCompilation = kotlinExtension.jvm().compilations.getByName("main")

        javaexec {
            mainClass.set("io.materia.examples.benchmarks.BenchmarkSnapshotTool")
            classpath = benchmarkCommonProject.files(
                jvmMainCompilation.output.allOutputs,
                jvmMainCompilation.runtimeDependencyFiles
            )
            args(
                file("docs/benchmarks/data/raw").absolutePath,
                file("docs/benchmarks/data/latest.json").absolutePath,
                file("README.md").absolutePath
            )
        }
    }
}

listOf(
    "benchmarkTriangleJvm",
    "benchmarkTriangleWeb",
    "benchmarkTriangleAndroid",
    "benchmarkEmbeddingGalaxyJvm",
    "benchmarkEmbeddingGalaxyWeb",
    "benchmarkEmbeddingGalaxyAndroid",
    "benchmarkForceGraphJvm",
    "benchmarkForceGraphWeb",
    "benchmarkForceGraphAndroid"
).forEach { taskName ->
    tasks.named(taskName) {
        mustRunAfter("cleanBenchmarkData")
    }
}

tasks.register("benchmarkReadmeMatrix") {
    group = "benchmark"
    description = "Run the full 9-run benchmark matrix and publish the measured README table"
    notCompatibleWithConfigurationCache("Coordinates hardware-bound benchmark tasks and snapshot publication")
    dependsOn("cleanBenchmarkData")
    dependsOn(
        "benchmarkTriangleJvm",
        "benchmarkTriangleWeb",
        "benchmarkTriangleAndroid",
        "benchmarkEmbeddingGalaxyJvm",
        "benchmarkEmbeddingGalaxyWeb",
        "benchmarkEmbeddingGalaxyAndroid",
        "benchmarkForceGraphJvm",
        "benchmarkForceGraphWeb",
        "benchmarkForceGraphAndroid"
    )
    finalizedBy("generateBenchmarkSnapshot")
}

// Native tests disabled since native targets are not compiled
// tasks.named("linuxX64Test") {
//     enabled = false
// }
// tasks.named("mingwX64Test") {
//     enabled = false
// }

// Generate Javadocs for Maven Central (required)
tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    // Create empty javadoc jar as placeholder
    from("src/main/resources") {
        include("**/*.md")
        into(".")
    }
    doFirst {
        // Create a minimal javadoc structure
        val javadocDir = file("${layout.buildDirectory.get()}/tmp/javadoc")
        javadocDir.mkdirs()
        file("$javadocDir/README.md").writeText("# Materia Framework Documentation\n\nDocumentation is available at https://github.com/codeyousef/Materia")
        from(javadocDir)
    }
}

// Maven Central Publishing via Central Portal API
tasks.register("publishToCentralPortal") {
    group = "publishing"
    description = "Publish to Maven Central using Central Portal API"
    dependsOn("publishToMavenLocal", "javadocJar")
    
    doLast {
        // Load credentials from local.properties
        val localProperties = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) {
                load(localFile.inputStream())
            }
        }
        
        val username = localProperties.getProperty("mavenCentralUsername") 
            ?: throw GradleException("mavenCentralUsername not found in local.properties")
        val password = localProperties.getProperty("mavenCentralPassword")
            ?: throw GradleException("mavenCentralPassword not found in local.properties")
        val signingPassword = localProperties.getProperty("signingPassword")
            ?: throw GradleException("signingPassword not found in local.properties")
            
        println("🚀 Publishing to Maven Central via Central Portal API...")
        println("📦 Username: $username")
        
        // Create bundle directory with proper Maven structure
        val bundleDir = file("${layout.buildDirectory.get()}/central-portal-bundle")
        bundleDir.deleteRecursively()
        
        // Process each publication type with correct artifact IDs
        val artifactMappings = mapOf(
            "materia" to "kotlinMultiplatform",
            "materia-jvm" to "jvm", 
            "materia-js" to "js"
        )
        
        val allFilesToProcess = mutableListOf<File>()
        val groupPath = project.group.toString().replace('.', '/')

        artifactMappings.forEach { (artifactId, _) ->
            val mavenPath = "$groupPath/$artifactId/${project.version}"
            val targetDir = file("$bundleDir/$mavenPath")
            targetDir.mkdirs()
            
            // Copy artifacts from local Maven repository
            val localMavenDir =
                file("${System.getProperty("user.home")}/.m2/repository/$groupPath/$artifactId/${project.version}")
            if (localMavenDir.exists()) {
                println("📦 Processing $artifactId artifacts...")
                
                localMavenDir.listFiles()?.forEach { file ->
                    if ((file.name.endsWith(".jar") || file.name.endsWith(".pom") || file.name.endsWith(".klib") || file.name.endsWith(
                            ".module"
                        )) &&
                        !file.name.endsWith(".md5") && !file.name.endsWith(".sha1") && !file.name.endsWith(".asc")) {
                        file.copyTo(File(targetDir, file.name), overwrite = true)
                        allFilesToProcess.add(File(targetDir, file.name))
                    }
                }
                
                // Add javadoc jar for each platform
                val javadocJar = file("${layout.buildDirectory.get()}/libs/${project.name}-${project.version}-javadoc.jar")
                val needsJavadoc = artifactId in listOf("materia", "materia-jvm")
                if (needsJavadoc && javadocJar.exists()) {
                    val javadocFileName = "$artifactId-${project.version}-javadoc.jar"
                    val targetJavadocJar = File(targetDir, javadocFileName)
                    if (!targetJavadocJar.exists()) {
                        javadocJar.copyTo(targetJavadocJar, overwrite = true)
                        allFilesToProcess.add(targetJavadocJar)
                    }
                }
            } else {
                println("⚠️ No artifacts found for $artifactId at $localMavenDir")
            }
        }
            
        println("📝 Generating checksums and signatures...")
        
        allFilesToProcess.forEach { file ->
            // Generate MD5 checksum
            val md5Hash = MessageDigest.getInstance("MD5")
                .digest(file.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            File(file.parent, "${file.name}.md5").writeText(md5Hash)
            
            // Generate SHA1 checksum  
            val sha1Hash = MessageDigest.getInstance("SHA-1")
                .digest(file.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            File(file.parent, "${file.name}.sha1").writeText(sha1Hash)
            
            // Generate GPG signature using real key files
            val sigFile = File(file.parent, "${file.name}.asc")
            println("   Creating GPG signature for ${file.name}...")
            
            try {
                // Import the private key
                val privateKeyFile = when {
                    rootProject.file("private-key-clean.asc").exists() -> rootProject.file("private-key-clean.asc")
                    rootProject.file("private-key.asc").exists() -> rootProject.file("private-key.asc")
                    else -> null
                }
                if (privateKeyFile == null || !privateKeyFile.exists()) {
                    throw GradleException("private-key.asc not found. Cannot sign artifacts.")
                }

                val signScript = rootProject.file("sign-artifact.sh")
                if (!signScript.exists()) {
                    throw GradleException("sign-artifact.sh not found. Cannot sign artifacts.")
                }

                providers.exec {
                    commandLine(
                        "bash", signScript.absolutePath,
                        signingPassword, privateKeyFile.absolutePath,
                        sigFile.absolutePath, file.absolutePath
                    )
                }.result.get().assertNormalExitValue()

                if (!sigFile.exists()) {
                    throw GradleException("Failed to create signature for ${file.name}")
                }
            } catch (e: Exception) {
                throw GradleException("GPG signing error for ${file.name}: ${e.message}", e)
            }
        }

        println("📦 Creating ZIP bundle for Central Portal API")

        // Create ZIP file for Central Portal API
        val zipFile = file("${bundleDir.parent}/materia-${project.version}-bundle.zip")
        ant.invokeMethod("zip", mapOf(
            "destfile" to zipFile.absolutePath,
            "basedir" to bundleDir.absolutePath
        ))
        
        println("🚀 Uploading to Central Portal via REST API...")
        println("📦 Bundle: ${zipFile.absolutePath}")
        println("👤 Username: $username")

        // Upload via Central Portal REST API
        val authString = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        val uploadResult = providers.exec {
            commandLine(
                "curl", "-v", "-X", "POST",
                "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC",
                "-H", "Authorization: Basic $authString",
                "-F", "bundle=@${zipFile.absolutePath}",
                "--fail-with-body"
            )
            isIgnoreExitValue = true
        }.result.get()

        if (uploadResult.exitValue == 0) {
            println("✅ Successfully uploaded to Central Portal!")
            println("🔗 Check status at: https://central.sonatype.com/publishing/deployments")
            println("💡 The deployment will be validated and published automatically")
        } else {
            println("❌ Upload failed with exit code: ${uploadResult.exitValue}")
            println("📂 ZIP bundle location: ${zipFile.absolutePath}")
            println("🔗 Manual upload at: https://central.sonatype.com/publishing/deployments")
            throw GradleException("Failed to upload to Maven Central. Check credentials and try again.")
        }
        
        if (allFilesToProcess.isEmpty()) {
            throw GradleException("No Maven artifacts found. Make sure to run publishToMavenLocal first.")
        }
    }
}

// Convenience task to publish to local maven repository
tasks.register("publishLocal") {
    group = "publishing"
    description = "Publish all publications to the local Maven repository"
    dependsOn("publishToMavenLocal")
}
