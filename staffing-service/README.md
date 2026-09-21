# StaffingServiceApp

## Overview

Provides on-call schedules for doctors based on ward and Emergency Status. For a given
ward it checks the ward exists, reads the current alert level, and picks doctors from a
hard-coded in-memory roster for the ward's department. A `POST` also publishes the
schedule as an event to the message broker.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

## How it works

1. **REST to `ward-service`:** `GET /wards/{id}` confirms the ward exists (`404` if not)
   and gives its department.
2. **REST to `alert-level-service`:** `GET /alert-level` gives the current level (0-8).
3. **Schedule sizing rule:**

   | Alert level | Doctors on call |
      |---|---|
   | 0-2 | 1 |
   | 3-5 | 2 |
   | 6-7 | 3 |
   | 8 (Code Blue) | Everyone on the department roster |

   The number is always capped at the size of the department's roster.
4. **Topic (producer):** `POST /schedule/{wardId}` also publishes the schedule as JSON
   to the ActiveMQ topic `staffing-events-topic`, which `ward-service` subscribes to.
   The REST validation calls stay synchronous, and the topic is used for the broadcast,
   so the sender does not need to know who is listening.

Broker URL and topic name come from `co.wethinkcode.healthsafe.mq.MqConfig` in this module.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns `OK` |
| GET | `/schedule/{wardId}` | Computes the on-call schedule. `404` if the ward is unknown |
| POST | `/schedule/{wardId}` | Same result, and also publishes it to `staffing-events-topic` |

Example response:

```json
{
  "wardId": "W-01",
  "department": "Cardiology",
  "alertLevel": 0,
  "onCall": ["Dr Ada"]
}
```

If `ward-service` or `alert-level-service` is unreachable, the endpoint returns `503`.

## Project structure

```
staffing-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── StaffingServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

Needs `ward-service` (7031) and `alert-level-service` (7032) running, which in turn need
`ingestion-service` (7030). The broker must be up (`cd ../common && docker compose up -d`)
before using `POST`.

```
java -jar target/staffing-service.jar
```

Listens on port `7033`.

## Test

No automated tests. Manual checks:

```
curl http://localhost:7033/health                        # -> OK
curl http://localhost:7033/schedule/W-01                 # 1 doctor at level 0
curl -X PUT -H "Content-Type: application/json" -d '{"level":8}' http://localhost:7032/alert-level
curl http://localhost:7033/schedule/W-01                 # whole Cardiology roster
curl -i http://localhost:7033/schedule/W-99              # 404
curl -X POST http://localhost:7033/schedule/W-01         # also publishes the event
```

After the `POST`, `ward-service`'s console should print a `Staffing event:` line.