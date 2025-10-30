plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        jvmToolchain(17)
    }

    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            dependencies {
                // Materia Core
                implementation(project(":materia-core"))
                implementation(project(":materia-renderer"))
                implementation(project(":materia-scene"))
                implementation(project(":materia-geometry"))
                implementation(project(":materia-material"))
                implementation(project(":materia-animation"))

                // Materia Tools Integration
                implementation(project(":tools:editor"))
                implementation(project(":tools:profiler"))

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Math
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            }
        }

        jvmMain {
            dependencies {
                // Desktop UI
                implementation(compose.desktop.currentOs)
                implementation(compose.desktop.common)

                // File I/O
                implementation("java.desktop")

                // Logging
                implementation("ch.qos.logback:logback-classic:1.4.11")
            }
        }

        jsMain {
            dependencies {
                // Web canvas
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:1.0.0-pre.574")

                // Web tools integration
                implementation(npm("@webgpu/types", "0.1.40"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.materia.samples.basic.DesktopAppKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )

            packageName = "Materia-Tools-Basic-Example"
            packageVersion = "1.0.0"
            description = "Basic usage example for Materia development tools"
            copyright = "© 2025 Materia Project"
            vendor = "Materia Project"

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                console = false
                dirChooser = true
                perUserInstall = true
            }

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
                bundleID = "io.materia.samples.basic"
                appCategory = "public.app-category.developer-tools"
                entitlementsFile.set(project.file("src/jvmMain/resources/entitlements.plist"))
            }

            linux {
                iconFile.set(project.file("src/jvmMain/resources/icon.png"))
                appCategory = "Development"
                debMaintainer = "tools@materia.dev"
                menuGroup = "Development"
            }
        }
    }
}

// Tasks for launching tools
tasks.register("launchSceneEditor", JavaExec::class) {
    group = "materia-tools"
    description = "Launch the Scene Editor"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("io.materia.tools.editor.SceneEditorKt")
    args = listOf("--project", project.projectDir.absolutePath)
}

tasks.register("launchMaterialEditor", JavaExec::class) {
    group = "materia-tools"
    description = "Launch the Material Editor"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("io.materia.tools.editor.MaterialEditorKt")
    args = listOf("--project", project.projectDir.absolutePath)
}

tasks.register("launchProfiler", JavaExec::class) {
    group = "materia-tools"
    description = "Launch the Performance Profiler"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("io.materia.tools.profiler.ProfilerKt")
    args = listOf("--project", project.projectDir.absolutePath)
}

tasks.register("launchWebTools", Exec::class) {
    group = "materia-tools"
    description = "Launch web-based development tools"
    commandLine("node", project.rootProject.file("tools/web-host/server.js"))
    doFirst {
        println("Starting Materia web tools at http://localhost:3000")
    }
}

// Custom tasks for this example
tasks.register("runExample", JavaExec::class) {
    group = "application"
    description = "Run the basic tools example"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("io.materia.samples.basic.DesktopAppKt")
}

tasks.register("generateSceneFile") {
    group = "materia-tools"
    description = "Generate example scene file"
    doLast {
        val sceneDir = file("scenes")
        sceneDir.mkdirs()

        val sceneContent = """
        {
          "formatVersion": "1.0",
          "scene": {
            "background": [0.1, 0.1, 0.1],
            "objects": [
              {
                "name": "cube",
                "type": "Mesh",
                "geometry": {
                  "type": "BoxGeometry",
                  "width": 2.0,
                  "height": 2.0,
                  "depth": 2.0
                },
                "material": "red_material",
                "position": [-3.0, 0.0, 0.0],
                "rotation": [0.0, 0.0, 0.0],
                "scale": [1.0, 1.0, 1.0]
              },
              {
                "name": "sphere",
                "type": "Mesh",
                "geometry": {
                  "type": "SphereGeometry",
                  "radius": 1.5
                },
                "material": "green_material",
                "position": [0.0, 0.0, 0.0],
                "rotation": [0.0, 0.0, 0.0],
                "scale": [1.0, 1.0, 1.0]
              },
              {
                "name": "plane",
                "type": "Mesh",
                "geometry": {
                  "type": "PlaneGeometry",
                  "width": 10.0,
                  "height": 10.0
                },
                "material": "gray_material",
                "position": [0.0, -2.0, 0.0],
                "rotation": [-1.5708, 0.0, 0.0],
                "scale": [1.0, 1.0, 1.0]
              }
            ],
            "lights": [
              {
                "name": "ambient",
                "type": "AmbientLight",
                "color": [0.4, 0.4, 0.4],
                "intensity": 1.0
              },
              {
                "name": "directional",
                "type": "DirectionalLight",
                "color": [0.8, 0.8, 0.8],
                "intensity": 1.0,
                "position": [5.0, 10.0, 5.0],
                "target": [0.0, 0.0, 0.0]
              }
            ]
          }
        }
        """.trimIndent()

        file("scenes/basic-scene.materia").writeText(sceneContent)

        val materialsContent = """
        {
          "materials": {
            "red_material": {
              "type": "StandardMaterial",
              "color": [1.0, 0.0, 0.0],
              "metalness": 0.0,
              "roughness": 0.5
            },
            "green_material": {
              "type": "StandardMaterial",
              "color": [0.0, 1.0, 0.0],
              "metalness": 0.8,
              "roughness": 0.2
            },
            "gray_material": {
              "type": "StandardMaterial",
              "color": [0.5, 0.5, 0.5],
              "metalness": 0.0,
              "roughness": 0.8
            }
          }
        }
        """.trimIndent()

        file("scenes/materials.json").writeText(materialsContent)

        println("Generated example scene files:")
        println("- scenes/basic-scene.materia")
        println("- scenes/materials.json")
    }
}

// Ensure scene files are generated before running
tasks.named("run") {
    dependsOn("generateSceneFile")
}

tasks.named("runExample") {
    dependsOn("generateSceneFile")
}