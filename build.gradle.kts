plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "vision.salient.sietch"
version = "2.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Root module depends on sietch-core for the library API
    implementation(project(":sietch-core"))
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("vision.salient.sietch.MainKt")
}

// Gradle task: index a directory
tasks.register<JavaExec>("index") {
    group = "sietch"
    description = "Index a directory into a flat file catalog (path, hash, size)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vision.salient.sietch.MainKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    doFirst {
        val exec = this as JavaExec
        val scanPath = project.findProperty("scanPath")?.toString()
            ?: throw GradleException("Usage: ./gradlew index -PscanPath=/some/directory [-Poutput=catalog.txt] [-Phash=sha256|md5|none]")
        val output = project.findProperty("output")?.toString() ?: ""
        val hash = project.findProperty("hash")?.toString() ?: "sha256"
        val argsList = mutableListOf(scanPath)
        if (output.isNotEmpty()) { argsList.add("--output"); argsList.add(output) }
        argsList.add("--hash"); argsList.add(hash)
        exec.args = argsList
    }
}

// Gradle task: inspect a SQLite database
tasks.register<JavaExec>("inspectDb") {
    group = "sietch"
    description = "List tables in a SQLite database"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("vision.salient.sietch.NaibKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    doFirst {
        val exec = this as JavaExec
        val dbPath = project.findProperty("db")?.toString()
            ?: throw GradleException("Usage: ./gradlew inspectDb -Pdb=/path/to/database.sqlite")
        exec.args = listOf(dbPath)
    }
}
