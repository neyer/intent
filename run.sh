#!/bin/bash

# Build the project first (quietly)
./gradlew build -q

# Get the runtime classpath from Gradle
CLASSPATH=$(./gradlew -q printRuntimeClasspath)

# Run Java directly with the classpath
exec java -cp "$CLASSPATH" com.intentevolved.MainKt "$@"
