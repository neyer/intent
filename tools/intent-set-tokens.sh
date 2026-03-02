#!/bin/bash
# Write input/output token counts as INT64 fields on an intent.
#
# Usage: ./tools/intent-set-tokens.sh <intent-id> <input-tokens> <output-tokens> [server]

INTENT_ID=$1
INPUT_TOKENS=$2
OUTPUT_TOKENS=$3
SERVER=${4:-localhost:50051}

if [ -z "$INTENT_ID" ] || [ -z "$INPUT_TOKENS" ] || [ -z "$OUTPUT_TOKENS" ]; then
    echo "Usage: $0 <intent-id> <input-tokens> <output-tokens> [server]"
    exit 1
fi

# Add fields (silently; server ignores if already defined)
grpcurl -plaintext \
    -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"input_tokens\", \"fieldType\": 3, \"description\": \"Claude API input tokens used\"}}" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp > /dev/null 2>&1

grpcurl -plaintext \
    -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"output_tokens\", \"fieldType\": 3, \"description\": \"Claude API output tokens used\"}}" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp > /dev/null 2>&1

# Set values
grpcurl -plaintext \
    -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"input_tokens\", \"int64_value\": $INPUT_TOKENS}}" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp > /dev/null 2>&1

grpcurl -plaintext \
    -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"output_tokens\", \"int64_value\": $OUTPUT_TOKENS}}" \
    "$SERVER" voluntas.v1.IntentService/SubmitOp

echo "Set tokens on intent $INTENT_ID: input=$INPUT_TOKENS output=$OUTPUT_TOKENS"
