import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

tasks {
    jar {
        archiveFileName.set("server.jar")
    }
}

group = "net.minestom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("net.minestom:minestom-snapshots:7320437640")
    implementation("de.articdive:jnoise-pipeline:4.0.0")
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("net.kyori:adventure-text-minimessage:4.12.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks {
    named<ShadowJar>("shadowJar") {
        manifest {
            attributes (
                "Main-Class" to "net.minestom.arena.Main",
                "Multi-Release" to true
            )
        }
        archiveBaseName.set("arena")
        mergeServiceFiles()
    }

    build { dependsOn(shadowJar) }
}
