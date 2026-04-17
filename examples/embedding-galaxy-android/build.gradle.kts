import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "io.materia.examples.embeddinggalaxy.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.materia.examples.embeddinggalaxy.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs.keepDebugSymbols += "**/*.so"
        resources.excludes += setOf(
            "META-INF/INDEX.LIST",
            "META-INF/DEPENDENCIES",
            "META-INF/**"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":examples:embedding-galaxy"))
    implementation(project(":materia-examples-common"))
    implementation(project(":materia-gpu"))
    implementation(project(":materia-engine"))
    implementation(project(":"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

configurations.configureEach {
    exclude(group = "org.lwjgl")
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("syncAndroidShaders"))
}

tasks.register("runAndroid") {
    group = "run"
    description = "Install and launch the Embedding Galaxy Android demo"
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("Invokes adb commands for installation")
    doNotTrackState("adb install/start invocations are non-deterministic")
    doLast {
        fun runAdbCommand(vararg args: String): Int = try {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { println(it) }
            }
            process.waitFor()
        } catch (error: Exception) {
            println("⚠️ Failed to run ${args.joinToString(" ")}: ${error.message}")
            -1
        }

        val component = "io.materia.examples.embeddinggalaxy.android/.EmbeddingGalaxyActivity"
        val apk = layout.buildDirectory
            .file("outputs/apk/debug/${project.name}-debug.apk")
            .get()
            .asFile

        if (!apk.exists()) {
            println("⚠️ APK not found at ${apk.absolutePath}")
            return@doLast
        }

        val deviceAvailable = runAdbCommand("adb", "get-state") == 0

        if (!deviceAvailable) {
            println("⚠️ No Android device detected. Install manually with:")
            println("    adb install -r ${apk.absolutePath}")
            println("    adb shell am start -n $component")
            return@doLast
        }

        if (runAdbCommand("adb", "install", "-r", apk.absolutePath) != 0) {
            println("⚠️ Failed to install APK automatically. Try:")
            println("    adb install -r ${apk.absolutePath}")
        }

        if (runAdbCommand("adb", "shell", "am", "start", "-n", component) != 0) {
            println("⚠️ Unable to launch automatically. Start manually with:")
            println("    adb shell am start -n $component")
        }
    }
}

tasks.register<Exec>("benchmarkAndroid") {
    group = "benchmark"
    description = "Run measured Android benchmark captures for the Embedding Galaxy example"
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("Launches adb/emulator automation")
    doNotTrackState("Hardware-bound benchmark automation")
    commandLine(
        "node",
        rootProject.file("scripts/benchmarks/run_android_benchmark.mjs").absolutePath,
        "--adb", "/home/yousef/Android/Sdk/platform-tools/adb",
        "--emulator", "/home/yousef/Android/Sdk/emulator/emulator",
        "--avd", "Pixel_9_Pro",
        "--apk", layout.buildDirectory.file("outputs/apk/debug/${project.name}-debug.apk").get().asFile.absolutePath,
        "--package", "io.materia.examples.embeddinggalaxy.android",
        "--component", "io.materia.examples.embeddinggalaxy.android/.EmbeddingGalaxyActivity",
        "--raw-dir", rootProject.file("docs/benchmarks/data/raw").absolutePath,
        "--scene", "embedding-galaxy"
    )
}
