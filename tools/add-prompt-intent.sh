#!/usr/bin/env bash
# UserPromptSubmit hook: records the prompt text as a typed 'prompt' intent.
#
# Reads a Claude Code hook JSON payload from stdin (.prompt field).
# Looks up the 'prompt' macro via GetCommands, then invokes it via SubmitOp.
# Silently exits if the server is unreachable or the agents module is not loaded.
#
# Env vars:
#   VOLUNTAS_SERVER  gRPC server address (default: localhost:50051)
#   VOLUNTAS_USER    username for auth
#   VOLUNTAS_TOKEN   auth token

set -euo pipefail

SERVER="${VOLUNTAS_SERVER:-localhost:50051}"

payload=$(cat)
prompt_text=$(printf '%s' "$payload" | jq -r '.prompt // empty' 2>/dev/null)

if [ -z "$prompt_text" ]; then
    exit 0
fi

# Find the macro entity ID for the 'prompt' command
macro_id=$(timeout 2 grpcurl -plaintext "$SERVER" voluntas.v1.IntentService/GetCommands 2>/dev/null \
    | jq -r '(.commands // [])[] | select(.keyword == "prompt") | .macro_entity_id // empty' 2>/dev/null \
    || true)

if [ -z "$macro_id" ]; then
    exit 0  # agents module not loaded or server unreachable
fi

# Find the user's own intent by matching their username against root's children
user_intent_id=$(grpcurl -plaintext -d '{"id": 0}' "$SERVER" voluntas.v1.IntentService/GetFocalScope 2>/dev/null \
    | jq -r --arg user "${VOLUNTAS_USER:-}" \
        '.children[] | select(.text == $user) | .id // empty' 2>/dev/null \
    || true)
parent_id="${user_intent_id:-0}"

grpcurl -plaintext \
    -d "$(jq -n \
        --argjson macroId "$macro_id" \
        --arg text "$prompt_text" \
        --argjson parentId "$parent_id" \
        --arg username "${VOLUNTAS_USER:-}" \
        --arg authToken "${VOLUNTAS_TOKEN:-}" \
        '{invoke_macro: {macroEntityId: $macroId, textArg: $text, parentId: $parentId},
          username: $username, authToken: $authToken}')" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp >/dev/null 2>&1 || true

exit 0
