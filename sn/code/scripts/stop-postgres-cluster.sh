#!/bin/bash
# Stops the SecureNet PostgreSQL replication cluster

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/pg-cluster"

echo "=== Stopping PostgreSQL Cluster ==="

for node in standby2 standby1 primary; do
    DIR="$PG_DIR/$node"
    if [ -d "$DIR" ] && pg_ctl -D "$DIR" status > /dev/null 2>&1; then
        echo "  Stopping $node..."
        pg_ctl -D "$DIR" stop -m fast
    else
        echo "  $node: not running"
    fi
done

echo "=== PostgreSQL Cluster Stopped ==="
