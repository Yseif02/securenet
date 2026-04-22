#!/bin/bash
# =============================================================================
# restart-dms.sh — Restart a Device Management Service instance on a new port
#
# Usage: restart-dms.sh <NEW_PORT> <INSTANCE_ID>
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-dms.sh <NEW_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-dms-restart-$$}"

source "$(dirname "$0")/env.sh"

echo "[restart-dms] Starting $INSTANCE_ID on port $NEW_PORT"

write_logging_props "$INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.devicemanagement.DmsMain \
    --port "$NEW_PORT" \
    --storage-url "$STORAGE_URL" \
    --idfs-url "$IDFS_URL" \
    --vss-urls "$VSS_URL" \
    --cluster-manager-url "$CLUSTER_MANAGER_URL" \
    >> "$LOG_DIR/$INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-dms] $INSTANCE_ID started with PID=$PID on port $NEW_PORT"
echo $PID > "$LOG_DIR/$INSTANCE_ID.pid"