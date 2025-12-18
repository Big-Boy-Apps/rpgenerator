plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // For colored terminal output
    implementation("com.github.ajalt.mordant:mordant:2.2.0")

    // Full-screen terminal UI (ncurses-like)
    implementation("com.googlecode.lanterna:lanterna:3.1.2")

    // SLF4J logging (simple implementation to avoid warnings)
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Ktor for web server
    implementation("io.ktor:ktor-server-core:3.0.3")
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-html-builder:3.0.3")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
}

application {
    mainClass.set("com.rpgenerator.cli.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    args = listOf("--claude-code", "--debug")
}
