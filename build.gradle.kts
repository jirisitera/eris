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
}

dependencies {
    // core
    implementation("net.minestom:minestom:26_3-SNAPSHOT")
    // logging
    implementation("org.slf4j:slf4j-simple:2.0.19")
    // nbt structure import
    implementation("dev.hollowcube:schem:2.0.1")

    // bettermodel & dependencies
    implementation("io.github.toxicity188:bettermodel-core:3.6.0")
    implementation("io.github.toxicity188:armormodel:1.2.0")
    implementation("gg.moonflower:molang-compiler:3.1.1.19")
    implementation("com.google.guava:guava:33.4.0-jre")
    implementation("org.joml:joml:1.10.8")
    implementation("org.semver4j:semver4j:6.0.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
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
