# EquipmentAlertServiceApp

## Overview

Uses a queue to guarantee delivery of critical medical equipment failure alerts.

Part of the [HealthSafe](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

Mechanism: ActiveMQ Queue (guaranteed delivery)

Queue: `equipment-failure-queue`

- Producer: `ward-service` (`../ward-service`) — publishes when an equipment failure is
  reported on one of its wards.
- Consumer: this service, which takes each alert off the queue and processes it.

## Why a queue and not a topic

Each failure alert must be handled by exactly one consumer, and must not be lost if this
service is down. A topic only delivers to subscribers connected at that moment, so
`staffing-events-topic` (see [`../common/`](../common)) is fine for updates that are safe
to miss. A queue keeps the message until a consumer takes it.

This was checked in the broker: with `equipment-alert-service` not running, the alert
published by `ward-service` stayed in `equipment-failure-queue` (queue size 1, 0 consumers)
and was not lost.

Messages are published as persistent, so they survive a broker restart. The consumer
acknowledges a message only after processing it, so a crash mid-processing leads to
redelivery. This gives at-least-once delivery, which means an alert can occasionally be
seen twice but should not be missed.

Broker URL and queue name come from `co.wethinkcode.healthsafe.mq.MqConfig` in this module
(`BROKER_URL`, `QUEUE`). Each service is an independent Maven project with no shared
parent pom, so this class is duplicated in every service that needs it, and the copies
differ slightly: each contains only the constants that service uses.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns `OK` |

## Project structure

```
equipment-alert-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── EquipmentAlertServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

The broker must be running first: `cd ../common && docker compose up -d`.

```
java -jar target/equipment-alert-service.jar
```

Listens on port `7034`.

## Test

No automated tests. Manual checks:

```
curl http://localhost:7034/health   # -> OK
```

To check delivery: trigger an equipment failure through `ward-service` and see the alert
in this service's console. Then look at `equipment-failure-queue` in the ActiveMQ console
at http://localhost:8161 (admin/admin): the queue size should return to 0 and the dequeue
count should increase.