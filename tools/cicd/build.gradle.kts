plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(11)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(project(":"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // GitHub API
    implementation("org.kohsuke:github-api:1.317")

    // GitLab API
    implementation("org.gitlab4j:gitlab4j-api:5.2.0")

    // Docker/Container support
    implementation("com.github.docker-java:docker-java:3.3.3")

    // Build tools - Gradle Tooling API temporarily removed pending upgrade
    // implementation("org.gradle:gradle-tooling-api:8.14.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}