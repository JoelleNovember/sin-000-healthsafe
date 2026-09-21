# IngestionServiceApp

## Overview

Parses and cleans `wards-outdated.csv`, a messy legacy export of wards, wings and
specialist departments, and is the first stop in the HealthSafe pipeline. The CSV is
read once at startup, each row is cleaned, duplicate wards are merged, and the result
is served over REST for `ward-service` to consume. Independent Maven module, no parent pom.

Part of the [HealthSafe](../README.md) project.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Returns `OK` |
| GET | `/wards` | All cleaned ward records (17 from the supplied CSV) |

## Cleaning rules

| Problem in the CSV | How it is handled |
|---|---|
| Padded, inconsistently cased header | Header row is skipped; columns are read by position |
| Lowercase or padded IDs (`w-02`, `W-03 `) | Trimmed and upper-cased (`W-02`, `W-03`) |
| Padding and double spaces (`" East Wing "`, `South  Wing`) | Trimmed and collapsed to single spaces |
| Inconsistent casing (`east wing`, `PAEDIATRICS`) | Title-cased; `ICU` is kept as an acronym |
| Spelling variant (`Pediatrics`) | Mapped to `Paediatrics` |
| Missing wing (W-08) | Stored as `null`, with a note |
| Placeholders (`N/A`, `TBD`, `unknown`) | Not numbers, so `bedsAvailable` becomes `null` with a note |
| Negative or unrealistic counts (`-1`, `-2`, `2023`) | Outside the valid range 0-100, so `null` with a note |
| Spelled-out numbers (`five`) | Converted to digits for zero to nine |
| Other non-numeric text (`full`) | `null` with a note. It could mean zero beds or at capacity, so it is not guessed |
| Duplicate ward (`W-05` / `w-05`) | Merged into one record: the first row wins, and blanks are filled from the second. The merge is recorded in `notes` |

Every changed or discarded value leaves a trace in the record's `notes` field.

## Example: W-05 cleaned

Input rows:

```
W-05,East Wing,Paediatrics,5
w-05,east wing ,PAEDIATRICS,five
```

Output (one merged record):

```json
{
  "wardId": "W-05",
  "wing": "East Wing",
  "department": "Paediatrics",
  "bedsAvailable": 5,
  "notes": " | duplicate row merged: bedsAvailable 'five' converted to 5"
}
```

## Project structure

```
ingestion-service/
├── pom.xml
└── src/main/
    ├── java/co/wethinkcode/healthsafe/
    │   ├── IngestionServiceApp.java
    │   ├── Ward.java
    │   └── WardCleaner.java
    └── resources/wards-outdated.csv
```

The CSV is loaded with `getResourceAsStream`, so it is found inside the packaged jar.

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7030`.

## Test

No automated tests. Manual checks:

```
curl http://localhost:7030/health   # -> OK
curl http://localhost:7030/wards    # 17 cleaned records
```

Things to check in the output: W-05 has `bedsAvailable: 5`, W-08 has `wing: null`,
W-11 says `Paediatrics`, and W-13 has `bedsAvailable: null`.