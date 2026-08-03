plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")
        bundledPlugin("Git4Idea")
    }

    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("version")
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    // Avoid clashing with an already-running sandbox IDE from runIde.
    buildSearchableOptions {
        enabled = false
    }

    wrapper {
        gradleVersion = "9.0.0"
    }
}
