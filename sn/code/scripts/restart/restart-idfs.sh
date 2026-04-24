#!/bin/bash
# =============================================================================
# restart-idfs.sh — Restart an IDFS instance
#
# All three IDFS instances are symmetric — none embeds the MQTT broker,
# so all are equally restartable by the ClusterManager.
#
# Usage (called by ClusterManager):
#   restart-idfs.sh <NEW_PORT> <NEW_INSTANCE_ID> <ORIGINAL_INSTANCE_ID>
#
# $1  NEW_PORT           — free port chosen by ClusterManager
# $2  NEW_INSTANCE_ID    — e.g. "idfs-1-r1"
# $3  ORIGINAL_INSTANCE_ID — e.g. "idfs-1" (stable across restarts; used to
#                            derive the slot index so ownership is preserved)
#
# Slot index derivation:
#   "idfs-1" → index 0   (original 1-based ID minus 1)
#   "idfs-2" → index 1
#   "idfs-3" → index 2
#   "idfs-1-r1", "idfs-1-r2", ... → still index 0 (strip suffix)
#
# IDFS_CLUSTER_SIZE is read from the environment (set by ClusterManager).
# =============================================================================

set -e

NEW_PORT="${1:?Usage: restart-idfs.sh <NEW_PORT> <NEW_INSTANCE_ID> <ORIGINAL_INSTANCE_ID>}"
NEW_INSTANCE_ID="${2:-idfs-restart-$$}"
ORIGINAL_INSTANCE_ID="${3:-$NEW_INSTANCE_ID}"

# Default cluster size to 3 if not set by ClusterManager
IDFS_CLUSTER_SIZE="${IDFS_CLUSTER_SIZE:-3}"

# Derive stable slot index from the *original* instance ID.
# Strip any restart suffix (e.g. "idfs-2-r3" → "idfs-2") then extract the number.
BASE_ID="${ORIGINAL_INSTANCE_ID%%-r*}"          # drop "-r1", "-r2", etc.
INSTANCE_NUM="${BASE_ID##*-}"                   # last segment after final "-"
INSTANCE_INDEX=$(( INSTANCE_NUM - 1 ))          # convert 1-based to 0-based

source "$(dirname "$0")/env.sh"

echo "[restart-idfs] Starting $NEW_INSTANCE_ID on port $NEW_PORT"
echo "[restart-idfs] Original ID: $ORIGINAL_INSTANCE_ID → slot $INSTANCE_INDEX / $IDFS_CLUSTER_SIZE"

write_logging_props "$NEW_INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.iotfirmware.IdfsMain \
    --http-port         "$NEW_PORT" \
    --mqtt-broker-url   "$MQTT_URL" \
    --dms-urls          "$DMS_URL" \
    --eps-urls          "$EPS_URL" \
    --vss-urls          "$VSS_URL" \
    --storage-url       "$STORAGE_URL" \
    --cluster-manager-url "$CLUSTER_MANAGER_URL" \
    --instance-index    "$INSTANCE_INDEX" \
    --idfs-cluster-size "$IDFS_CLUSTER_SIZE" \
    >> "$LOG_DIR/$NEW_INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-idfs] $NEW_INSTANCE_ID started with PID=$PID on port $NEW_PORT (slot $INSTANCE_INDEX)"
echo $PID > "$LOG_DIR/$NEW_INSTANCE_ID.pid"
