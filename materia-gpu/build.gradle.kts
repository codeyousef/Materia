import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.androidLibrary)
}

val hostOs = System.getProperty("os.name")

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

    if (!hostOs.startsWith("Windows", ignoreCase = true)) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        macosX64()
        macosArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":"))
                // wgpu4k-toolkit for unified GPU abstraction (replaces LWJGL Vulkan and custom native backends)
                api(libs.wgpu4k)
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
                // LWJGL GLFW kept for windowing (wgpu4k-toolkit uses it internally for JVM)
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.glfw)
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
                // wgpu4k-toolkit handles Android via its native helper - no custom native bridge needed
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
