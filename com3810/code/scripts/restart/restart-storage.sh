#!/bin/bash
# =============================================================================
# restart-storage.sh — Restart a Storage Service instance on a new port
#
# Called by ClusterManager when a storage instance is declared FAILED.
# Usage: restart-storage.sh <NEW_PORT> <INSTANCE_ID>
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-storage.sh <NEW_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-storage-restart-$$}"

source "$(dirname "$0")/env.sh"

echo "[restart-storage] Starting $INSTANCE_ID on port $NEW_PORT"

write_logging_props "$INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.storage.StorageMain \
    --port "$NEW_PORT" \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10 \
    >> "$LOG_DIR/$INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-storage] $INSTANCE_ID started with PID=$PID on port $NEW_PORT"
echo $PID > "$LOG_DIR/$INSTANCE_ID.pid"
