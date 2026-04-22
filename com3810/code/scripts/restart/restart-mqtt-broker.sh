#!/bin/bash
# =============================================================================
# restart-mqtt-broker.sh — Restart the standalone MQTT broker
#
# The MQTT broker MUST restart on its original port (1883) because all
# devices and IDFS instances have the broker URL configured at startup and
# cannot discover a new port at runtime.
#
# This means we must kill the old process before launching the new one.
# ClusterManager passes the originalInstanceId as $3, which we use to
# find the PID file written by the previous run.
#
# Usage (called by ClusterManager):
#   restart-mqtt-broker.sh <PORT> <NEW_INSTANCE_ID> <ORIGINAL_INSTANCE_ID>
#
# $1  PORT                  — always 1883 (fixed port for MQTT)
# $2  NEW_INSTANCE_ID       — e.g. "mqtt-broker-r1"
# $3  ORIGINAL_INSTANCE_ID  — e.g. "mqtt-broker" (base name for PID file lookup)
# =============================================================================

set -e

PORT="${1:-1883}"
NEW_INSTANCE_ID="${2:-mqtt-broker-restart-$$}"
ORIGINAL_INSTANCE_ID="${3:-mqtt-broker}"

source "$(dirname "$0")/env.sh"

echo "[restart-mqtt-broker] Restarting $NEW_INSTANCE_ID on port $PORT"
echo "[restart-mqtt-broker] Original ID: $ORIGINAL_INSTANCE_ID"

# -----------------------------------------------------------------------
# Step 1: Kill the previous broker process using its PID file.
# Walk back through the restart chain: try the original ID and any -rN
# suffixes to find a running process.
# -----------------------------------------------------------------------
kill_old_broker() {
    local base="$1"
    # Try original name and up to 10 restart suffixes
    for suffix in "" -r1 -r2 -r3 -r4 -r5 -r6 -r7 -r8 -r9 -r10; do
        local pid_file="$LOG_DIR/${base}${suffix}.pid"
        if [ -f "$pid_file" ]; then
            local old_pid
            old_pid=$(cat "$pid_file" 2>/dev/null || true)
            if [ -n "$old_pid" ] && kill -0 "$old_pid" 2>/dev/null; then
                echo "[restart-mqtt-broker] Killing old broker PID=$old_pid (from $pid_file)"
                kill "$old_pid" 2>/dev/null || true
                # Wait up to 8s for it to die
                for i in $(seq 1 8); do
                    sleep 1
                    if ! kill -0 "$old_pid" 2>/dev/null; then
                        echo "[restart-mqtt-broker] Old broker PID=$old_pid exited after ${i}s"
                        return 0
                    fi
                done
                # Force-kill if still alive
                echo "[restart-mqtt-broker] Old broker still alive after 8s — force-killing PID=$old_pid"
                kill -9 "$old_pid" 2>/dev/null || true
                sleep 1
                return 0
            fi
        fi
    done
    echo "[restart-mqtt-broker] No running PID found for $base — assuming already dead"
}

kill_old_broker "$ORIGINAL_INSTANCE_ID"

# -----------------------------------------------------------------------
# Step 2: Wait for port 1883 to be free (up to 10s)
# -----------------------------------------------------------------------
echo "[restart-mqtt-broker] Waiting for port $PORT to be free..."
for i in $(seq 1 10); do
    if ! lsof -i :"$PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "[restart-mqtt-broker] Port $PORT is free after ${i}s"
        break
    fi
    if [ "$i" -eq 10 ]; then
        echo "[restart-mqtt-broker] WARNING: port $PORT still in use after 10s — proceeding anyway"
    fi
    sleep 1
done

# -----------------------------------------------------------------------
# Step 3: Launch new broker process
# -----------------------------------------------------------------------
write_logging_props "$NEW_INSTANCE_ID"

java -cp "$CLASSPATH" \
    -Djava.util.logging.config.file="$LOG_PROPS_FILE" \
    com.securenet.iotfirmware.mqtt.MqttBrokerMain \
    --port "$PORT" \
    >> "$LOG_DIR/$NEW_INSTANCE_ID.log" 2>&1 &

PID=$!
echo "[restart-mqtt-broker] $NEW_INSTANCE_ID started with PID=$PID on port $PORT"
echo $PID > "$LOG_DIR/$NEW_INSTANCE_ID.pid"