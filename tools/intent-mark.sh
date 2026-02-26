#!/bin/bash
# Helper script to mark intents as started/completed
# Usage: ./intent-mark.sh <start|complete|done> <intent-id> [server]

ACTION=$1
INTENT_ID=$2
SERVER=${3:-localhost:50051}

if [ -z "$ACTION" ] || [ -z "$INTENT_ID" ]; then
    echo "Usage: $0 <start|complete|done> <intent-id> [server]"
    exit 1
fi

TIMESTAMP=$(date +%s%N)

case $ACTION in
    start)
        grpcurl -plaintext -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"started\", \"fieldType\": 8}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        grpcurl -plaintext -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"started\", \"timestamp_value\": \"$TIMESTAMP\"}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        echo "Started intent $INTENT_ID"
        ;;
    complete)
        grpcurl -plaintext -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"completed\", \"fieldType\": 8}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        grpcurl -plaintext -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"completed\", \"timestamp_value\": \"$TIMESTAMP\"}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        echo "Completed intent $INTENT_ID"
        ;;
    done)
        # Mark as completed with timestamp AND set done=true
        grpcurl -plaintext -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"completed\", \"fieldType\": 8}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        grpcurl -plaintext -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"completed\", \"timestamp_value\": \"$TIMESTAMP\"}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        grpcurl -plaintext -d "{\"add_field\": {\"intentId\": $INTENT_ID, \"fieldName\": \"done\", \"fieldType\": 6}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        grpcurl -plaintext -d "{\"set_field_value\": {\"intentId\": $INTENT_ID, \"fieldName\": \"done\", \"bool_value\": true}}" $SERVER com.intentevolved.IntentService/SubmitOp > /dev/null 2>&1
        echo "Done intent $INTENT_ID"
        ;;
    *)
        echo "Unknown action: $ACTION"
        exit 1
        ;;
esac
