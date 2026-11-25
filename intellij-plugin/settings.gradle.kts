import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

// The IntelliJ plugin for unweird: a separate build, because it downloads an IDE to compile against.
// Build: ../gradlew -p intellij-plugin buildPlugin   ·   try it in a sandbox IDE: ../gradlew -p intellij-plugin runIde
rootProject.name = "unweird-intellij"

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.20.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
