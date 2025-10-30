plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }

    // Native targets
    mingwX64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Depends on root project which contains core, renderer, material, scene
                implementation(project(":"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
    }
}
