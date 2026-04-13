plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
            commonWebpackConfig {
                outputFileName = "volume-texture.js"
                devServer = devServer?.copy(
                    open = false,
                    port = 8084
                )
            }
            binaries.executable()
            testTask {
                enabled = false
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
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
                implementation(libs.lwjgl.vulkan)

                val osName = System.getProperty("os.name").lowercase()
                val lwjglNatives = when {
                    osName.contains("win") -> "natives-windows"
                    osName.contains("linux") -> "natives-linux"
                    osName.contains("mac") || osName.contains("darwin") -> "natives-macos"
                    else -> "natives-linux"
                }

                runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
                runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
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

tasks.register<JavaExec>("runJvm") {
    group = "run"
    description = "Run the JVM version of the Volume Texture example"

    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(jvmMain.compileKotlinTaskName)

    mainClass.set("io.materia.examples.volumetexture.MainKt")
    classpath = files(
        jvmMain.output.allOutputs,
        configurations.named("jvmRuntimeClasspath")
    )
    jvmArgs(
        "-Dorg.lwjgl.system.stackSize=8192",
        "--enable-native-access=ALL-UNNAMED",
        "-Xmx2G",
        "-XX:+UseG1GC"
    )

    val osName = System.getProperty("os.name").lowercase()
    if (osName.contains("mac") || osName.contains("darwin")) {
        jvmArgs("-XstartOnFirstThread")
    }

    val java22Home = file("/usr/lib/jvm/java-22-openjdk")
    if (java22Home.exists()) {
        executable = file("$java22Home/bin/java").absolutePath
    }

    if (osName.contains("linux")) {
        val jemallocPath = "/usr/lib/libjemalloc.so"
        if (file(jemallocPath).exists()) {
            environment("LD_PRELOAD", jemallocPath)
        }
    }

    doFirst {
        println("🧊 Launching Volume Texture example on JVM")
        println("Bootstrapping Vulkan renderer with a Data3DTexture scene…")
    }
}

tasks.register("run") {
    group = "run"
    description = "Alias for `runJvm` to keep example commands consistent"
    dependsOn("runJvm")
}

tasks.register("runJs") {
    group = "run"
    description = "Run the Volume Texture example in the browser"
    dependsOn("jsBrowserDevelopmentRun")
}
