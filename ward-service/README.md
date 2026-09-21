# WardServiceApp

## Overview

Provides lists of wards and departments for the HealthSafe hospital system. It loads
the cleaned ward records from `ingestion-service`, keeps them in memory, and serves them
to other services. It also takes part in the messaging layer: it listens for staffing
updates and publishes equipment failure alerts.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

## How it works

- **Ward data:** on the first request that needs it, the service calls
  `GET http://localhost:7030/wards` on `ingestion-service` and caches the result.
  Loading lazily means the services can start in any order. If ingestion is not
  reachable, the endpoint returns `503`, and the next request tries again.
- **Topic (consumer):** subscribes to the ActiveMQ topic `staffing-events-topic`
  and logs each schedule event published by `staffing-service`. A topic only delivers
  to subscribers that are connected at the time, so events published while this
  service is down are not replayed.
- **Queue (producer):** publishes to the ActiveMQ queue `equipment-failure-queue`
  when an equipment failure is reported for a ward. A queue holds each message until a
  consumer takes it, so alerts are not lost if `equipment-alert-service` is down.
- Broker URL, topic name and queue name come from
  `co.wethinkcode.healthsafe.mq.MqConfig` in this module.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns `OK` |
| GET | `/wards` | All cleaned ward records |
| GET | `/wards/{id}` | One ward (ID is case-insensitive). `404` if unknown |
| GET | `/departments` | Sorted list of distinct department names |
| POST | `/wards/{id}/equipment-failure` | Reports an equipment failure and publishes an alert to `equipment-failure-queue` (adjust to match your code) |

`staffing-service` calls `GET /wards/{id}` to check that a ward exists before scheduling.
See [Integration contracts](../README.md#integration-contracts) in the root README.

## Project structure

```
ward-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── Ward.java
    ├── WardServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

Start the ActiveMQ broker first (`cd ../common && docker compose up -d`), and
`ingestion-service` before making ward requests.

```
java -jar target/ward-service.jar
```

Listens on port `7031`.

## Test

No automated tests. Manual checks (with ingestion-service running):

```
curl http://localhost:7031/health              # -> OK
curl http://localhost:7031/wards               # cleaned ward list
curl http://localhost:7031/wards/w-05          # found, ID is case-insensitive
curl -i http://localhost:7031/wards/W-99       # 404
curl http://localhost:7031/departments         # distinct department names
```

To check the topic subscription, run `curl -X POST http://localhost:7033/schedule/W-01`
against `staffing-service` and look for the `Staffing event:` line in this service's
console.

To check the queue, trigger an equipment failure and confirm `equipment-failure-queue`
shows the message in the ActiveMQ console at http://localhost:8161 (admin/admin).