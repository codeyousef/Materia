import org.gradle.api.JavaVersion

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidLibrary)
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
                // materia-gpu provides abstract GPU types
                implementation(project(":materia-gpu"))
                // Root project provides wgpu4k and korlibs-math
                implementation(project(":"))
                implementation(libs.kotlinx.coroutines.core)
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
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.browser)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.filament.android)
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

        val androidUnitTest by getting
    }
}

android {
    val compileSdkVersion = libs.versions.androidCompileSdk.get().toInt()
    val minSdkVersion = libs.versions.androidMinSdk.get().toInt()

    compileSdk = compileSdkVersion
    namespace = "io.materia.engine"

    defaultConfig {
        minSdk = minSdkVersion
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].assets.srcDirs("src/androidMain/assets")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
