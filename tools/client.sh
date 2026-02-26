#!/bin/bash
set -e
cd "$(dirname "$0")"

# Build if needed
./gradlew --quiet classes

# Get classpath
CLASSPATH=$(./gradlew --quiet printRuntimeClasspath | tail -1)

# Default arguments
HOST="${1:-localhost}"
PORT="${2:-50051}"

exec java -cp "$CLASSPATH" com.intentevolved.com.intentevolved.terminal.TerminalClientKt "$HOST" "$PORT"
