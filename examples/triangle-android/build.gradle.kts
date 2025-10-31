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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":examples:triangle"))
    implementation(project(":materia-examples-common"))
    implementation(project(":materia-gpu-android-native"))
    implementation(project(":materia-gpu"))
    implementation(project(":"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("syncAndroidShaders"))
}

tasks.register("runAndroid") {
    group = "examples"
    description = "Install the Triangle Android demo on a connected device or emulator"
    dependsOn("installDebug")
    doLast {
        println("📱 Triangle Android demo installed. Attempting to launch activity…")
        val component = "io.materia.examples.triangle.android/.MainActivity"
        val result = runCatching {
            project.exec {
                commandLine("adb", "shell", "am", "start", "-n", component)
            }
        }
        if (result.isFailure) {
            println("⚠️ Unable to launch automatically (adb not available?). Start manually with:")
            println("    adb shell am start -n $component")
        }
    }
}
