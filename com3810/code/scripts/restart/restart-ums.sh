#!/bin/bash
# =============================================================================
# restart-ums.sh — Restart a User Management Service instance on a new port
#
# Usage: restart-ums.sh <NEW_PORT> <INSTANCE_ID>
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-ums.sh <NEW_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-ums-restart-$$}"

source "$(dirname "$0")/env.sh"

echo "[restart-ums] Starting $INSTANCE_ID on port $NEW_PORT"

write_logging_props "$INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.usermanagement.UmsMain \
    --port "$NEW_PORT" \
    --storage-url "$STORAGE_URL" \
    >> "$LOG_DIR/$INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-ums] $INSTANCE_ID started with PID=$PID on port $NEW_PORT"
echo $PID > "$LOG_DIR/$INSTANCE_ID.pid"
