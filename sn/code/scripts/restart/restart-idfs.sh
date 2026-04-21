#!/bin/bash
# =============================================================================
# restart-idfs.sh — Restart an IDFS instance (no-broker mode)
#
# Only idfs-2 and idfs-3 are restartable this way (they run without the
# embedded broker). idfs-1 hosts the MQTT broker — if it fails, the broker
# is also down, which requires manual intervention.
#
# Usage: restart-idfs.sh <NEW_PORT> <INSTANCE_ID>
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-idfs.sh <NEW_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-idfs-restart-$$}"

source "$(dirname "$0")/env.sh"

echo "[restart-idfs] Starting $INSTANCE_ID on port $NEW_PORT (no-broker mode)"

write_logging_props "$INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.iotfirmware.IdfsMain \
    --http-port "$NEW_PORT" \
    --no-broker \
    --dms-urls "$DMS_URL" \
    --eps-url "$EPS_URL" \
    --storage-url "$STORAGE_URL" \
    --mqtt-broker-url "$MQTT_URL" \
    >> "$LOG_DIR/$INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-idfs] $INSTANCE_ID started with PID=$PID on port $NEW_PORT"
echo $PID > "$LOG_DIR/$INSTANCE_ID.pid"
