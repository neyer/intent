#!/bin/bash
java -cp "$(cd "$(dirname "$0")/.." && ./gradlew --quiet printRuntimeClasspath | tail -1)" com.apxhard.voluntas.PrintPlanKt $1

