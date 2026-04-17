#!/bin/bash
# =============================================================================
# SecureNet Platform — Start all distributed services
#
# Starts the full SecureNet platform with:
#   - PostgreSQL replication cluster (primary + 2 standbys)
#   - Embedded MQTT broker + IDFS
#   - Storage Service
#   - 3x UMS, DMS, Notification, VSS instances (stateless, load balanced)
#   - 3x EPS Raft cluster nodes (stateful, leader-elected)
#   - API Gateway (client-facing, routes via load balancers)
#   - Cluster Manager (monitors all instances, detects failures)
#
# Prerequisites:
#   - PostgreSQL cluster: ./scripts/setup-postgres-cluster.sh
#   - Build: mvn clean package -DskipTests
#
# Usage:
#   ./scripts/start-platform.sh
#   ./scripts/stop-platform.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
PID_DIR="$PROJECT_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

# Build classpath from packaged JARs + dependencies
CLASSPATH=""
for jar in "$PROJECT_DIR"/securenet-*/target/*.jar \
           "$PROJECT_DIR"/securenet-*/target/dependency/*.jar \
           "$PROJECT_DIR"/demo/target/*.jar \
           "$PROJECT_DIR"/demo/target/dependency/*.jar; do
    [ -e "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done
CLASSPATH="${CLASSPATH#:}"

if [ -z "$CLASSPATH" ]; then
    echo "No JARs found. Run: mvn clean package -DskipTests"
    exit 1
fi

start_service() {
    local name=$1
    local main_class=$2
    shift 2

    echo "  Starting $name..."
    java -cp "$CLASSPATH" "$main_class" "$@" \
        > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$PID_DIR/$name.pid"
    echo "    PID: $(cat "$PID_DIR/$name.pid")"
}

echo "=== Starting SecureNet Platform ==="
echo ""

# =====================================================================
# 1. PostgreSQL Cluster
# =====================================================================
echo "--- PostgreSQL Cluster ---"
"$SCRIPT_DIR/start-postgres-cluster.sh"
sleep 1

STORAGE_URL="http://localhost:9000"

# =====================================================================
# 2. MQTT Broker + IDFS
# =====================================================================
echo ""
echo "--- IDFS + MQTT Broker ---"
start_service "idfs" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8080 --mqtt-port 1883 \
    --dms-url http://localhost:9002 \
    --eps-url http://localhost:9003
sleep 2

# =====================================================================
# 3. Storage Service
# =====================================================================
echo ""
echo "--- Storage Service ---"
start_service "storage" "com.securenet.storage.StorageMain" \
    --port 9000 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet
sleep 1

# =====================================================================
# 4. User Management Service (3 instances)
# =====================================================================
echo ""
echo "--- User Management Service (3 instances) ---"
start_service "ums-1" "com.securenet.usermanagement.UmsMain" \
    --port 9001 --storage-url $STORAGE_URL
sleep 0.3
start_service "ums-2" "com.securenet.usermanagement.UmsMain" \
    --port 9011 --storage-url $STORAGE_URL
sleep 0.3
start_service "ums-3" "com.securenet.usermanagement.UmsMain" \
    --port 9021 --storage-url $STORAGE_URL
sleep 0.3

# =====================================================================
# 5. Device Management Service (3 instances)
# =====================================================================
echo ""
echo "--- Device Management Service (3 instances) ---"
start_service "dms-1" "com.securenet.devicemanagement.DmsMain" \
    --port 9002 --storage-url $STORAGE_URL --idfs-url http://localhost:8080
sleep 0.3
start_service "dms-2" "com.securenet.devicemanagement.DmsMain" \
    --port 9012 --storage-url $STORAGE_URL --idfs-url http://localhost:8080
sleep 0.3
start_service "dms-3" "com.securenet.devicemanagement.DmsMain" \
    --port 9022 --storage-url $STORAGE_URL --idfs-url http://localhost:8080
sleep 0.3

# =====================================================================
# 6. EPS Raft Cluster (3 stateful nodes)
# =====================================================================
echo ""
echo "--- Event Processing Service (3-node Raft cluster) ---"
start_service "eps-1" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-1 --api-port 9003 --raft-port 9013 \
    --storage-url $STORAGE_URL \
    --peers http://localhost:9023,http://localhost:9033
sleep 0.5
start_service "eps-2" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-2 --api-port 9103 --raft-port 9023 \
    --storage-url $STORAGE_URL \
    --peers http://localhost:9013,http://localhost:9033
sleep 0.5
start_service "eps-3" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-3 --api-port 9203 --raft-port 9033 \
    --storage-url $STORAGE_URL \
    --peers http://localhost:9013,http://localhost:9023
sleep 1

# =====================================================================
# 7. Notification Service (3 instances)
# =====================================================================
echo ""
echo "--- Notification Service (3 instances) ---"
start_service "notify-1" "com.securenet.notification.NotificationMain" \
    --port 9004 --storage-url $STORAGE_URL
sleep 0.3
start_service "notify-2" "com.securenet.notification.NotificationMain" \
    --port 9014 --storage-url $STORAGE_URL
sleep 0.3
start_service "notify-3" "com.securenet.notification.NotificationMain" \
    --port 9024 --storage-url $STORAGE_URL
sleep 0.3

# =====================================================================
# 8. Video Streaming Service (3 instances)
# =====================================================================
echo ""
echo "--- Video Streaming Service (3 instances) ---"
start_service "vss-1" "com.securenet.videostreaming.VssMain" \
    --port 9005 --storage-url $STORAGE_URL
sleep 0.3
start_service "vss-2" "com.securenet.videostreaming.VssMain" \
    --port 9015 --storage-url $STORAGE_URL
sleep 0.3
start_service "vss-3" "com.securenet.videostreaming.VssMain" \
    --port 9025 --storage-url $STORAGE_URL
sleep 0.3

# =====================================================================
# 9. API Gateway
# =====================================================================
echo ""
echo "--- API Gateway ---"
start_service "gateway" "com.securenet.gateway.GatewayMain" \
    --port 8443 \
    --ums-urls http://localhost:9001,http://localhost:9011,http://localhost:9021 \
    --dms-urls http://localhost:9002,http://localhost:9012,http://localhost:9022 \
    --eps-urls http://localhost:9003,http://localhost:9103,http://localhost:9203 \
    --notify-urls http://localhost:9004,http://localhost:9014,http://localhost:9024 \
    --vss-urls http://localhost:9005,http://localhost:9015,http://localhost:9025
sleep 0.5

# =====================================================================
# 10. Cluster Manager (monitors all instances)
# =====================================================================
echo ""
echo "--- Cluster Manager ---"
start_service "cluster-manager" "com.securenet.common.ClusterManagerMain" \
    --port 9090 \
    --check-interval 5000 \
    --failure-threshold 15000 \
    --instance IDFS:idfs:http://localhost:8080 \
    --instance Storage:storage:http://localhost:9000 \
    --instance UMS:ums-1:http://localhost:9001 \
    --instance UMS:ums-2:http://localhost:9011 \
    --instance UMS:ums-3:http://localhost:9021 \
    --instance DMS:dms-1:http://localhost:9002 \
    --instance DMS:dms-2:http://localhost:9012 \
    --instance DMS:dms-3:http://localhost:9022 \
    --instance EPS:eps-1:http://localhost:9003 \
    --instance EPS:eps-2:http://localhost:9103 \
    --instance EPS:eps-3:http://localhost:9203 \
    --instance Notification:notify-1:http://localhost:9004 \
    --instance Notification:notify-2:http://localhost:9014 \
    --instance Notification:notify-3:http://localhost:9024 \
    --instance VSS:vss-1:http://localhost:9005 \
    --instance VSS:vss-2:http://localhost:9015 \
    --instance VSS:vss-3:http://localhost:9025 \
    --instance Gateway:gateway:http://localhost:8443
sleep 0.5

# =====================================================================
# Summary
# =====================================================================
echo ""
echo "================================================================"
echo "  SecureNet Platform Running — 21 Java processes"
echo "================================================================"
echo ""
echo "  PostgreSQL Cluster:"
echo "    Primary:       localhost:5432  (read-write)"
echo "    Standby 1:     localhost:5433  (read-only)"
echo "    Standby 2:     localhost:5434  (read-only)"
echo ""
echo "  Infrastructure:"
echo "    MQTT Broker:   localhost:1883"
echo "    IDFS:          localhost:8080"
echo "    Storage:       localhost:9000"
echo "    API Gateway:   localhost:8443"
echo "    Cluster Mgr:   localhost:9090"
echo ""
echo "  Service Clusters (3 instances each, load balanced by Gateway):"
echo "    UMS:           localhost:9001, 9011, 9021"
echo "    DMS:           localhost:9002, 9012, 9022"
echo "    Notification:  localhost:9004, 9014, 9024"
echo "    VSS:           localhost:9005, 9015, 9025"
echo ""
echo "  EPS Raft Cluster (stateful, leader-elected):"
echo "    API:           localhost:9003, 9103, 9203"
echo "    Raft RPC:      localhost:9013, 9023, 9033"
echo ""
echo "  Commands:"
echo "    Raft status:     curl http://localhost:9013/raft/status"
echo "    Cluster health:  curl http://localhost:9090/cluster/status"
echo "    Run demo:        java -cp \"\$CP\" com.securenet.demo.PlatformDemo"
echo "    Stop all:        ./scripts/stop-platform.sh"
echo "================================================================"