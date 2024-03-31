plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ktorServer)
}

group = "com.cbruegg"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.bigbone)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.serverside.ktor.client.core)
    implementation(libs.serverside.ktor.client.cio)
    implementation(libs.serverside.ktor.client.content.negotiation)
    implementation(libs.serverside.ktor.serialization.kotlinx.json)

    implementation(libs.serverside.ktor.server.core)
    implementation(libs.serverside.ktor.server.cors)
    implementation(libs.serverside.ktor.server.netty)
    implementation(libs.serverside.ktor.server.sessions)
    implementation(libs.serverside.ktor.server.auth)
    implementation(libs.serverside.ktor.server.content.negotiation)
    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
}

application {
    applicationDefaultJvmArgs = listOf("-Xmx768m")
    mainClass.set("com.cbruegg.socialmediaserver.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(20)
}