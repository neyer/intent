#!/bin/bash
# Read an intent and its immediate children (focal scope).
# Use id=0 for the root intent. Outputs JSON.
#
# Usage: ./tools/intent-get.sh <id> [server]

ID=${1:-0}
SERVER=${2:-localhost:50051}

grpcurl -plaintext -d "{\"id\": $ID}" "$SERVER" voluntas.v1.IntentService/GetFocalScope
