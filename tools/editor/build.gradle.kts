import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    js(IR) {
        browser {
            webpackTask(Action {
                mainOutputFileName = "scene-editor.js"
            })
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(npm("@webgpu/types", "0.1.40"))
                implementation(libs.kotlinx.browser)
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.materia.tools.editor.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Materia Scene Editor"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "Materia Tools"
                upgradeUuid = "a6f2b8c3-d4e5-4f6a-8b9c-1d2e3f4a5b6c"
            }

            macOS {
                bundleID = "io.materia.tools.editor"
            }

            linux {
                packageName = "materia-scene-editor"
            }
        }
    }
}

tasks.register("runWeb") {
    group = "tools"
    description = "Run the web-based scene editor"
    dependsOn("jsBrowserDevelopmentRun")
}

tasks.register("runDesktop") {
    group = "tools"
    description = "Run the desktop scene editor"
    dependsOn("jvmRun")
}

tasks.register("packageDesktop") {
    group = "tools"
    description = "Package desktop scene editor"
    dependsOn("packageDistributionForCurrentOS")
}