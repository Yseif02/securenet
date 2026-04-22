#!/bin/bash
# =============================================================================
# restart-eps.sh — EPS Raft node failure handler
#
# EPS nodes are STATEFUL (Raft log, leader election). A failed EPS node
# cannot simply be restarted on a new port — the remaining two nodes
# already have a quorum (2 of 3) and are continuing to operate. The
# failed node needs to rejoin on its ORIGINAL Raft port so peers can
# reach it via their configured peer lists.
#
# What this script does:
#   1. Logs the failure clearly
#   2. Attempts to restart on the ORIGINAL port (passed as NEW_PORT by
#      ClusterManager, which in EPS's case should be the original port)
#   3. The restarted node rejoins the Raft cluster as a follower and
#      catches up via log replication from the current leader
#
# Usage: restart-eps.sh <ORIGINAL_API_PORT> <INSTANCE_ID>
# =============================================================================

set -e

API_PORT="${1:?Usage: restart-eps.sh <ORIGINAL_API_PORT> <INSTANCE_ID>}"
INSTANCE_ID="${2:-eps-restart-$$}"

source "$(dirname "$0")/env.sh"

# Determine node config from instance ID
case "$INSTANCE_ID" in
    eps-1*)
        NODE_ID="eps-1"
        RAFT_PORT=9013
        PEERS="http://localhost:9023,http://localhost:9033"
        ;;
    eps-2*)
        NODE_ID="eps-2"
        RAFT_PORT=9023
        PEERS="http://localhost:9013,http://localhost:9033"
        ;;
    eps-3*)
        NODE_ID="eps-3"
        RAFT_PORT=9033
        PEERS="http://localhost:9013,http://localhost:9023"
        ;;
    *)
        echo "[restart-eps] ERROR: Unknown EPS instance '$INSTANCE_ID'. Manual restart required."
        echo "[restart-eps] The remaining 2 EPS nodes have quorum and are continuing to operate."
        exit 1
        ;;
esac

echo "[restart-eps] Restarting $NODE_ID on API port $API_PORT, Raft port $RAFT_PORT"
echo "[restart-eps] Note: node will rejoin as FOLLOWER and catch up via log replication"

write_logging_props "$INSTANCE_ID-restarted"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.eventprocessing.EpsMain \
    --node-id "$NODE_ID" \
    --api-port "$API_PORT" \
    --raft-port "$RAFT_PORT" \
    --storage-url "$STORAGE_URL" \
    --dms-urls "$DMS_URL" \
    --cluster-manager-url "$CLUSTER_MANAGER_URL" \
    --peers "$PEERS" \
    >> "$LOG_DIR/$INSTANCE_ID-restarted.log" 2>&1 &

PID=$!
echo "[restart-eps] $NODE_ID restarted with PID=$PID"
echo $PID > "$LOG_DIR/$INSTANCE_ID-restarted.pid"