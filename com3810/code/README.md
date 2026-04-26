# SecureNet IoT Security Platform

SecureNet is a distributed smart-home security platform built as a multi-service Java system. It includes user management, device management, IoT firmware delivery, MQTT-based device communication, event processing, notifications, video streaming, an API gateway, and a demo client flow that exercises the platform end to end.

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+ with `createdb` and `dropdb`

## Quick Start

### Setup Database
Run in bash:
```bash
createdb securenet
```
Or open Postgres terminal and run:
```bash
create database securenet
```


### Build everything

```bash
mvn clean package -DskipTests
```

### Start the full platform

```bash
./scripts/setup-postgres-cluster.sh
./scripts/start-platform.sh
```

This launches the PostgreSQL replication cluster plus the SecureNet services, mock-device infrastructure, Raft nodes, and ClusterManager ensemble.

### Run the guided demo

```bash
java -cp "$(find . -name '*.jar' -path '*/target/*' | grep -v sources | tr '\n' ':')" \
  com.securenet.demo.PlatformDemo
```

The demo walks through:

- homeowner registration and login
- firmware seeding
- device onboarding
- mock device bootstrap + MQTT provisioning
- door lock and camera commands
- event timeline reads
- archived video playback lookup
- cluster-status inspection

### Stop the platform

```bash
./scripts/stop-platform.sh --with-pg
```

## Testing

### Fast unit and component tests

```bash
mvn test
```

Runs the regular `*Test` layer. (15 sec)


### Integration suites

Prerequisite: `./scripts/start-platform.sh` is already running. (about 5 min)

```bash
mvn verify -P integration
```

### Load suites

Prerequisite: the full platform is already running. (about 45 seconds)

```bash
mvn verify -P load
```

## Project Structure

```text
com3810/code/
├── securenet-model/              Shared model objects, JSON, clients, common infra
├── securenet-storage/            PostgreSQL-backed storage service
├── securenet-user-management/    User accounts, auth, push-token registry
├── securenet-device-management/  Device registry, ownership, remote commands
├── securenet-iot-firmware/       IDFS device gateway and MQTT-facing firmware flow
├── securenet-event-processing/   Security event ingestion and Raft-backed processing
├── securenet-notification/       Push notification delivery and retry outbox logic
├── securenet-video-streaming/    Recording sessions, chunk persistence, playback
├── securenet-api-gateway/        Client-facing edge API
├── securenet-client-api/         High-level client abstraction used by demos/tests
├── demo/                         End-to-end walkthroughs and mock devices
└── scripts/                      Platform lifecycle and PostgreSQL cluster scripts
```

## Operational Notes

- `start-platform.sh` is the standard local topology entrypoint.
- `stop-platform.sh --with-pg` is the cleanest shutdown path after demos or tests.
- Mock devices talk to IDFS over HTTP for registration/provisioning, then switch to MQTT for steady-state command/event traffic.
- Notification retries are persisted in PostgreSQL through the notification outbox table.

## Troubleshooting

### Port already in use

```bash
./scripts/stop-platform.sh --with-pg
```

If needed, rerun the start script after shutdown completes.

### Reset local database state

```bash
dropdb securenet && createdb securenet
```

### Demo compile issues after partial reverts

If a revert removed helper classes used by the demo devices, rebuild from the current repository state with:

```bash
mvn -q -pl demo -am compile
```
