#!/bin/bash
# Helper script to mark intents as started/completed
# Usage: ./intent-mark.sh <start|complete|done> <intent-id> [server]
#
# Auth: set VOLUNTAS_USER and VOLUNTAS_TOKEN env vars when the auth module is loaded.

ACTION=$1
INTENT_ID=$2
SERVER=${3:-localhost:50051}

if [ -z "$ACTION" ] || [ -z "$INTENT_ID" ]; then
    echo "Usage: $0 <start|complete|done> <intent-id> [server]"
    exit 1
fi

TIMESTAMP=$(date +%s%N)
USER="${VOLUNTAS_USER:-}"
TOKEN="${VOLUNTAS_TOKEN:-}"

submit() {
    grpcurl -plaintext -d "$1" "$SERVER" voluntas.v1.IntentService/SubmitOp > /dev/null 2>&1
}

add_timestamp_field() {
    local field="$1"
    submit "$(jq -n --argjson id "$INTENT_ID" --arg field "$field" \
        --arg user "$USER" --arg token "$TOKEN" \
        '{add_field: {intentId: $id, fieldName: $field, fieldType: 8}, username: $user, authToken: $token}')"
    submit "$(jq -n --argjson id "$INTENT_ID" --arg field "$field" \
        --argjson ts "$TIMESTAMP" \
        --arg user "$USER" --arg token "$TOKEN" \
        '{set_field_value: {intentId: $id, fieldName: $field, timestamp_value: $ts}, username: $user, authToken: $token}')"
}

case $ACTION in
    start)
        add_timestamp_field "started"
        echo "Started intent $INTENT_ID"
        ;;
    complete)
        add_timestamp_field "completed"
        echo "Completed intent $INTENT_ID"
        ;;
    done)
        add_timestamp_field "completed"
        submit "$(jq -n --argjson id "$INTENT_ID" \
            --arg user "$USER" --arg token "$TOKEN" \
            '{add_field: {intentId: $id, fieldName: "done", fieldType: 6}, username: $user, authToken: $token}')"
        submit "$(jq -n --argjson id "$INTENT_ID" \
            --arg user "$USER" --arg token "$TOKEN" \
            '{set_field_value: {intentId: $id, fieldName: "done", bool_value: true}, username: $user, authToken: $token}')"
        echo "Done intent $INTENT_ID"
        ;;
    *)
        echo "Unknown action: $ACTION"
        exit 1
        ;;
esac
