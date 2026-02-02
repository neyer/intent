#!/bin/bash
java -cp "$(./gradlew --quiet printRuntimeClasspath | tail -1)" com.intentevolved.PrintPlanKt $1

