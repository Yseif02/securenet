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

# Optionally stop PostgreSQL cluster
if [ "$1" = "--with-pg" ]; then
    echo ""
    "$SCRIPT_DIR/stop-postgres-cluster.sh"
fi

echo ""
echo "=== All services stopped ==="
echo ""
echo "Note: PostgreSQL cluster is still running."
echo "To stop it: ./scripts/stop-postgres-cluster.sh"
