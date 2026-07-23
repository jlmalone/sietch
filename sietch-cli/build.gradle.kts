plugins {
    kotlin("jvm")
    application
}

group = "vision.salient.sietch"
version = "2.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":sietch-core"))
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("vision.salient.sietch.cli.MainKt")
}
