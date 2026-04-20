#!/bin/bash
# =============================================================================
# restart-vss.sh — Restart a Video Streaming Service instance
#
# Usage: restart-vss.sh <NEW_PORT> <INSTANCE_ID>
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-vss.sh <NEW_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-vss-restart-$$}"

source "$(dirname "$0")/env.sh"

echo "[restart-vss] Starting $INSTANCE_ID on port $NEW_PORT"

write_logging_props "$INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.videostreaming.VssMain \
    --port "$NEW_PORT" \
    --storage-url "$STORAGE_URL" \
    >> "$LOG_DIR/$INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-vss] $INSTANCE_ID started with PID=$PID on port $NEW_PORT"
echo $PID > "$LOG_DIR/$INSTANCE_ID.pid"
