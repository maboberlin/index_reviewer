# Index Reviewer

Executes the SMI index review for Q3 2026 and exposes it over a REST API.

Spring Boot 4 (Web MVC, Data JPA) on Java 21, with an in-memory H2 database.

## Build

Requires a JDK 21 or newer. The Maven wrapper downloads everything else.

```bash
./mvnw clean verify          # compiles and runs the tests
```

On Windows use `mvnw.cmd`. If the wrapper cannot reach Maven Central from your
network, any local Maven 3.9+ works the same: `mvn clean verify`.

## Run

```bash
./mvnw spring-boot:run
```

The application starts on **http://localhost:8080**.

The schema is recreated on every start and the database is in memory, so **all
data is lost on shutdown** and the CSV files have to be uploaded again. Browse
it while running at http://localhost:8080/h2-console (JDBC URL
`jdbc:h2:mem:indexreviewer`, user `sa`, no password).

The review parameters live in `src/main/resources/application.properties` and
are validated at startup — a missing or out-of-range value stops the
application with a message naming the property:

```properties
index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.cut-off-date=2026-09-10
index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.review-date=2026-09-21
index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.target-size=20
index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.max-weight=0.18
index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.selection-buffer=2
```

## Endpoints

All request and response fields use `snake_case`.

### 1. Upload input data

```
POST /api/v1/uploads          (multipart/form-data)
```

| Part | Value |
| --- | --- |
| `data_type` | `SPI_UNIVERSE`, `SEC_DATA` or `COMPOSITION` |
| `file` | the semicolon-separated CSV |

```bash
curl -F "data_type=SPI_UNIVERSE" -F "file=@data/spi_universe.csv" \
     http://localhost:8080/api/v1/uploads
```

```json
{"data_type":"SPI_UNIVERSE","file_name":"spi_universe.csv","imported_rows":205,"skipped_rows":204}
```

Each upload **replaces** the previously imported data of that type. A file is
imported all or nothing: one bad row rejects the whole file with `400` and a
list of `line` / `field` / `message` entries. `skipped_rows` counts redundant
rows dropped during import — the supplied `spi_universe.csv` repeats most of
its 409 rows, leaving 205 distinct companies.

All three files must be uploaded before a review can run.

### 2. Run a review

```
POST /api/v1/index-reviews/run          (application/json)
```

```bash
curl -X POST -H "Content-Type: application/json" \
     -d '{"index_type":"SMI_INDEX_REVIEW_Q3_2026"}' \
     http://localhost:8080/api/v1/index-reviews/run
```

```json
{
  "constituents": [
    {"id": "205", "weight": 0.180000000000},
    {"id": "155", "weight": 0.179999999998},
    {"id": "63",  "weight": 0.142590432480}
  ],
  "joiners": ["177"],
  "leavers": ["103"],
  "review_status": "COMPLETED"
}
```

Constituents are returned heaviest first. The weights are final weights after
the 18% cap and the redistribution of the excess; they sum to exactly 1.

Re-running replaces the previous result rather than adding to it, and over
unchanged data produces an identical index.

### 3. Read the last result

```
POST /api/v1/index-reviews/status          (application/json)
```

Same request and same response shape as `/run`, but reads the stored outcome
instead of recomputing it. Before the first run:

```json
{"constituents":[],"joiners":[],"leavers":[],"review_status":"NOT_STARTED"}
```

### Status codes

| Code | Meaning |
| --- | --- |
| `200` | success |
| `400` | unknown `data_type` / `index_type`, or a rejected CSV |
| `409` | an import or review of the same type is already running |
| `422` | the review could not be completed, e.g. no data imported yet |

## Notes on the review

- `FFMCAP = Price(cut-off) x Shares(review date) x FreeFloat(review date)`.
- A company in the universe without the data the formula needs is **excluded**
  and logged at `WARN`, not treated as an error. The supplied `sec_data.csv`
  has 204 review-date rows for 205 universe companies, so company `166` is
  skipped on every run.
- Ranking ties are broken by company id, so a rerun is reproducible.
- The selection buffer keeps the top `target_size - buffer` ranks outright and
  lets the surrounding `2 x buffer` ranks compete for the remaining places,
  preferring companies already in the index.
- Capping is iterative: redistributing the excess can lift another constituent
  over the cap, so it repeats until none exceeds it.
