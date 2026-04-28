#!/bin/bash
# Write input/output token counts as INT64 fields on an intent.
#
# Usage: ./tools/intent-set-tokens.sh <intent-id> <input-tokens> <output-tokens> [server]
#
# Auth: set VOLUNTAS_USER and VOLUNTAS_TOKEN env vars when the auth module is loaded.

INTENT_ID=$1
INPUT_TOKENS=$2
OUTPUT_TOKENS=$3
SERVER=${4:-localhost:50051}

if [ -z "$INTENT_ID" ] || [ -z "$INPUT_TOKENS" ] || [ -z "$OUTPUT_TOKENS" ]; then
    echo "Usage: $0 <intent-id> <input-tokens> <output-tokens> [server]"
    exit 1
fi

USER="${VOLUNTAS_USER:-}"
TOKEN="${VOLUNTAS_TOKEN:-}"

submit() {
    grpcurl -plaintext -d "$1" "$SERVER" voluntas.v1.IntentService/SubmitOp > /dev/null 2>&1
}

# Add fields (server ignores if already defined)
submit "$(jq -n --argjson id "$INTENT_ID" --arg user "$USER" --arg token "$TOKEN" \
    '{add_field: {intentId: $id, fieldName: "input_tokens", fieldType: 3, description: "Claude API input tokens used"}, username: $user, authToken: $token}')"

submit "$(jq -n --argjson id "$INTENT_ID" --arg user "$USER" --arg token "$TOKEN" \
    '{add_field: {intentId: $id, fieldName: "output_tokens", fieldType: 3, description: "Claude API output tokens used"}, username: $user, authToken: $token}')"

# Set values
submit "$(jq -n --argjson id "$INTENT_ID" --argjson v "$INPUT_TOKENS" \
    --arg user "$USER" --arg token "$TOKEN" \
    '{set_field_value: {intentId: $id, fieldName: "input_tokens", int64_value: $v}, username: $user, authToken: $token}')"

grpcurl -plaintext \
    -d "$(jq -n --argjson id "$INTENT_ID" --argjson v "$OUTPUT_TOKENS" \
        --arg user "$USER" --arg token "$TOKEN" \
        '{set_field_value: {intentId: $id, fieldName: "output_tokens", int64_value: $v}, username: $user, authToken: $token}')" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp

echo "Set tokens on intent $INTENT_ID: input=$INPUT_TOKENS output=$OUTPUT_TOKENS"
