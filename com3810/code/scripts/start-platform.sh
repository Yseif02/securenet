#!/bin/bash
# =============================================================================
# SecureNet Platform — Start all distributed services
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESTART_DIR="$SCRIPT_DIR/restart"

RUN_TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
LOG_DIR="$PROJECT_DIR/logs/run_$RUN_TIMESTAMP"
PID_DIR="$PROJECT_DIR/pids"

mkdir -p "$LOG_DIR" "$PID_DIR"
rm -f "$PID_DIR"/*.pid
ln -sfn "$LOG_DIR" "$PROJECT_DIR/logs/latest"

echo "Logs directory: $LOG_DIR"

chmod +x "$RESTART_DIR"/*.sh

# Build classpath
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

    java -cp "$CLASSPATH" \
        -Djava.util.logging.config.file="$props_file" \
        -Dinstance.name="$name" \
        "$main_class" "$@" \
        >> "$log_file" 2>&1 &
    echo $! > "$PID_DIR/$name.pid"
    echo "    Started $name  PID=$(cat "$PID_DIR/$name.pid")  log: logs/run_$RUN_TIMESTAMP/$name.log"
}

echo "=== Starting SecureNet Platform ==="
echo ""

STORAGE_URL="http://localhost:9000,http://localhost:9010,http://localhost:9020"
DMS_URLS="http://localhost:9002,http://localhost:9012,http://localhost:9022"
EPS_URLS="http://localhost:9003,http://localhost:9103,http://localhost:9203"
VSS_URLS="http://localhost:9005,http://localhost:9015,http://localhost:9025"
IDFS_URLS="http://localhost:8080,http://localhost:8081,http://localhost:8082"
MQTT_URL="tcp://localhost:1883"

# =====================================================================
# 1. PostgreSQL Cluster
# =====================================================================
echo "--- PostgreSQL Cluster ---"
"$SCRIPT_DIR/start-postgres-cluster.sh"
sleep 1

# =====================================================================
# 2. Standalone MQTT Broker
# =====================================================================
echo ""
echo "--- MQTT Broker (standalone) ---"
start_service "mqtt-broker" "com.securenet.iotfirmware.mqtt.MqttBrokerMain" \
    --host 0.0.0.0 --port 1883
sleep 1  # broker must be ready before IDFS connects

# =====================================================================
# 3. Storage Service (3 instances)
# =====================================================================
echo ""
echo "--- Storage Service (3 instances) ---"
start_service "storage-1" "com.securenet.storage.StorageMain" \
    --port 9000 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
sleep 1
start_service "storage-2" "com.securenet.storage.StorageMain" \
    --port 9010 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
sleep 1
start_service "storage-3" "com.securenet.storage.StorageMain" \
    --port 9020 \
    --jdbc-url jdbc:postgresql://localhost:5432/securenet \
    --pool-size 10
sleep 1

# =====================================================================
# 4. IDFS (3 symmetric instances — all connect to standalone broker)
# =====================================================================
echo ""
echo "--- IDFS (3 symmetric instances) ---"
start_service "idfs-1" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8080 \
    --mqtt-broker-url "$MQTT_URL" \
    --dms-urls "$DMS_URLS" \
    --eps-urls "$EPS_URLS" \
    --vss-urls "$VSS_URLS" \
    --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090 \
    --instance-index 0 \
    --idfs-cluster-size 3
sleep 0.3
start_service "idfs-2" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8081 \
    --mqtt-broker-url "$MQTT_URL" \
    --dms-urls "$DMS_URLS" \
    --eps-urls "$EPS_URLS" \
    --vss-urls "$VSS_URLS" \
    --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090 \
    --instance-index 1 \
    --idfs-cluster-size 3
sleep 0.3
start_service "idfs-3" "com.securenet.iotfirmware.IdfsMain" \
    --http-port 8082 \
    --mqtt-broker-url "$MQTT_URL" \
    --dms-urls "$DMS_URLS" \
    --eps-urls "$EPS_URLS" \
    --vss-urls "$VSS_URLS" \
    --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090 \
    --instance-index 2 \
    --idfs-cluster-size 3
sleep 2

# =====================================================================
# 5. User Management Service (3 instances)
# =====================================================================
echo ""
echo "--- User Management Service (3 instances) ---"
start_service "ums-1" "com.securenet.usermanagement.UmsMain" \
    --port 9001 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "ums-2" "com.securenet.usermanagement.UmsMain" \
    --port 9011 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "ums-3" "com.securenet.usermanagement.UmsMain" \
    --port 9021 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3

# =====================================================================
# 6. Device Management Service (3 instances)
# =====================================================================
echo ""
echo "--- Device Management Service (3 instances) ---"
start_service "dms-1" "com.securenet.devicemanagement.DmsMain" \
    --port 9002 --storage-url "$STORAGE_URL" \
    --idfs-url "$IDFS_URLS" \
    --vss-urls "$VSS_URLS" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "dms-2" "com.securenet.devicemanagement.DmsMain" \
    --port 9012 --storage-url "$STORAGE_URL" \
    --idfs-url "$IDFS_URLS" \
    --vss-urls "$VSS_URLS" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "dms-3" "com.securenet.devicemanagement.DmsMain" \
    --port 9022 --storage-url "$STORAGE_URL" \
    --idfs-url "$IDFS_URLS" \
    --vss-urls "$VSS_URLS" \
    --cluster-manager-url http://localhost:9090
sleep 0.3

# =====================================================================
# 7. EPS Raft Cluster (3 stateful nodes)
# =====================================================================
echo ""
echo "--- Event Processing Service (3-node Raft cluster) ---"
start_service "eps-1" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-1 --api-port 9003 --raft-port 9013 \
    --storage-url "$STORAGE_URL" \
    --dms-urls "$DMS_URLS" \
    --cluster-manager-url http://localhost:9090 \
    --peers http://localhost:9023,http://localhost:9033
sleep 0.5
start_service "eps-2" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-2 --api-port 9103 --raft-port 9023 \
    --storage-url "$STORAGE_URL" \
    --dms-urls "$DMS_URLS" \
    --cluster-manager-url http://localhost:9090 \
    --peers http://localhost:9013,http://localhost:9033
sleep 0.5
start_service "eps-3" "com.securenet.eventprocessing.EpsMain" \
    --node-id eps-3 --api-port 9203 --raft-port 9033 \
    --storage-url "$STORAGE_URL" \
    --dms-urls "$DMS_URLS" \
    --cluster-manager-url http://localhost:9090 \
    --peers http://localhost:9013,http://localhost:9023
sleep 1

# =====================================================================
# 8. Notification Service (3 instances)
# =====================================================================
echo ""
echo "--- Notification Service (3 instances) ---"
start_service "notify-1" "com.securenet.notification.NotificationMain" \
    --port 9004 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "notify-2" "com.securenet.notification.NotificationMain" \
    --port 9014 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "notify-3" "com.securenet.notification.NotificationMain" \
    --port 9024 --storage-url "$STORAGE_URL" \
    --cluster-manager-url http://localhost:9090
sleep 0.3

# =====================================================================
# 9. Video Streaming Service (3 instances)
# =====================================================================
echo ""
echo "--- Video Streaming Service (3 instances) ---"
start_service "vss-1" "com.securenet.videostreaming.VssMain" \
    --port 9005 --storage-url "$STORAGE_URL" \
    --self-url http://localhost:9005 \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "vss-2" "com.securenet.videostreaming.VssMain" \
    --port 9015 --storage-url "$STORAGE_URL" \
    --self-url http://localhost:9015 \
    --cluster-manager-url http://localhost:9090
sleep 0.3
start_service "vss-3" "com.securenet.videostreaming.VssMain" \
    --port 9025 --storage-url "$STORAGE_URL" \
    --self-url http://localhost:9025 \
    --cluster-manager-url http://localhost:9090
sleep 0.3

# =====================================================================
# 10. API Gateway
# =====================================================================
echo ""
echo "--- API Gateway ---"
start_service "gateway" "com.securenet.gateway.GatewayMain" \
    --port 8443 \
    --ums-urls http://localhost:9001,http://localhost:9011,http://localhost:9021 \
    --dms-urls "$DMS_URLS" \
    --eps-urls "$EPS_URLS" \
    --notify-urls http://localhost:9004,http://localhost:9014,http://localhost:9024 \
    --vss-urls "$VSS_URLS" \
    --cluster-manager-url http://localhost:9090
sleep 0.5

# =====================================================================
# 11. Cluster Manager
# =====================================================================
echo ""
echo "--- Cluster Manager ---"
start_service "cluster-manager" "com.securenet.common.ClusterManagerMain" \
    --port 9090 \
    --check-interval 5000 \
    --failure-threshold 15000 \
    --scripts-dir "$RESTART_DIR" \
    --log-dir "$LOG_DIR" \
    --initial-delay 30000 \
    --instance "MqttBroker:mqtt-broker:http://localhost:1884|restart-mqtt-broker.sh" \
    --instance "IDFS:idfs-1:http://localhost:8080|restart-idfs.sh" \
    --instance "IDFS:idfs-2:http://localhost:8081|restart-idfs.sh" \
    --instance "IDFS:idfs-3:http://localhost:8082|restart-idfs.sh" \
    --instance "Storage:storage-1:http://localhost:9000|restart-storage.sh" \
    --instance "Storage:storage-2:http://localhost:9010|restart-storage.sh" \
    --instance "Storage:storage-3:http://localhost:9020|restart-storage.sh" \
    --instance "UMS:ums-1:http://localhost:9001|restart-ums.sh" \
    --instance "UMS:ums-2:http://localhost:9011|restart-ums.sh" \
    --instance "UMS:ums-3:http://localhost:9021|restart-ums.sh" \
    --instance "DMS:dms-1:http://localhost:9002|restart-dms.sh" \
    --instance "DMS:dms-2:http://localhost:9012|restart-dms.sh" \
    --instance "DMS:dms-3:http://localhost:9022|restart-dms.sh" \
    --instance "EPS:eps-1:http://localhost:9003|restart-eps.sh" \
    --instance "EPS:eps-2:http://localhost:9103|restart-eps.sh" \
    --instance "EPS:eps-3:http://localhost:9203|restart-eps.sh" \
    --instance "Notification:notify-1:http://localhost:9004|restart-notification.sh" \
    --instance "Notification:notify-2:http://localhost:9014|restart-notification.sh" \
    --instance "Notification:notify-3:http://localhost:9024|restart-notification.sh" \
    --instance "VSS:vss-1:http://localhost:9005|restart-vss.sh" \
    --instance "VSS:vss-2:http://localhost:9015|restart-vss.sh" \
    --instance "VSS:vss-3:http://localhost:9025|restart-vss.sh" \
    --instance "Gateway:gateway:http://localhost:8443"
sleep 0.5

# =====================================================================
# Summary
# =====================================================================
echo ""
echo "================================================================"
echo "  SecureNet Platform Running — 28 Java processes"
echo "================================================================"
echo ""
echo "  PostgreSQL Cluster:"
echo "    Primary:       localhost:5432  (read-write)"
echo "    Standby 1:     localhost:5433  (read-only)"
echo "    Standby 2:     localhost:5434  (read-only)"
echo ""
echo "  Infrastructure:"
echo "    MQTT Broker:   localhost:1883  (standalone, auto-restart)"
echo "    API Gateway:   localhost:8443"
echo "    Cluster Mgr:   localhost:9090"
echo ""
echo "  Service Clusters (3 instances, load balanced, auto-restart):"
echo "    IDFS:          localhost:8080, 8081, 8082  (all symmetric)"
echo "    Storage:       localhost:9000, 9010, 9020  (HikariCP pool x3)"
echo "    UMS:           localhost:9001, 9011, 9021"
echo "    DMS:           localhost:9002, 9012, 9022"
echo "    Notification:  localhost:9004, 9014, 9024"
echo "    VSS:           localhost:9005, 9015, 9025"
echo ""
echo "  EPS Raft Cluster (stateful, auto-restart on original ports):"
echo "    API:           localhost:9003, 9103, 9203"
echo "    Raft RPC:      localhost:9013, 9023, 9033"
echo ""
echo "  Restart scripts: $RESTART_DIR"
echo "  Logs:            $LOG_DIR"
echo "        (symlinked as logs/latest)"
echo ""
echo "  Commands:"
echo "    Raft status:     curl http://localhost:9013/raft/status"
echo "    Cluster health:  curl http://localhost:9090/cluster/status"
echo "    Stop all:        ./scripts/stop-platform.sh"
echo "================================================================"