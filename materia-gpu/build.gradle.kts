import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.androidLibrary)
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
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
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
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
                implementation(libs.lwjgl.vulkan)
                implementation(project(":"))
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(project(":"))
            }
        }

        val androidUnitTest by getting

        val androidMain by getting {
            dependencies {
                // Native Vulkan library for Android
                implementation(project(":materia-gpu-android-native"))
            }
        }
    }
}

android {
    val compileSdkVersion = libs.versions.androidCompileSdk.get().toInt()
    val minSdkVersion = libs.versions.androidMinSdk.get().toInt()

    compileSdk = compileSdkVersion
    namespace = "io.materia.gpu"

    defaultConfig {
        minSdk = minSdkVersion
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
