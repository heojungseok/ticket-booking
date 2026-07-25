# Ticket Booking

Ticket Booking is an MSA playground for FCFS, raffle, and general booking flows. The
current functional Phase 1 scope is the performance and booking path. The other
applications start, expose health/schema foundations, and reserve their intended
boundaries, but their product flows are not implemented yet.

This document defines local procedures and expected outcomes. It is not evidence
that a particular build, container run, recovery drill, or E2E run has passed.

## Stack

- Java 21
- Gradle Groovy DSL
- Spring Boot 3.3.x
- PostgreSQL, MongoDB, Redis, Kafka, and Zookeeper
- Docker Compose
- Swagger via springdoc-openapi 2.5.0

## Services

| Service | Port | Current scope |
| --- | ---: | --- |
| api-gateway | 8000 | Application-health skeleton; routing, JWT, and rate-limiting flows are not implemented |
| user-service | 8001 | Application-health and PostgreSQL/Flyway schema skeleton; signup, login, and JWT issuing are not implemented |
| performance-service | 8002 | Phase 1 performance, seat, outbox publishing, and booking-event projection |
| booking-service | 8003 | Phase 1 seat projection, booking/payment, outbox publishing, and duplicate-seat defense |
| notification-service | 8004 | Application-health plus MongoDB/Kafka configuration skeleton; notification flows are not implemented |
| queue-service | 8005 | Application-health plus Redis configuration skeleton; queue/token flows are not implemented |

## Local prerequisites and setup

Install Docker with Docker Compose. Java 21 is also required when applications run
on the host. Run the shell snippets from the repository root; `curl`, `jq`, and
standard PostgreSQL/Kafka tools inside the containers are used by the verification
procedures.

Create the local environment file:

```bash
cp .env.example .env
```

The local-only `.env` is ignored and must not be committed. Replace every sample
secret before use, keep the file private, and never paste expanded secret values
into logs or evidence.

The current MongoDB URI is assembled directly from the root username and password
in container mode, while host mode reads the URI from `.env`. Until that connection
construction is redesigned to encode credentials, local Mongo root usernames and
passwords must avoid URI-reserved characters such as `@`, `:`, `/`, `?`, `#`, `%`,
and similar delimiters. Keep `MONGODB_URI` synchronized with the chosen local
credentials.

For IntelliJ, open the repository root as a Gradle project and select a Java 21
SDK/toolchain. Do not depend on a machine-specific repository or JDK path.

### Host-dev mode

Host-dev mode runs only the base infrastructure in containers:

```bash
docker compose --env-file .env -f docker-compose.yml up -d --wait --wait-timeout 180
```

Run each application as a separate host process, with `.env` explicitly exported
in that terminal. The reusable form is:

```bash
set -a; source .env; set +a; ./gradlew :<module>:bootRun
```

Use one terminal/process for each of the six modules:

```bash
set -a; source .env; set +a; ./gradlew :api-gateway:bootRun
set -a; source .env; set +a; ./gradlew :user-service:bootRun
set -a; source .env; set +a; ./gradlew :performance-service:bootRun
set -a; source .env; set +a; ./gradlew :booking-service:bootRun
set -a; source .env; set +a; ./gradlew :notification-service:bootRun
set -a; source .env; set +a; ./gradlew :queue-service:bootRun
```

This mode preserves each application's localhost defaults and the infrastructure
host ports in `docker-compose.yml`. Before stopping the base infrastructure, stop
all six host JVMs (for example, Ctrl-C in each terminal). Then remove only the base
containers and network, without deleting volumes:

```bash
docker compose --env-file .env -f docker-compose.yml down
```

### All-container local mode

Build all executable jars first:

```bash
./gradlew clean bootJar
```

Every current Dockerfile copies `build/libs/*.jar`. Before the image build, require
exactly one jar in each of the six module directories so a wildcard cannot select
zero or multiple artifacts:

```bash
for module in api-gateway user-service performance-service booking-service notification-service queue-service; do
  count="$(find "$module/build/libs" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
  if [ "$count" -ne 1 ]; then
    printf '%s: expected exactly one jar, found %s\n' "$module" "$count"
    exit 1
  fi
done
```

Validate the merged configuration, build images, and start the full stack:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml config --quiet
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml build
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml up -d --wait --wait-timeout 180
```

Base and override files must always be used together. The override depends on the
databases, Redis, and Kafka declared by the base file.

`--wait` proves that infrastructure healthchecks pass and that application
containers without healthchecks are running. Compose `--wait` does not prove application HTTP health. The six applications currently have no Compose
healthchecks, so use the next section as a separate gate.

- Kafka UI: http://localhost:8080
- Redis Commander: http://localhost:8081

## Application health

Check exactly these six endpoints:

| Service | Application-health URL |
| --- | --- |
| api-gateway | http://localhost:8000/actuator/health |
| user-service | http://localhost:8001/actuator/health |
| performance-service | http://localhost:8002/actuator/health |
| booking-service | http://localhost:8003/actuator/health |
| notification-service | http://localhost:8004/actuator/health |
| queue-service | http://localhost:8005/actuator/health |

For each poll, use this request shape:

```bash
curl -sS --max-time 3 -o <service>.json -w '%{http_code}' <url>
```

The following polls every two seconds for up to 180 seconds. Each service passes
only when the response is HTTP 200 and `jq -e '.status == "UP"'` succeeds:

```bash
poll_health() {
  service="$1"
  url="$2"

  for _ in {1..90}; do
    code="$(curl -sS --max-time 3 -o "${service}.json" -w '%{http_code}' "$url" || true)"
    if [ "$code" = "200" ] && jq -e '.status == "UP"' "${service}.json" >/dev/null; then
      return 0
    fi
    sleep 2
  done

  printf '%s did not reach application health within 180 seconds\n' "$service"
  return 1
}

poll_health api-gateway http://localhost:8000/actuator/health || exit 1
poll_health user-service http://localhost:8001/actuator/health || exit 1
poll_health performance-service http://localhost:8002/actuator/health || exit 1
poll_health booking-service http://localhost:8003/actuator/health || exit 1
poll_health notification-service http://localhost:8004/actuator/health || exit 1
poll_health queue-service http://localhost:8005/actuator/health || exit 1
```

This is application health, not traffic readiness. It does not prove that the
health-only service features are implemented, that Kafka is currently available,
or that either direction of the Phase 1 event flow works.

## Phase 1 functional E2E

This E2E covers only the implemented performance and booking path. Run it after
all six application-health checks pass. Use a new shell with `.env` exported so
database usernames and database names come from the local file:

```bash
set -a
source .env
set +a
```

### 1. Create a fresh performance and seat dataset

Create a performance and require HTTP 201:

```bash
PERFORMANCE_HTTP="$(curl -sS --max-time 10 \
  -o performance-create.json \
  -w '%{http_code}' \
  -X POST http://localhost:8002/performances \
  -H 'Content-Type: application/json' \
  --data '{"title":"Container E2E","description":"Task 4","venueName":"Local","startsAt":"2026-08-01T19:00:00","saleType":"FCFS","status":"OPEN"}')"
test "$PERFORMANCE_HTTP" = "201" || exit 1
PERFORMANCE_ID="$(jq -er '.id' performance-create.json)" || exit 1
```

Create three seats and parse the first seat ID:

```bash
SEATS_HTTP="$(curl -sS --max-time 10 \
  -o seats-create.json \
  -w '%{http_code}' \
  -X POST "http://localhost:8002/performances/${PERFORMANCE_ID}/seats/batch" \
  -H 'Content-Type: application/json' \
  --data '{"section":"A","rowName":"1","startNumber":1,"endNumber":3,"price":10000}')"
test "$SEATS_HTTP" = "201" || exit 1
test "$(jq -er 'length' seats-create.json)" = "3" || exit 1
SEAT_ID="$(jq -er '.[0].id' seats-create.json)" || exit 1
USER_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
```

Use these IDs only for this run. Do not reuse an old performance, seat, or user ID
when diagnosing a fresh cycle.

### 2. Prove `SEATS_CREATED` publication and booking projection

Do not use a fixed sleep. Poll at two-second intervals for at most 60 seconds.
First, run this exact performance outbox query:

```sql
select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;
```

The command and assertion are:

```bash
unset PERFORMANCE_EVENT_ID
for _ in {1..30}; do
  PERFORMANCE_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;")"
  case "$PERFORMANCE_OUTBOX_ROW" in
    *'|PUBLISHED|0')
      PERFORMANCE_EVENT_ID="${PERFORMANCE_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep 2
done
test -n "${PERFORMANCE_EVENT_ID:-}" || exit 1
test "$PERFORMANCE_OUTBOX_ROW" = "${PERFORMANCE_EVENT_ID}|PUBLISHED|0" || exit 1
```

This requires one row with `PUBLISHED|0` and saves its event ID. Next, require the
booking projection to be `${PERFORMANCE_ID}|AVAILABLE` using this exact SQL:

```sql
select performance_id,status from booking_seats where seat_id='${SEAT_ID}';
```

```bash
for _ in {1..30}; do
  BOOKING_SEAT_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select performance_id,status from booking_seats where seat_id='${SEAT_ID}';")"
  [ "$BOOKING_SEAT_ROW" = "${PERFORMANCE_ID}|AVAILABLE" ] && break
  sleep 2
done
test "$BOOKING_SEAT_ROW" = "${PERFORMANCE_ID}|AVAILABLE" || exit 1
```

Finally, prove the booking consumer recorded the same event ID with this exact SQL:

```sql
select event_id from processed_events where event_id='${PERFORMANCE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';
```

```bash
for _ in {1..30}; do
  BOOKING_PROCESSED_EVENT_ID="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${PERFORMANCE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';")"
  [ "$BOOKING_PROCESSED_EVENT_ID" = "$PERFORMANCE_EVENT_ID" ] && break
  sleep 2
done
test "$BOOKING_PROCESSED_EVENT_ID" = "$PERFORMANCE_EVENT_ID" || exit 1
```

### 3. Create a booking, allowing for mock payment randomness

Submit at most three booking attempts. Every attempt must return HTTP 201 and must
provide both `bookingId` and `status`. A failed mock payment must leave the
`bookings` row `FAILED`, the `payments` row `FAILED`, and the projected
`booking_seats` row `AVAILABLE` before the next attempt. A paid attempt must leave
those values `PAID`, `SUCCEEDED`, and `BOOKED`.

```bash
PAID_BOOKING=0
for PAYMENT_ATTEMPT in 1 2 3; do
  BOOKING_HTTP="$(curl -sS --max-time 10 \
    -o "booking-${PAYMENT_ATTEMPT}.json" \
    -w '%{http_code}' \
    -X POST http://localhost:8003/bookings \
    -H "X-User-Id: ${USER_ID}" \
    -H 'Content-Type: application/json' \
    --data "{\"performanceId\":\"${PERFORMANCE_ID}\",\"seatId\":\"${SEAT_ID}\",\"amount\":10000}")"
  test "$BOOKING_HTTP" = "201" || exit 1

  BOOKING_ID="$(jq -er '.bookingId' "booking-${PAYMENT_ATTEMPT}.json")" || exit 1
  BOOKING_STATUS="$(jq -er '.status' "booking-${PAYMENT_ATTEMPT}.json")" || exit 1

  BOOKING_PAYMENT_SEAT_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select b.status,p.status,bs.status from bookings b join payments p on p.booking_id=b.id join booking_seats bs on bs.seat_id=b.seat_id where b.id='${BOOKING_ID}';")"

  case "$BOOKING_STATUS" in
    FAILED)
      test "$BOOKING_PAYMENT_SEAT_ROW" = "FAILED|FAILED|AVAILABLE" || exit 1
      ;;
    PAID)
      test "$BOOKING_PAYMENT_SEAT_ROW" = "PAID|SUCCEEDED|BOOKED" || exit 1
      PAID_BOOKING=1
      break
      ;;
    *)
      printf 'unexpected booking status: %s\n' "$BOOKING_STATUS"
      exit 1
      ;;
  esac
done

if [ "$PAID_BOOKING" -ne 1 ]; then
  printf '%s\n' 'mock randomness로 판정 불가'
  printf '%s\n' 'Restart the E2E once with a fresh performance and seat dataset.'
  exit 2
fi
```

Three mock failures are not an infrastructure failure. Classify that run only as
`mock randomness로 판정 불가`, then restart the E2E once with a fresh seat dataset.

### 4. Prove `BOOKING_PAID` publication and the original seat projection

Poll the booking outbox every two seconds for at most 60 seconds. Use this exact
SQL and require one `PUBLISHED|0` row:

```sql
select id,status,retry_count from outbox_events where aggregate_id='${BOOKING_ID}' and event_type='BOOKING_PAID' order by created_at desc limit 1;
```

```bash
unset BOOKING_EVENT_ID
for _ in {1..30}; do
  BOOKING_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${BOOKING_ID}' and event_type='BOOKING_PAID' order by created_at desc limit 1;")"
  case "$BOOKING_OUTBOX_ROW" in
    *'|PUBLISHED|0')
      BOOKING_EVENT_ID="${BOOKING_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep 2
done
test -n "${BOOKING_EVENT_ID:-}" || exit 1
test "$BOOKING_OUTBOX_ROW" = "${BOOKING_EVENT_ID}|PUBLISHED|0" || exit 1
```

Require the performance consumer to record that exact event ID:

```sql
select event_id from processed_events where event_id='${BOOKING_EVENT_ID}' and consumer_name='performance-booking-paid-consumer' and event_type='BOOKING_PAID';
```

```bash
for _ in {1..30}; do
  PERFORMANCE_PROCESSED_EVENT_ID="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${BOOKING_EVENT_ID}' and consumer_name='performance-booking-paid-consumer' and event_type='BOOKING_PAID';")"
  [ "$PERFORMANCE_PROCESSED_EVENT_ID" = "$BOOKING_EVENT_ID" ] && break
  sleep 2
done
test "$PERFORMANCE_PROCESSED_EVENT_ID" = "$BOOKING_EVENT_ID" || exit 1
```

Then query the original performance seat with this exact SQL and require `BOOKED`:

```sql
select status from seats where id='${SEAT_ID}' and performance_id='${PERFORMANCE_ID}';
```

```bash
for _ in {1..30}; do
  PERFORMANCE_SEAT_STATUS="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select status from seats where id='${SEAT_ID}' and performance_id='${PERFORMANCE_ID}';")"
  [ "$PERFORMANCE_SEAT_STATUS" = "BOOKED" ] && break
  sleep 2
done
test "$PERFORMANCE_SEAT_STATUS" = "BOOKED" || exit 1
```

### 5. Prove duplicate-seat defense

Run this only after a `PAID` result and after both the booking projection and the
original performance seat are `BOOKED`. Record the existing booking count by seat
and the payment-join-booking count by seat:

```bash
BOOKING_COUNT_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select count(*) from bookings where seat_id='${SEAT_ID}';")"
PAYMENT_COUNT_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select count(*) from payments p join bookings b on b.id=p.booking_id where b.seat_id='${SEAT_ID}';")"
```

Repeat the same request. It must return HTTP 409 with
`.message == "seat is not available"`:

```bash
DUPLICATE_HTTP="$(curl -sS --max-time 10 \
  -o duplicate-booking.json \
  -w '%{http_code}' \
  -X POST http://localhost:8003/bookings \
  -H "X-User-Id: ${USER_ID}" \
  -H 'Content-Type: application/json' \
  --data "{\"performanceId\":\"${PERFORMANCE_ID}\",\"seatId\":\"${SEAT_ID}\",\"amount\":10000}")"
test "$DUPLICATE_HTTP" = "409" || exit 1
jq -e '.message == "seat is not available"' duplicate-booking.json >/dev/null || exit 1
```

Both counts must remain unchanged, and both seat representations must remain
`BOOKED`:

```bash
BOOKING_COUNT_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select count(*) from bookings where seat_id='${SEAT_ID}';")"
PAYMENT_COUNT_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select count(*) from payments p join bookings b on b.id=p.booking_id where b.seat_id='${SEAT_ID}';")"
BOOKING_SEAT_STATUS="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select status from booking_seats where seat_id='${SEAT_ID}';")"
PERFORMANCE_SEAT_STATUS="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
  psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
  "select status from seats where id='${SEAT_ID}' and performance_id='${PERFORMANCE_ID}';")"

test "$BOOKING_COUNT_AFTER" = "$BOOKING_COUNT_BEFORE" || exit 1
test "$PAYMENT_COUNT_AFTER" = "$PAYMENT_COUNT_BEFORE" || exit 1
test "$BOOKING_SEAT_STATUS" = "BOOKED" || exit 1
test "$PERFORMANCE_SEAT_STATUS" = "BOOKED" || exit 1
```

## Dependency restart and recovery

Run one dependency drill at a time. Never restart two dependencies together.
Before each drill, save the pre-state named in the table. Use the merged project:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml restart <dependency>
```

After the restart, poll infrastructure state and all impacted applications for up
to 180 seconds, at two-second intervals. Then poll the follow-up HTTP/database/event
condition for up to 60 seconds, also at two-second intervals. Do not substitute a
fixed sleep.

| Dependency | Pre-state to record | Expected impacted applications | Follow-up | Pass criteria |
| --- | --- | --- | --- | --- |
| `user-db` | A representative `users` row and the `flyway_schema_history` version/checksum | `user-service` | Query the same user row and Flyway state | Application health returns; the same user row and migration history remain |
| `performance-db` | Existing performance and seat IDs | `performance-service` | Read the old IDs, then create a fresh performance | Old IDs remain and the fresh create returns the expected HTTP result within 60 seconds |
| `booking-db` | Existing booking, payment, and outbox IDs | `booking-service` | Read the old IDs, then complete a fresh paid booking | Old IDs remain; a fresh booking reaches `PAID`, its payment reaches `SUCCEEDED`, and the event cycle completes |
| `notification-db` | A uniquely named Mongo sentinel document | `notification-service` | Read the same sentinel document | Application health returns and the document is byte-for-byte equivalent |
| `redis` | Distinct performance, booking, and queue sentinel keys | `performance-service`, `booking-service`, `queue-service` | Read the same keys, then exercise a fresh booking lock | Keys remain and the fresh lock/booking path completes without manual application restart |
| `zookeeper` | Topic list and consumer-group offsets | Kafka, then `performance-service`, `booking-service`, and `notification-service` | Run a fresh two-way `SEATS_CREATED` and `BOOKING_PAID` cycle | Topics/offsets are preserved, both new events are processed, and no application restart is needed |
| `kafka` | Topic end offsets and consumer-group offsets | `performance-service`, `booking-service`, and `notification-service` | Run a fresh two-way `SEATS_CREATED` and `BOOKING_PAID` cycle | Existing offsets are preserved/nondecreasing, both new events are processed, and no application restart is needed |

Use a unique run ID for sentinel data. For example, create and read a Mongo
sentinel without printing the password:

```bash
RUN_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T notification-db \
  mongosh --quiet \
  --username "$MONGO_INITDB_ROOT_USERNAME" \
  --password "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  notification_db \
  --eval "db.m4_sentinels.updateOne({_id:'${RUN_ID}'},{\$set:{value:'preserve-me'}},{upsert:true})"
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T notification-db \
  mongosh --quiet \
  --username "$MONGO_INITDB_ROOT_USERNAME" \
  --password "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  notification_db \
  --eval "db.m4_sentinels.findOne({_id:'${RUN_ID}'})"
```

Record Kafka topics, topic end offsets, and consumer-group offsets from the broker
before a Zookeeper or Kafka drill. Use the exact topic names observed in that run;
do not infer offsets from application health:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T kafka \
  kafka-topics --bootstrap-server localhost:29092 --list
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T kafka \
  kafka-get-offsets --bootstrap-server localhost:29092 --topic <topic>
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T kafka \
  kafka-consumer-groups --bootstrap-server localhost:29092 --all-groups --describe
```

If an impacted application recovers only after that application is restarted,
record `automatic recovery failed / manual recovery succeeded`. The dependency
drill fails the M4 resilience criterion even if the manual restart restores service.

## Kafka negative case and known limitation

This is a controlled failure characterization, not a resilience-success test.
Before stopping Kafka, inspect and log the effective producer values of
`max.block.ms` and `delivery.timeout.ms` for the running performance-service
instance. Do not assume library defaults. If either effective value is unavailable,
do not run this negative case.

Set shell variables only from the observed values and calculate the deadline:

```bash
MAX_BLOCK_MS=<observed-effective-max.block.ms>
DELIVERY_TIMEOUT_MS=<observed-effective-delivery.timeout.ms>
NEGATIVE_DEADLINE_SECONDS=$(( (MAX_BLOCK_MS + DELIVERY_TIMEOUT_MS + 999) / 1000 + 30 ))
```

Stop Kafka through the merged project, create a fresh performance and fresh seat
dataset so a new `SEATS_CREATED` outbox event exists, and poll every two seconds:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml stop kafka
```

Within `NEGATIVE_DEADLINE_SECONDS`, the new performance outbox row is expected to
become `FAILED|1`:

```bash
NEGATIVE_ELAPSED=0
while [ "$NEGATIVE_ELAPSED" -le "$NEGATIVE_DEADLINE_SECONDS" ]; do
  NEGATIVE_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;")"
  case "$NEGATIVE_OUTBOX_ROW" in
    *'|FAILED|1')
      NEGATIVE_EVENT_ID="${NEGATIVE_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep 2
  NEGATIVE_ELAPSED=$((NEGATIVE_ELAPSED + 2))
done
test "$NEGATIVE_OUTBOX_ROW" = "${NEGATIVE_EVENT_ID}|FAILED|1" || exit 1
```

Recover Kafka and apply the 180-second infrastructure/application-health gate:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml up -d --wait --wait-timeout 180 kafka
```

After recovery, the exact same outbox row is expected to remain `FAILED|1`. Poll
for 60 seconds and require no matching booking `processed_events` row and no
`booking_seats` projection:

```bash
for _ in {1..30}; do
  RECOVERED_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where id='${NEGATIVE_EVENT_ID}';")"
  BOOKING_NEGATIVE_PROCESSED="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${NEGATIVE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';")"
  BOOKING_NEGATIVE_SEAT="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select seat_id from booking_seats where seat_id='${SEAT_ID}';")"
  sleep 2
done

test "$RECOVERED_OUTBOX_ROW" = "${NEGATIVE_EVENT_ID}|FAILED|1" || exit 1
test -z "$BOOKING_NEGATIVE_PROCESSED" || exit 1
test -z "$BOOKING_NEGATIVE_SEAT" || exit 1
```

Automatic outbox retry and DLQ handling are not implemented. Therefore this
negative case is not resilience success. After characterizing it, a normal fresh
`SEATS_CREATED` and `BOOKING_PAID` event cycle must still pass.

## Data preservation and shutdown

Before stopping the full stack, record:

- representative row IDs from the user, performance, and booking PostgreSQL databases;
- the Mongo sentinel document;
- the Redis sentinel keys and values;
- Kafka topics, topic end offsets, and consumer-group offsets.

Stop the merged project without `-v`:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml down
```

`down` removes containers and the project network, but named volumes and test data
remain. There are eight named volumes.

- `user-db-data`
- `performance-db-data`
- `booking-db-data`
- `notification-db-data`
- `redis-data`
- `zookeeper-data`
- `zookeeper-log`
- `kafka-data`

Start only the base infrastructure and wait up to 180 seconds:

```bash
docker compose --env-file .env -f docker-compose.yml up -d --wait --wait-timeout 180
```

Compare the same database rows, Mongo document, Redis keys, Kafka topics, end
offsets, and consumer offsets with the recorded pre-stop state. Then start the six
host-dev JVMs, run all six application-health polls, and run one fresh Phase 1 E2E
cycle. Stop the host JVMs and finish by taking down the base project:

```bash
docker compose --env-file .env -f docker-compose.yml down
```

The exact baseline cleanup command would add `-v`, deleting all eight named
volumes and their test data. `down -v` is destructive and forbidden without separate explicit approval.

## Limitations

- Local transport and broker/database connections are plaintext, single-node, and
  intended only for development credentials.
- Functional Phase 1 is limited to performance plus booking. API gateway, user,
  notification, and queue remain application-health/schema/configuration
  skeletons, not completed gateway/auth/notification/queue flows.
- An outbox event that reaches `FAILED` has no automatic retry or DLQ path.
- Redis Commander uses a mutable `latest` tag, so supply-chain byte
  reproducibility is not claimed.
- Image build inputs, runtime behavior, and image digest evidence must be verified
  for the particular run being evaluated; this guide does not provide that
  evidence.
