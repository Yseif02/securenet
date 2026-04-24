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

## Testing

### Fast unit and component tests
```bash
mvn test
```

This is the default fast layer. It runs `*Test` classes only and excludes the heavier full-platform suites.

### Full-platform smoke and correctness tests
Prerequisite: the platform is already running via `./scripts/start-platform.sh`.

```bash
mvn verify -P integration
```

This runs `*IT` suites such as the assertion-driven smoke flow in `demo`. (This took me about 5 min to run)

### Resilience and failover tests
Prerequisite: the full platform and ClusterManager are already running.

```bash
mvn verify -P resilience
```

This runs `*ResilienceIT` suites that use the real load balancer and ClusterManager failover harness. (This took me about 3:30 to run)

### Load tests
Prerequisite: the full platform is already running.

```bash
mvn verify -P load
```

This runs `*LoadE2E` suites. The stress harness now supports finite runs and writes a machine-readable JSON summary file when given `--summary-file`. (Took less than a minute to run)

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
