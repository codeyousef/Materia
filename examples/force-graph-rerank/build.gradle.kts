import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }

    js(IR) {
        browser {
            binaries.executable()
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                implementation(project(":kreekt-examples-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.glfw)
                implementation(libs.lwjgl.vulkan)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    mainClass.set("io.kreekt.examples.forcegraph.MainKt")
    val compilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = files(
        compilation.output.allOutputs,
        compilation.runtimeDependencyFiles
    )
}
