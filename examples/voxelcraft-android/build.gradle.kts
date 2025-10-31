import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "io.materia.examples.voxelcraft.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.materia.examples.voxelcraft.android"
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
    implementation(project(":examples:voxelcraft"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
}

configurations.configureEach {
    exclude(group = "org.lwjgl")
}

tasks.register("runAndroid") {
    group = "run"
    description = "Install and launch the VoxelCraft Android placeholder demo"
    dependsOn("assembleDebug")
    notCompatibleWithConfigurationCache("Invokes adb commands for installation")
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

        val component = "io.materia.examples.voxelcraft.android/.VoxelCraftActivity"
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
