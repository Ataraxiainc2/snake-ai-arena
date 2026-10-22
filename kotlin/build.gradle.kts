import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

val kotlinVersion = "2.4.21"

// Build output lives outside the arena folder (~/.snake-arena/build), so kotlin/ holds only sources and tactics.
layout.buildDirectory.set(file(System.getProperty("user.home") + "/.snake-arena/build"))

dependencies {
    // The arena compiles tacticas/*.kt by itself (audit, compile, load, hot reload), like SNAKES.py loads .py files.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    testImplementation(kotlin("test"))
}

// Bytecode for Java 26: the newest that Kotlin 2.4 emits and that Android's d8 (build-tools 37) accepts, so the same
// classes run on the PC and the phone. Gradle itself runs on Java 27 (gradle/gradle-daemon-jvm.properties).
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_26
    targetCompatibility = JavaVersion.VERSION_26
}

application {
    mainClass.set("arena.MainKt")
    applicationDefaultJvmArgs = listOf("-Xss16m", "-XX:+UseParallelGC")
}

tasks.named<JavaExec>("run") {
    // Paths given on the command line are relative to the arena folder (the one holding SNAKES.py).
    workingDir = rootDir.parentFile
}

/**
 * build/phone/arena.zip: the engine, the API, the reference ports and the Kotlin standard library as Android DEX, to
 * time the arena on a phone with ART (see README: adb push + dalvikvm64 ... arena.MainKt bench). Needs the Android
 * SDK (ANDROID_HOME, or the default %LOCALAPPDATA%\Android\Sdk) with build-tools and a platform installed.
 */
tasks.register<Exec>("phoneDex") {
    dependsOn("jar")
    // everything the action needs is resolved into values or file collections here, so the configuration cache can
    // store the task: no Project access while it runs
    val sdk = providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("LOCALAPPDATA").map { "$it/Android/Sdk" })
    val stdlib = configurations.runtimeClasspath.get().filter { it.name.startsWith("kotlin-stdlib-") }
    val jar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val out = layout.buildDirectory.file("phone/arena.zip")
    val windows = providers.systemProperty("os.name").map { it.startsWith("Windows") }
    doFirst {
        val root = File(sdk.get())
        val buildTools = File(root, "build-tools").listFiles()?.maxByOrNull { it.name } ?: error("no Android build-tools in $root")
        val platform = File(root, "platforms").listFiles()?.maxByOrNull { it.name } ?: error("no Android platform in $root")
        val zip = out.get().asFile
        zip.parentFile.mkdirs()
        val d8 = File(buildTools, if (windows.get()) "d8.bat" else "d8")
        // the real tactics, when `pack` prepared them
        val tactics = zip.resolveSibling("tactics.jar").takeIf { it.exists() }?.path
        commandLine(listOfNotNull(d8.path, "--release", "--min-api", "34", "--output", zip.path, jar.get().asFile.path,
            stdlib.singleFile.path, tactics, "--lib", File(platform, "android.jar").path))
    }
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootDir.parentFile
    maxHeapSize = "2g"
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
