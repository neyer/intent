#!/bin/bash
java -cp "$(cd "$(dirname "$0")/.." && ./gradlew --quiet printRuntimeClasspath | tail -1)" com.intentevolved.PrintPlanKt $1

