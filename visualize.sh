#!/bin/bash
set -e
cd "$(dirname "$0")"

# Build if needed
./gradlew --quiet classes

# Get classpath
CLASSPATH=$(./gradlew --quiet printRuntimeClasspath | tail -1)

exec java -cp "$CLASSPATH" com.intentevolved.visualizer.TimelineVisualizerKt "$@"
