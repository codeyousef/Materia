import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "io.materia.examples.triangle.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.materia.examples.triangle.android"
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
    implementation(project(":examples:triangle"))
    implementation(project(":materia-examples-common"))
    implementation(project(":materia-gpu"))
    implementation(project(":"))
    implementation(libs.filament.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

configurations.configureEach {
    exclude(group = "org.lwjgl")
}

fun resolveAdbExecutable(): File? {
    val home = System.getProperty("user.home")
    val candidates = listOfNotNull(
        System.getenv("ADB"),
        System.getenv("ANDROID_HOME")?.let { "$it/platform-tools/adb" },
        System.getenv("ANDROID_SDK_ROOT")?.let { "$it/platform-tools/adb" },
        "$home/Android/Sdk/platform-tools/adb",
        "$home/Android/sdk/platform-tools/adb",
        "$home/Library/Android/sdk/platform-tools/adb"
    )

    return candidates
        .map(::File)
        .firstOrNull { candidate -> candidate.exists() && candidate.canExecute() }
}

fun runCommand(vararg args: String, printOutput: Boolean = true): Pair<Int, List<String>> {
    return try {
        val process = ProcessBuilder(*args)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().useLines { lines -> lines.toList() }
        if (printOutput) {
            output.forEach(::println)
        }

        process.waitFor() to output
    } catch (error: Exception) {
        if (printOutput) {
            println("⚠️ Failed to run ${args.joinToString(" ")}: ${error.message}")
        }
        -1 to listOf(error.message ?: "Unknown process error")
    }
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("syncAndroidShaders"))
}

tasks.register("runAndroid") {
    group = "run"
    description = "Install the Triangle Android demo on a connected device or emulator"
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("Invokes adb commands for installation")
    doNotTrackState("adb install/start invocations are non-deterministic")
    doLast {
        val component = "io.materia.examples.triangle.android/.TriangleActivity"
        val adb = resolveAdbExecutable()
        val apk = layout.buildDirectory
            .file("outputs/apk/debug/${project.name}-debug.apk")
            .get()
            .asFile

        if (adb == null) {
            println("⚠️ adb was not found. Set ADB, ANDROID_HOME, or ANDROID_SDK_ROOT, or install platform-tools.")
            return@doLast
        }

        if (!apk.exists()) {
            println("⚠️ APK not found at ${apk.absolutePath}")
            return@doLast
        }

        runCommand(adb.absolutePath, "start-server")
        val deviceAvailable = runCommand(adb.absolutePath, "get-state").first == 0

        if (!deviceAvailable) {
            println("⚠️ No Android device detected. Install manually with:")
            println("    ${adb.absolutePath} install -r ${apk.absolutePath}")
            println("    ${adb.absolutePath} shell am start -n $component")
            return@doLast
        }

        if (runCommand(adb.absolutePath, "install", "-r", apk.absolutePath).first != 0) {
            println("⚠️ Failed to install APK automatically. Try:")
            println("    ${adb.absolutePath} install -r ${apk.absolutePath}")
        }

        if (runCommand(adb.absolutePath, "shell", "am", "start", "-n", component).first != 0) {
            println("⚠️ Unable to launch automatically (adb not available?). Start manually with:")
            println("    ${adb.absolutePath} shell am start -n $component")
        }
    }
}

tasks.register("smokeAndroid") {
    group = "verification"
    description = "Install, launch, and verify the Triangle Android demo boot log on a connected device or emulator"
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("Invokes adb and polls logcat for device state")
    doNotTrackState("adb install/start/logcat invocations are non-deterministic")
    doLast {
        val adb = resolveAdbExecutable()
            ?: error("adb was not found. Set ADB, ANDROID_HOME, or ANDROID_SDK_ROOT, or install platform-tools.")
        val component = "io.materia.examples.triangle.android/.TriangleActivity"
        val packageName = "io.materia.examples.triangle.android"
        val apk = layout.buildDirectory
            .file("outputs/apk/debug/${project.name}-debug.apk")
            .get()
            .asFile

        check(apk.exists()) { "APK not found at ${apk.absolutePath}" }

        runCommand(adb.absolutePath, "start-server")
        check(runCommand(adb.absolutePath, "get-state").first == 0) {
            "No Android device/emulator detected for smoke test. Attach one and retry."
        }

        runCommand(adb.absolutePath, "logcat", "-c")
        runCommand(adb.absolutePath, "shell", "am", "force-stop", packageName, printOutput = false)

        check(runCommand(adb.absolutePath, "install", "-r", apk.absolutePath).first == 0) {
            "Failed to install APK for smoke test."
        }
        check(runCommand(adb.absolutePath, "shell", "am", "start", "-n", component).first == 0) {
            "Failed to launch TriangleActivity for smoke test."
        }

        val successMarker = "Renderer boot succeeded"
        val failureMarkers = listOf(
            "Renderer bootstrap failed",
            "Headless boot succeeded",
            "Vulkan not advertised",
            "FATAL EXCEPTION"
        )
        val deadline = System.currentTimeMillis() + 30_000L
        var lastOutput = emptyList<String>()

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(1_000L)
            val (_, output) = runCommand(
                adb.absolutePath,
                "logcat",
                "-d",
                "TriangleActivity:I",
                "AndroidRuntime:E",
                "*:S",
                printOutput = false
            )
            lastOutput = output

            if (output.any { line -> line.contains(successMarker) }) {
                println("✅ Triangle Android smoke test passed")
                return@doLast
            }

            val failure = output.firstOrNull { line -> failureMarkers.any(line::contains) }
            if (failure != null) {
                val reason = output.firstOrNull { line ->
                    line.contains("TriangleActivity: Reason:") ||
                        line.contains("Android rendering is blocked")
                }?.substringAfter("Reason: ")?.trim()

                val diagnostic = output.firstOrNull { line ->
                    line.contains("DeviceCreationFailedException:") ||
                        line.contains("NoGraphicsSupportException:")
                }?.substringAfter("TriangleActivity:")?.trim()

                val details = reason ?: diagnostic
                if (details != null) {
                    error("Triangle Android smoke test failed: $details")
                }

                error("Triangle Android smoke test failed: $failure")
            }
        }

        val tail = lastOutput.takeLast(20).joinToString("\n")
        error("Timed out waiting for TriangleActivity boot log. Last log lines:\n$tail")
    }
}
