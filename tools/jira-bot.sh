#!/bin/bash
# Sync the intent tree to Jira Cloud.
#
# Usage:
#   ./tools/jira-bot.sh                            # one-shot sync, uses jira-bot.json
#   ./tools/jira-bot.sh jira-bot.json              # one-shot sync, explicit config
#   ./tools/jira-bot.sh jira-bot.json --watch      # poll for new intents every pollIntervalSeconds
#   ./tools/jira-bot.sh jira-bot.json --list-fields # print custom field IDs (find epicLinkField)
#   ./tools/jira-bot.sh --init                     # write example config to jira-bot.json.example
set -e
cd "$(dirname "$0")/.."

./gradlew --quiet classes

CLASSPATH=$(./gradlew --quiet printRuntimeClasspath | tail -1)

exec java -cp "$CLASSPATH" com.apxhard.voluntas.jirabot.JiraBotKt "$@"
