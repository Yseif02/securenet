#!/bin/bash
# =============================================================================
# env.sh — Common environment for SecureNet restart scripts
#
# Sourced by all per-service restart scripts. Sets CLASSPATH, STORAGE_URL,
# and LOG_DIR so they don't have to be repeated in every script.
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CODE_DIR="$PROJECT_DIR"

# Build classpath from all packaged JARs
CLASSPATH=""
for jar in "$CODE_DIR"/securenet-*/target/*.jar \
           "$CODE_DIR"/securenet-*/target/dependency/*.jar \
           "$CODE_DIR"/demo/target/*.jar \
           "$CODE_DIR"/demo/target/dependency/*.jar; do
    [ -e "$jar" ] && CLASSPATH="$CLASSPATH:$jar"
done
CLASSPATH="${CLASSPATH#:}"

# Storage URLs — all three instances, load balanced
STORAGE_URL="http://localhost:9000,http://localhost:9010,http://localhost:9020"
STORAGE_JDBC_URL="jdbc:postgresql://localhost:5432,localhost:5433,localhost:5434/securenet?targetServerType=primary"

# IDFS URLs — all three instances, load balanced
IDFS_URL="http://localhost:8080,http://localhost:8081,http://localhost:8082"

# DMS URLs
DMS_URL="http://localhost:9002,http://localhost:9012,http://localhost:9022"

# EPS URLs — all three, load balanced
EPS_URL="http://localhost:9003,http://localhost:9103,http://localhost:9203"
EPS_API_URLS="eps-1=http://localhost:9003,eps-2=http://localhost:9103,eps-3=http://localhost:9203"

# VSS URLs — all three instances, load balanced
VSS_URL="http://localhost:9005,http://localhost:9015,http://localhost:9025"

# MQTT broker — standalone process on 1883
MQTT_URL="tcp://localhost:1883"

# Cluster Manager — used by services for dynamic instance discovery
CLUSTER_MANAGER_URL="http://localhost:9090,http://localhost:9091,http://localhost:9092"

# Log directory — passed in from ClusterManager as LOG_DIR env var,
# falls back to logs/latest if not set
LOG_DIR="${LOG_DIR:-$PROJECT_DIR/logs/latest}"

# Helper: write a JUL logging.properties file for the restarted instance
# Usage: write_logging_props <name>
# Sets LOG_PROPS_FILE for use in the java command
write_logging_props() {
    local name=$1
    local log_file="$LOG_DIR/$name.log"
    LOG_PROPS_FILE="$LOG_DIR/$name-logging.properties"
    cat > "$LOG_PROPS_FILE" <<EOF
handlers=java.util.logging.FileHandler
java.util.logging.FileHandler.pattern=$log_file
java.util.logging.FileHandler.formatter=com.securenet.common.LogFormatter
java.util.logging.FileHandler.append=true
java.util.logging.FileHandler.limit=50000000
.level=INFO
sun.net.level=WARNING
io.netty.level=WARNING
io.moquette.level=WARNING
com.zaxxer.hikari.level=WARNING
EOF
}
