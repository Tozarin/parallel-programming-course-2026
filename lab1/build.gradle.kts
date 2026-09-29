plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "lab1"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}
