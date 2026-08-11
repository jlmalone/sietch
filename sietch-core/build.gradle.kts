plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.0.21"
    `java-library`
}

group = "vision.salient.sietch"
version = "2.2.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.3"

dependencies {
    // HTTP client for Kubo IPFS API
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // SQLite for ContentLocationRegistry
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
