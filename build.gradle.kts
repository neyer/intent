plugins {
    kotlin("jvm") version "2.2.0"
    id("com.google.protobuf") version "0.9.4"
    application
}

group = "com.intentevolved"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3") // runtime for Kotlin stubs
    implementation("com.googlecode.lanterna:lanterna:3.1.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
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

application {
    mainClass.set("com.intentevolved.MainKt")
}

// Task to print runtime classpath for use in shell scripts
tasks.register("printRuntimeClasspath") {
    doLast {
        println(sourceSets["main"].runtimeClasspath.asPath)
    }
}
