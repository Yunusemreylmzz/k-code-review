import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "k-code-review"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.1.10"
        id("org.jetbrains.intellij.platform") version "2.5.0"
        id("org.jetbrains.intellij.platform.settings") version "2.5.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    id("org.jetbrains.intellij.platform.settings")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
