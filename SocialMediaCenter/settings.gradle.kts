rootProject.name = "SocialMediaCenter"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental") // needed for Ktor 3.0.0 WASM
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental") // needed for Ktor 3.0.0 WASM
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

if (startParameter.projectProperties.getOrDefault("excludeComposeApp", "") != "true") {
    // Condition is needed to avoid trying to configure Kotlin/Native in the Linux aarch64 builder
    include(":composeApp")
}
include(":server")