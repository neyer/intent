#!/bin/bash
# Add a new intent with the given text, under an optional parent (defaults to root).
# Outputs JSON with the new intent's id on success.
#
# Usage: ./tools/intent-add.sh <text> [parent-id] [server]

TEXT="$1"
PARENT_ID=${2:-0}
SERVER=${3:-localhost:50051}

if [ -z "$TEXT" ]; then
    echo "Usage: $0 <text> [parent-id] [server]"
    exit 1
fi

grpcurl -plaintext \
    -d "$(jq -n --arg text "$TEXT" --argjson parentId "$PARENT_ID" \
        '{create_intent: {text: $text, parentId: $parentId}}')" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp
