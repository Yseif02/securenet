# SecureNet IoT Security Platform — README

## Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 14+ (with `createdb` command available)

## Quick Start

### 1. Set up PostgreSQL
```bash
# Create the database (run once)
createdb securenet

# If you need to reset (drops all tables):
dropdb securenet && createdb securenet
```

### 2. Build
```bash
cd com3810/code
mvn clean package -DskipTests
```

### 3. Run the Full Distributed Platform (Multi-JVM mode)
```bash
# Set up PostgreSQL replication cluster (primary + 2 standbys)
./scripts/setup-postgres-cluster.sh

#Start PostgresSQL cluster (if not already running)
./scripts/start-postgres-cluster.sh

# Start all 21 processes
./scripts/start-platform.sh

# Run the demo against the running platform
java -cp "$(find . -name '*.jar' -path '*/target/*' | grep -v sources | tr '\n' ':')" \
  com.securenet.demo.PlatformDemo

# Stop everything
./scripts/stop-platform.sh
```

## Project Structure
```
com3810/code/
├── securenet-model/           Model records, enums, exceptions, common utilities
├── securenet-storage/         PostgreSQL-backed storage (port 9000)
├── securenet-user-management/ User accounts + auth tokens (port 9001)
├── securenet-device-management/ Device registry + commands (port 9002)
├── securenet-iot-firmware/    IDFS device gateway + MQTT broker (port 8080)
├── securenet-event-processing/ Event ingestion + Raft consensus (port 9003)
├── securenet-notification/    Push notification dispatch (port 9004)
├── securenet-video-streaming/ Recording sessions + archives (port 9005)
├── securenet-api-gateway/     Client-facing API gateway (port 8443)
├── securenet-client-api/      Client application service
├── demo/                      Orchestrator + mock devices
└── scripts/                   Platform start/stop scripts
```

## Troubleshooting

### Port already in use
Kill any lingering Java processes: `pkill -f securenet` or `./scripts/stop-platform.sh`

### Database already has data
Reset: `dropdb securenet && createdb securenet`
