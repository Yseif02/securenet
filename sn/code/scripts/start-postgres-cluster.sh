#!/bin/bash
# Starts the SecureNet PostgreSQL replication cluster (primary + 2 standbys)
# Run setup-postgres-cluster.sh first if not yet initialized.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PG_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/pg-cluster"

echo "=== Starting PostgreSQL Cluster ==="

for node in primary standby1 standby2; do
    DIR="$PG_DIR/$node"
    if [ -d "$DIR" ]; then
        if pg_ctl -D "$DIR" status > /dev/null 2>&1; then
            echo "  $node: already running"
        else
            echo "  Starting $node..."
            pg_ctl -D "$DIR" -l "$PG_DIR/$node.log" start
        fi
    else
        echo "  $node: not initialized — run setup-postgres-cluster.sh first"
    fi
done

echo "=== PostgreSQL Cluster Started ==="
