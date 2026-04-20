#!/bin/bash
# =============================================================================
# SecureNet Platform — Start all distributed services
#
# Starts the full SecureNet platform with:
#   - PostgreSQL replication cluster (primary + 2 standbys)
#   - Embedded MQTT broker + 3x IDFS
#   - 3x Storage Service (HikariCP pool, load balanced)
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

# Each run gets its own timestamped directory
RUN_TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
LOG_DIR="$PROJECT_DIR/logs/run_$RUN_TIMESTAMP"
PID_DIR="$PROJECT_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"

# Write a symlink logs/latest -> current run for convenience
ln -sfn "$LOG_DIR" "$PROJECT_DIR/logs/latest"

echo "Logs directory: $LOG_DIR"

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

# -----------------------------------------------------------------------------
# start_service <name> <main_class> [args...]
# -----------------------------------------------------------------------------
start_service() {
    local name=$1
    local main_class=$2
    shift 2

    local log_file="$LOG_DIR/$name.log"
    local props_file="$LOG_DIR/$name-logging.properties"
    cat > "$props_file" <<EOF
handlers=java.util.logging.FileHandler
java.util.logging.FileHandler.pattern=$log_file
java.util.logging.FileHandler.formatter=com.securenet.common.LogFormatter
java.util.logging.FileHandler.append=false
java.util.logging.FileHandler.limit=50000000
.level=INFO
sun.net.level=WARNING
io.netty.level=WARNING
io.moquette.level=WARNING
com.zaxxer.hikari.level=WARNING
EOF

    echo "  Starting $name..."
    java -cp "$CLASSPATH" \
        -Djava.util.logging.config.file="$props_file" \
        "$main_class" "$@" \
        >> "$log_file" 2>&1 &
    echo $! > "$PID_DIR/$name.pid"
    echo "    PID: $(cat "$PID_DIR/$name.pid")  log: logs/run_$RUN_TIMESTAMP/$name.log"
}

echo "=== Starting SecureNet Platform ==="
echo ""

# =====================================================================
# 1. PostgreSQL Cluster
# =====================================================================
echo "--- PostgreSQL Cluster ---"
"$SCRIPT_DIR/start-postgres-cluster.sh"
sleep 1

# All three storage instances + their load-balanced URL for downstream services
STORAGE_URL="http://localhost:9000,http://localhost:9010,http://localhost:9020"

# =====================================================================
# 2. MQTT Broker + IDFS (3 instances)
# =====================================================================
echo ""
echo "--- IDFS + MQTT Broker (3 instances) ---"
start_service "idfs-1" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8080 --mqtt-port 1883 \
    --dms-url http://localhost:9002 \
    --eps-url http://localhost:9003 \
    --storage-url $STORAGE_URL
sleep 0.3
start_service "idfs-2" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8081 --no-broker \
    --dms-url http://localhost:9002 \
    --eps-url http://localhost:9003 \
    --storage-url $STORAGE_URL \
    --mqtt-broker-url tcp://localhost:1883
sleep 0.3
start_service "idfs-3" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8082 --no-broker \
    --dms-url http://localhost:9002 \
    --eps-url http://localhost:9003 \
    --storage-url $STORAGE_URL \
    --mqtt-broker-url tcp://localhost:1883
sleep 2

# =====================================================================
# 3. Storage Service (3 instances, HikariCP pool, load balanced)
# =====================================================================
echo ""
echo "--- Storage Service (3 instances) ---"
start_service "storage-1" "com.securenet.storage.StorageMain" \
    --port 9000 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
sleep 0.3
start_service "storage-2" "com.securenet.storage.StorageMain" \
    --port 9010 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
sleep 0.3
start_service "storage-3" "com.securenet.storage.StorageMain" \
    --port 9020 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
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
    --port 9002 --storage-url $STORAGE_URL \
    --idfs-url http://localhost:8080,http://localhost:8081,http://localhost:8082
sleep 0.3
start_service "dms-2" "com.securenet.devicemanagement.DmsMain" \
    --port 9012 --storage-url $STORAGE_URL \
    --idfs-url http://localhost:8080,http://localhost:8081,http://localhost:8082
sleep 0.3
start_service "dms-3" "com.securenet.devicemanagement.DmsMain" \
    --port 9022 --storage-url $STORAGE_URL \
    --idfs-url http://localhost:8080,http://localhost:8081,http://localhost:8082
sleep 0.3

# =====================================================================
# 6. EPS Raft Cluster (3 stateful nodes)
# =====================================================================
echo ""
echo "--- Event Processing Service (3-node Raft cluster) ---"
start_service "eps-1" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-1 --api-port 9003 --raft-port 9013 \
    --storage-url $STORAGE_URL \
    --dms-url http://localhost:9002 \
    --peers http://localhost:9023,http://localhost:9033
sleep 0.5
start_service "eps-2" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-2 --api-port 9103 --raft-port 9023 \
    --storage-url $STORAGE_URL \
    --dms-url http://localhost:9002 \
    --peers http://localhost:9013,http://localhost:9033
sleep 0.5
start_service "eps-3" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-3 --api-port 9203 --raft-port 9033 \
    --storage-url $STORAGE_URL \
    --dms-url http://localhost:9002 \
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
    --instance IDFS:idfs-1:http://localhost:8080 \
    --instance IDFS:idfs-2:http://localhost:8081 \
    --instance IDFS:idfs-3:http://localhost:8082 \
    --instance Storage:storage-1:http://localhost:9000 \
    --instance Storage:storage-2:http://localhost:9010 \
    --instance Storage:storage-3:http://localhost:9020 \
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
echo "  SecureNet Platform Running — 27 Java processes"
echo "================================================================"
echo ""
echo "  PostgreSQL Cluster:"
echo "    Primary:       localhost:5432  (read-write)"
echo "    Standby 1:     localhost:5433  (read-only)"
echo "    Standby 2:     localhost:5434  (read-only)"
echo ""
echo "  Infrastructure:"
echo "    MQTT Broker:   localhost:1883"
echo "    API Gateway:   localhost:8443"
echo "    Cluster Mgr:   localhost:9090"
echo ""
echo "  Service Clusters (3 instances each, load balanced):"
echo "    IDFS:          localhost:8080, 8081, 8082"
echo "    Storage:       localhost:9000, 9010, 9020  (HikariCP pool x3)"
echo "    UMS:           localhost:9001, 9011, 9021"
echo "    DMS:           localhost:9002, 9012, 9022"
echo "    Notification:  localhost:9004, 9014, 9024"
echo "    VSS:           localhost:9005, 9015, 9025"
echo ""
echo "  EPS Raft Cluster (stateful, leader-elected):"
echo "    API:           localhost:9003, 9103, 9203"
echo "    Raft RPC:      localhost:9013, 9023, 9033"
echo ""
echo "  Logs: $LOG_DIR"
echo "        (symlinked as logs/latest)"
echo ""
echo "  Commands:"
echo "    Raft status:     curl http://localhost:9013/raft/status"
echo "    Cluster health:  curl http://localhost:9090/cluster/status"
echo "    Run demo:        java -cp \"\$(find . -name '*.jar' -path '*/target/*' \\"
echo "                       | grep -v sources | tr '\n' ':')\" com.securenet.demo.PlatformDemo"
echo "    Stop all:        ./scripts/stop-platform.sh"
echo "================================================================"