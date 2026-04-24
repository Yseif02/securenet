#!/bin/bash
# =============================================================================
# SecureNet — PostgreSQL Replication Cluster Setup
#
# Creates a primary + 2 streaming standby PostgreSQL cluster for the
# Data Storage Layer, as specified in Stage 3 (DS Problem #6: Data
# Replication).
#
# Layout:
#   primary:  port 5432 (read-write)
#   standby1: port 5433 (read-only replica)
#   standby2: port 5434 (read-only replica)
#
# Prerequisites:
#   - PostgreSQL installed (brew install postgresql@16 on macOS)
#   - pg_basebackup available on PATH
#
# Usage:
#   ./scripts/setup-postgres-cluster.sh    # first-time setup
#   ./scripts/start-postgres-cluster.sh    # start after setup
#   ./scripts/stop-postgres-cluster.sh     # stop all
#
# The primary auto-creates the securenet database and schema on first run.
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PG_CLUSTER_DIR="$PROJECT_DIR/pg-cluster"

PRIMARY_PORT=5432
STANDBY1_PORT=5433
STANDBY2_PORT=5434
SOCKET_DIR=/tmp

PRIMARY_DIR="$PG_CLUSTER_DIR/primary"
STANDBY1_DIR="$PG_CLUSTER_DIR/standby1"
STANDBY2_DIR="$PG_CLUSTER_DIR/standby2"

echo "=== Setting up PostgreSQL Replication Cluster ==="
echo ""

# Clean up any existing cluster
if [ -d "$PG_CLUSTER_DIR" ]; then
    echo "Removing existing cluster at $PG_CLUSTER_DIR..."
    # Stop any running instances
    for dir in "$PRIMARY_DIR" "$STANDBY1_DIR" "$STANDBY2_DIR"; do
        [ -f "$dir/postmaster.pid" ] && pg_ctl -D "$dir" stop -m fast 2>/dev/null || true
    done
    rm -rf "$PG_CLUSTER_DIR"
fi

mkdir -p "$PG_CLUSTER_DIR"

# --- 1. Initialize primary ---
echo "--- Initializing primary (port $PRIMARY_PORT) ---"
initdb -D "$PRIMARY_DIR" --no-locale --encoding=UTF8 -U "$USER"

# Configure primary for replication
cat >> "$PRIMARY_DIR/postgresql.conf" << EOF

# SecureNet replication config
listen_addresses = 'localhost'
unix_socket_directories = '$SOCKET_DIR'
port = $PRIMARY_PORT
wal_level = replica
max_wal_senders = 4
hot_standby = on

# WAL archiving (simplified — uses pg_wal directly)
archive_mode = on
archive_command = 'true'
EOF

# Allow replication connections from localhost
cat >> "$PRIMARY_DIR/pg_hba.conf" << EOF

# Replication connections
local   replication     all                                trust
host    replication     all             127.0.0.1/32       trust
host    replication     all             ::1/128            trust
EOF

# Start primary
echo "Starting primary..."
pg_ctl -D "$PRIMARY_DIR" -l "$PG_CLUSTER_DIR/primary.log" start
sleep 2

# Create the securenet database
echo "Creating securenet database..."
createdb -h localhost -p $PRIMARY_PORT securenet 2>/dev/null || echo "  Database already exists"

# --- 2. Create standby 1 via pg_basebackup ---
echo ""
echo "--- Creating standby 1 (port $STANDBY1_PORT) ---"
pg_basebackup -D "$STANDBY1_DIR" -R -X stream \
    -h localhost -p $PRIMARY_PORT -U "$USER"

# Override port for standby 1
cat >> "$STANDBY1_DIR/postgresql.conf" << EOF

# Standby 1 overrides
unix_socket_directories = '$SOCKET_DIR'
port = $STANDBY1_PORT
EOF

# Start standby 1
echo "Starting standby 1..."
pg_ctl -D "$STANDBY1_DIR" -l "$PG_CLUSTER_DIR/standby1.log" start
sleep 1

# --- 3. Create standby 2 via pg_basebackup ---
echo ""
echo "--- Creating standby 2 (port $STANDBY2_PORT) ---"
pg_basebackup -D "$STANDBY2_DIR" -R -X stream \
    -h localhost -p $PRIMARY_PORT -U "$USER"

# Override port for standby 2
cat >> "$STANDBY2_DIR/postgresql.conf" << EOF

# Standby 2 overrides
unix_socket_directories = '$SOCKET_DIR'
port = $STANDBY2_PORT
EOF

# Start standby 2
echo "Starting standby 2..."
pg_ctl -D "$STANDBY2_DIR" -l "$PG_CLUSTER_DIR/standby2.log" start
sleep 1

# --- 4. Verify replication ---
echo ""
echo "--- Verifying replication ---"
psql -h localhost -p $PRIMARY_PORT -d securenet -c "SELECT client_addr, state, sync_state FROM pg_stat_replication;"

echo ""
echo "=== PostgreSQL Cluster Ready ==="
echo "  Primary:  localhost:$PRIMARY_PORT  (read-write)"
echo "  Standby1: localhost:$STANDBY1_PORT (read-only)"
echo "  Standby2: localhost:$STANDBY2_PORT (read-only)"
echo ""
echo "  Logs: $PG_CLUSTER_DIR/*.log"
echo ""
echo "  Stop:  ./scripts/stop-postgres-cluster.sh"
echo "  Start: ./scripts/start-postgres-cluster.sh"
