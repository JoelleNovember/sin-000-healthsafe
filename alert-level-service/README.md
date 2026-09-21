# AlertLevelServiceApp

## Overview

Tracks the hospital Emergency Status (0-8, 8 = full Code Blue). The level is held in
memory in a thread-safe counter and starts at 0. It resets to 0 when the service restarts.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

REST: called by `staffing-service` (`../staffing-service`) to read the current
status when computing on-call schedules — see [Integration contracts](../README.md#integration-contracts)
in the root README.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns `OK` |
| GET | `/alert-level` | Current level, as `{ "level": 0 }` |
| PUT | `/alert-level` | Sets the level. Body: `{ "level": 5 }`. Returns `400` if the level is outside 0-8 |

The `PUT` endpoint is an addition to the brief's contracts, which only specify the read.
Without it the level could never change.

## Project structure

```
alert-level-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/AlertLevelServiceApp.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/alert-level-service.jar
```

Listens on port `7032`.

## Test

No automated tests. Manual checks:

```
curl http://localhost:7032/health
curl http://localhost:7032/alert-level
curl -X PUT -H "Content-Type: application/json" -d '{"level":5}' http://localhost:7032/alert-level
curl -i -X PUT -H "Content-Type: application/json" -d '{"level":9}' http://localhost:7032/alert-level   # 400
```