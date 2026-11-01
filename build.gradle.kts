plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.japicraft"
version = "INDEV"

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    mavenCentral()
    maven("https://maven.blamejared.com/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    // core
    implementation("net.minestom:minestom:26_3-SNAPSHOT")
    // Logging API for application code and runtime backend.
    implementation("org.slf4j:slf4j-api:2.0.20")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.19")
    // nbt structure import
    implementation("dev.hollowcube:schem:2.0.1")

    // simple voice chat api
    implementation("de.maxhenkel.voicechat:voicechat-api:2.6.27")

    // BetterModel and libraries used directly by the Minestom adapter.
    implementation("io.github.toxicity188:bettermodel-core:3.6.0")
    implementation("org.joml:joml:1.10.8")
    implementation("org.semver4j:semver4j:6.0.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.1")
    implementation("com.google.code.gson:gson:2.14.0")

    // BetterModel expects these at runtime but does not publish them transitively.
    runtimeOnly("io.github.toxicity188:armormodel:1.2.0")
    runtimeOnly("gg.moonflower:molang-compiler:3.1.1.19")
    runtimeOnly("com.google.guava:guava:33.4.0-jre")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "com.japicraft.Eris"
        }
    }
    shadowJar {
        archiveClassifier.set("")
        archiveVersion.set("")
        archiveBaseName.set("eris")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        filesMatching("META-INF/*.kotlin_module") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("blue_wizard.bbmodel")
        exclude("demon_knight.bbmodel")
        exclude("steve.bbmodel")
        mergeServiceFiles()
    }
}
