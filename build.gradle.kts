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

val grpcVersion = "1.62.2"
val grpcKotlinVersion = "1.4.1"

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3")
    implementation("com.googlecode.lanterna:lanterna:3.1.1")
    implementation("com.google.code.gson:gson:2.10.1")  // JSON parsing

    // gRPC dependencies
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")  // For reflection
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

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
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.intentevolved.com.intentevolved.terminal.TerminalClientKt")
}

// Task to run the gRPC server
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the Intent gRPC server"
    mainClass.set("com.intentevolved.server.IntentServer")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("50051", "current.pb")
}

// Task to run the terminal client
tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Run the Intent terminal client"
    mainClass.set("com.intentevolved.com.intentevolved.terminal.TerminalClientKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    args = listOf("localhost", "50051")
}

// Task to print runtime classpath for use in shell scripts
tasks.register("printRuntimeClasspath") {
    doLast {
        println(sourceSets["main"].runtimeClasspath.asPath)
    }
}
