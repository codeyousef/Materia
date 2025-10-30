plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("io.materia.tools.api.ToolServerKt")
}

dependencies {
    implementation(project(":tools:editor"))
    implementation(project(":tools:profiler"))
    implementation(project(":tools:tests"))
    implementation(project(":tools:docs"))

    // Ktor server
    implementation("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-content-negotiation:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-cors:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-compression:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-caching-headers:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-call-logging:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-websockets:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-auth:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-auth-jwt:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-status-pages:${libs.versions.ktor.get()}")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor.get()}")
    implementation(libs.kotlinx.serialization.json)

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.43.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.43.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.43.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.43.0")
    implementation("com.h2database:h2:2.2.220")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:${libs.versions.ktor.get()}")
    testImplementation(libs.kotlin.test)
}