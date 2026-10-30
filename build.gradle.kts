plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.japicraft"
version = "INDEV"

repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    mavenCentral()
}

dependencies {
    // core
    implementation("net.minestom:minestom:26_3-SNAPSHOT")
    // logging
    implementation("org.slf4j:slf4j-simple:2.0.19")
    // configs
    implementation("org.tomlj:tomlj:1.3.0")
    // nbt structure import
    implementation("dev.hollowcube:schem:2.0.1")
    // type checking
    compileOnly("org.checkerframework:checker-qual:4.2.3")
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
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        exclude("META-INF/LICENSE.txt")
        mergeServiceFiles()
    }
}
