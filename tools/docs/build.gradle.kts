plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.dokka)
    application
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Web server for documentation
    implementation("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-content-negotiation:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-cors:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-html-builder:${libs.versions.ktor.get()}")

    // Documentation generation
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    implementation("org.jetbrains.dokka:dokka-core:${libs.versions.dokka.get()}")

    // Markdown processing
    implementation("org.jetbrains:markdown:0.5.0")

    // Search indexing
    implementation("org.apache.lucene:lucene-core:9.7.0")
    implementation("org.apache.lucene:lucene-analysis-common:9.7.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

application {
    mainClass.set("io.materia.tools.docs.server.DocServerKt")
}

tasks.register("serve") {
    group = "tools"
    description = "Start documentation server"
    dependsOn("run")
}

// Dokka configuration will be added once the implementation is complete

tasks.register("docs") {
    group = "documentation"
    description = "Generate all documentation"
    dependsOn("dokkaHtml")
}

// Serve task already defined above