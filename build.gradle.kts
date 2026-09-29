plugins {
    java
}

group = "dev.veloraxx"
version = "1.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    val localLibraries = System.getenv("PAPER_SERVER_LIBS")
    if (localLibraries == null) compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    else compileOnly(fileTree(localLibraries) { include("**/*.jar") })
    val localFancyNpcs = System.getenv("FANCYNPCS_JAR")
    if (localFancyNpcs == null) compileOnly("maven.modrinth:fancynpcs:2.12.1")
    else compileOnly(files(localFancyNpcs))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(26))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}
