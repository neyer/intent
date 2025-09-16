plugins {
    kotlin("jvm") version "2.2.0"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.intentevolved"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3") // runtime for Kotlin stubs
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
}

kotlin {
    jvmToolchain(21)
}
