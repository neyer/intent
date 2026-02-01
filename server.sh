#!/bin/bash
set -e
cd "$(dirname "$0")"

# Build if needed
./gradlew --quiet classes

# Get classpath
CLASSPATH=$(./gradlew --quiet printRuntimeClasspath | tail -1)

# Default arguments
PORT="${1:-50051}"
FILE="${2:-current.pb}"

exec java -cp "$CLASSPATH" com.intentevolved.server.IntentServer "$PORT" "$FILE"
