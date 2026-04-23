#!/bin/bash
# =============================================================================
# SecureNet Platform — Stop all services
#
# Stops all Java services started by start-platform.sh.
# Use --with-pg to also stop the PostgreSQL cluster.
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_DIR="$PROJECT_DIR/pids"
LATEST_LOG="$PROJECT_DIR/logs/latest"
DEVICE_RECORDS_PATH_FILE="$LATEST_LOG/device-records.path"

cleanup_device_records() {
    local candidate=""

    if [ -f "$DEVICE_RECORDS_PATH_FILE" ]; then
        candidate="$(cat "$DEVICE_RECORDS_PATH_FILE")"
    elif [ -e "$LATEST_LOG/device-records" ]; then
        candidate="$LATEST_LOG/device-records"
    fi

    if [ -z "$candidate" ]; then
        echo "  No device records directory recorded for cleanup"
        return
    fi

    local resolved
    resolved="$(cd "$(dirname "$candidate")" 2>/dev/null && pwd)/$(basename "$candidate")"
    if [ ! -e "$resolved" ]; then
        echo "  Device records directory already absent: $resolved"
        return
    fi

    case "$resolved" in
        ""|"/"|"$PROJECT_DIR"|"$PROJECT_DIR/logs"|"$PROJECT_DIR/logs/latest")
            echo "  Refusing unsafe device-records cleanup target: $resolved"
            return
            ;;
    esac

    case "$resolved" in
        "$PROJECT_DIR"/logs/run_*/device-records|"$PROJECT_DIR"/logs/latest/device-records)
            echo "  Removing device records directory: $resolved"
            rm -rf "$resolved"
            ;;
        *)
            echo "  Refusing cleanup outside run-owned logs directory: $resolved"
            ;;
    esac
}

echo "=== Stopping SecureNet Platform ==="
echo ""

# Stop all Java services
for pid_file in "$PID_DIR"/*.pid; do
    [ -f "$pid_file" ] || continue
    name=$(basename "$pid_file" .pid)
    PID=$(cat "$pid_file")

    if kill -0 "$PID" 2>/dev/null; then
        echo "  Stopping $name (PID $PID)..."
        kill "$PID"
    else
        echo "  $name already stopped"
    fi
    rm -f "$pid_file"
done

# Also stop any processes restarted by the Cluster Manager
# (their PID files are written to the log directory by restart scripts)
if [ -d "$LATEST_LOG" ]; then
    for pid_file in "$LATEST_LOG"/*.pid; do
        [ -f "$pid_file" ] || continue
        name=$(basename "$pid_file" .pid)
        PID=$(cat "$pid_file")
        if kill -0 "$PID" 2>/dev/null; then
            echo "  Stopping restarted $name (PID $PID)..."
            kill "$PID"
        fi
        rm -f "$pid_file"
    done
fi

# Fallback: kill any java processes still holding our ports
for port in 1883 8080 8081 8082 8443 9000 9001 9002 9003 9004 9005 \
            9010 9011 9012 9013 9014 9015 9020 9021 9022 9023 9024 \
            9025 9033 9090 9103 9203; do
    pid=$(lsof -ti :$port 2>/dev/null)
    if [ -n "$pid" ]; then
        echo "  Force-killing PID $pid on port $port"
        kill $pid 2>/dev/null || true
    fi
done

# Optionally stop PostgreSQL cluster
if [ "$1" = "--with-pg" ]; then
    echo ""
    "$SCRIPT_DIR/stop-postgres-cluster.sh"
fi

cleanup_device_records

echo ""
echo "=== All services stopped ==="
echo ""
echo "Note: PostgreSQL cluster is still running."
echo "To stop it: ./scripts/stop-postgres-cluster.sh"
