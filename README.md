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

Install Bash 3.2 or newer, `uuidgen`, `curl`, `jq`, Docker, and Docker Compose v2.
Java 21 is also required when applications run on the host. Run the shell snippets
from the repository root; standard PostgreSQL/Kafka tools inside the containers
are used by the verification procedures.

Run this prerequisite probe in Bash. It checks each command independently,
parses the Bash invoked by `bash -c`, enforces Bash 3.2 or newer, and confirms this
Compose build supports the two wait flags used by the guide:

```bash
for required_command in bash uuidgen curl jq docker; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    printf 'missing required command: %s\n' "$required_command" >&2
    exit 1
  fi
done

BASH_VERSION_VALUE="$(bash -c 'printf "%s\n" "$BASH_VERSION"')" || exit 1
BASH_MAJOR="${BASH_VERSION_VALUE%%.*}"
BASH_VERSION_REST="${BASH_VERSION_VALUE#*.}"
BASH_MINOR="${BASH_VERSION_REST%%.*}"
case "$BASH_MAJOR" in
  ''|*[!0-9]*) printf '%s\n' 'could not parse Bash major version' >&2; exit 1 ;;
esac
case "$BASH_MINOR" in
  ''|*[!0-9]*) printf '%s\n' 'could not parse Bash minor version' >&2; exit 1 ;;
esac
if (( BASH_MAJOR < 3 || (BASH_MAJOR == 3 && BASH_MINOR < 2) )); then
  printf '%s\n' 'Bash 3.2 or newer is required' >&2
  exit 1
fi

docker compose version >/dev/null 2>&1 || exit 1
docker compose up --help | grep -q -- '--wait' || exit 1
docker compose up --help | grep -q -- '--wait-timeout' || exit 1
```

Create the local environment file:

```bash
[ ! -e .env ] || {
  printf '%s\n' '.env already exists; refusing to overwrite it' >&2
  exit 1
}
(
  umask 077
  cp .env.example .env
) || exit 1
chmod 600 .env || exit 1
```

The local-only `.env` is ignored and must not be committed. Replace every sample
secret before use, keep the file private, and never paste expanded secret values
into logs or evidence.

Before Bash sources `.env`, validate it as inert text and require exact private
file mode `600` with these canonical Bash-3.2-compatible functions. The validator
permits blank separator lines, but rejects comments, whitespace-only lines,
quotes, duplicate/unknown/missing keys, empty or malformed assignments, and every
character outside the conservative value alphabet. The loader accepts only the
exact repository-root `.env`, validates content and mode before sourcing
`./.env`, exports every assignment, and restores `set +a`. Error output identifies
only a line number, key, file, or mode requirement, never a value:

```bash
validate_dotenv() {
  local file="${1:-.env}"
  local line
  local line_number=0
  local key
  local value
  local expected
  local allowed
  local seen='|'
  local required_keys=(
    USER_DB_NAME
    USER_DB_USERNAME
    USER_DB_PASSWORD
    PERFORMANCE_DB_NAME
    PERFORMANCE_DB_USERNAME
    PERFORMANCE_DB_PASSWORD
    BOOKING_DB_NAME
    BOOKING_DB_USERNAME
    BOOKING_DB_PASSWORD
    MONGO_INITDB_ROOT_USERNAME
    MONGO_INITDB_ROOT_PASSWORD
    MONGODB_URI
    REDIS_HOST
    REDIS_PORT
    KAFKA_BOOTSTRAP_SERVERS
  )

  [ -r "$file" ] || {
    printf 'dotenv is not readable: %s\n' "$file" >&2
    return 1
  }

  while IFS= read -r line || [ -n "$line" ]; do
    line_number=$((line_number + 1))
    [ -z "$line" ] && continue

    if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=([A-Za-z0-9._:/@?=-]+)$ ]]; then
      printf 'dotenv line %s is not a supported assignment\n' "$line_number" >&2
      return 1
    fi

    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    allowed=0
    for expected in "${required_keys[@]}"; do
      if [ "$key" = "$expected" ]; then
        allowed=1
        break
      fi
    done
    if [ "$allowed" -ne 1 ]; then
      printf 'dotenv contains unknown key: %s\n' "$key" >&2
      return 1
    fi

    case "$seen" in
      *"|${key}|"*)
        printf 'dotenv contains duplicate key: %s\n' "$key" >&2
        return 1
        ;;
    esac
    seen="${seen}${key}|"

    case "$key" in
      MONGO_INITDB_ROOT_USERNAME|MONGO_INITDB_ROOT_PASSWORD)
        if [[ ! "$value" =~ ^[A-Za-z0-9._-]+$ ]]; then
          printf 'dotenv Mongo credential is not URI-safe: %s\n' "$key" >&2
          return 1
        fi
        ;;
    esac
  done < "$file"

  for expected in "${required_keys[@]}"; do
    case "$seen" in
      *"|${expected}|"*) ;;
      *)
        printf 'dotenv is missing required key: %s\n' "$expected" >&2
        return 1
        ;;
    esac
  done
}

load_dotenv() {
  local file="${1:-.env}"
  local mode

  [ "$file" = ".env" ] || {
    printf 'dotenv loader accepts only .env: %s\n' "$file" >&2
    return 1
  }
  [ -f "$file" ] && [ ! -L "$file" ] || {
    printf 'dotenv must be a regular non-symlink file: %s\n' "$file" >&2
    return 1
  }

  if mode="$(stat -f %Lp "$file" 2>/dev/null)"; then
    :
  elif mode="$(stat -c %a "$file" 2>/dev/null)"; then
    :
  else
    printf 'could not read dotenv mode: %s\n' "$file" >&2
    return 1
  fi
  [ "$mode" = "600" ] || {
    printf 'dotenv must have mode 600: %s\n' "$file" >&2
    return 1
  }

  validate_dotenv "$file" || return 1
  set -a
  if ! . ./.env; then
    set +a
    return 1
  fi
  set +a
}

load_dotenv .env || exit 1
```

The allowed general value alphabet is `[A-Za-z0-9._:/@?=-]+`. Mongo root
username/password values are further limited to `[A-Za-z0-9._-]+`. Consequently,
the shared file cannot contain shell-executable characters such as `$`, backticks,
`;`, `&`, `|`, parentheses, redirects, backslashes, spaces, comments, or quotes.
This restricted shared dotenv format prevents Bash execution and Compose semantic drift. A secret that cannot fit the format requires redesigning/encoding
the connection configuration; do not copy-paste it into this sourced file.

Run the canonical validator/loader block once in every new verification Bash
shell and require `load_dotenv .env || exit 1` before Compose or host processes.
Later sections reuse the functions by name instead of redefining either one.
Sourcing the validated file overwrites ordinary ambient variables with its exact
assignments, so `.env` is the actual environment input and `--env-file` is not the
only protection against ambient precedence.

Verify that ambient input is overwritten without printing either value:

```bash
EXPECTED_USER_DB_NAME="$(sed -n 's/^USER_DB_NAME=//p' .env)" || exit 1
test -n "$EXPECTED_USER_DB_NAME" || exit 1
export USER_DB_NAME=task4-ambient-override-must-not-survive
load_dotenv .env || exit 1
test "$USER_DB_NAME" = "$EXPECTED_USER_DB_NAME" || exit 1
unset EXPECTED_USER_DB_NAME
docker compose --env-file .env -f docker-compose.yml config --quiet || exit 1
```

Also prove the base configuration fails closed when no required database
initialization input exists. Both output streams are discarded so this negative
test cannot expose values:

```bash
if env -i PATH="$PATH" docker compose --env-file /dev/null \
  -f docker-compose.yml config --quiet >/dev/null 2>&1; then
  printf '%s\n' 'empty environment unexpectedly rendered the base configuration' >&2
  exit 1
fi
```

The current MongoDB URI is assembled directly from the root username and password
in container mode, while host mode reads the URI from `.env`. Until that connection
construction is redesigned to encode credentials, local Mongo root usernames and
passwords must avoid URI-reserved characters such as `@`, `:`, `/`, `?`, `#`, `%`,
and similar delimiters. Keep `MONGODB_URI` synchronized with the chosen local
credentials.

Initialization credentials must be chosen before the first named-volume creation.
Changing PostgreSQL or Mongo initialization values in `.env` later does not rotate
credentials already stored in existing named volumes. Credential rotation needs
an explicit database migration/redesign; destructive volume recreation remains a
separately approved operation.

For IntelliJ, open the repository root as a Gradle project and select a Java 21
SDK/toolchain. Do not depend on a machine-specific repository or JDK path.

### Host-dev mode

Host-dev mode runs only the base infrastructure in containers:

```bash
load_dotenv .env || exit 1
docker compose --env-file .env -f docker-compose.yml up -d --wait --wait-timeout 180
```

Run each application as a separate host process, with `.env` explicitly exported
in that terminal. First run the canonical validator function in each terminal;
then use this reusable form:

```bash
load_dotenv .env || exit 1; ./gradlew :<module>:bootRun
```

Use one terminal/process for each of the six modules:

```bash
load_dotenv .env || exit 1; ./gradlew :api-gateway:bootRun
load_dotenv .env || exit 1; ./gradlew :user-service:bootRun
load_dotenv .env || exit 1; ./gradlew :performance-service:bootRun
load_dotenv .env || exit 1; ./gradlew :booking-service:bootRun
load_dotenv .env || exit 1; ./gradlew :notification-service:bootRun
load_dotenv .env || exit 1; ./gradlew :queue-service:bootRun
```

This mode preserves each application's localhost defaults and the infrastructure
host ports in `docker-compose.yml`. Before stopping the base infrastructure, stop
all six host JVMs (for example, Ctrl-C in each terminal). Then remove only the base
containers and network, without deleting volumes:

```bash
load_dotenv .env || exit 1
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
load_dotenv .env || exit 1
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
only when the response is HTTP 200 and `jq -e '.status == "UP"'` succeeds. The
deadline is fixed before the first request, so command execution time counts
against it; each sleep is `min(2, remaining)` seconds:

```bash
sleep_until_poll_deadline() {
  local deadline="$1"
  local remaining=$((deadline - SECONDS))

  (( remaining > 0 )) || return 1
  (( remaining > 2 )) && remaining=2
  sleep "$remaining"
}

poll_health() {
  local service="$1"
  local url="$2"
  local deadline=$((SECONDS + 180))
  local remaining
  local curl_timeout
  local code

  while (( SECONDS < deadline )); do
    remaining=$((deadline - SECONDS))
    curl_timeout="$remaining"
    (( curl_timeout > 3 )) && curl_timeout=3
    code="$(curl -sS --max-time "$curl_timeout" -o "${service}.json" -w '%{http_code}' "$url" || true)"
    if [ "$code" = "200" ] && jq -e '.status == "UP"' "${service}.json" >/dev/null; then
      return 0
    fi
    sleep_until_poll_deadline "$deadline" || break
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
all six application-health checks pass. Continue in the same verification Bash
shell so the canonical dotenv validator and application-health polling helpers
remain defined. If a new shell is necessary, rerun the single canonical validator
block and the single application-health helper block above before continuing.
Export `.env` only after validation succeeds:

```bash
load_dotenv .env || exit 1
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
Each loop fixes its wall-clock deadline before the first query, so database command
time counts against the same deadline. First, run this exact performance outbox
query:

```sql
select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;
```

The command and assertion are:

```bash
unset PERFORMANCE_EVENT_ID PERFORMANCE_OUTBOX_ROW
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  PERFORMANCE_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;")"
  case "$PERFORMANCE_OUTBOX_ROW" in
    *'|PUBLISHED|0')
      PERFORMANCE_EVENT_ID="${PERFORMANCE_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep_until_poll_deadline "$deadline" || break
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
unset BOOKING_SEAT_ROW
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  BOOKING_SEAT_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select performance_id,status from booking_seats where seat_id='${SEAT_ID}';")"
  [ "$BOOKING_SEAT_ROW" = "${PERFORMANCE_ID}|AVAILABLE" ] && break
  sleep_until_poll_deadline "$deadline" || break
done
test "$BOOKING_SEAT_ROW" = "${PERFORMANCE_ID}|AVAILABLE" || exit 1
```

Finally, prove the booking consumer recorded the same event ID with this exact SQL:

```sql
select event_id from processed_events where event_id='${PERFORMANCE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';
```

```bash
unset BOOKING_PROCESSED_EVENT_ID
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  BOOKING_PROCESSED_EVENT_ID="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${PERFORMANCE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';")"
  [ "$BOOKING_PROCESSED_EVENT_ID" = "$PERFORMANCE_EVENT_ID" ] && break
  sleep_until_poll_deadline "$deadline" || break
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
unset BOOKING_EVENT_ID BOOKING_OUTBOX_ROW
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  BOOKING_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${BOOKING_ID}' and event_type='BOOKING_PAID' order by created_at desc limit 1;")"
  case "$BOOKING_OUTBOX_ROW" in
    *'|PUBLISHED|0')
      BOOKING_EVENT_ID="${BOOKING_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep_until_poll_deadline "$deadline" || break
done
test -n "${BOOKING_EVENT_ID:-}" || exit 1
test "$BOOKING_OUTBOX_ROW" = "${BOOKING_EVENT_ID}|PUBLISHED|0" || exit 1
```

Require the performance consumer to record that exact event ID:

```sql
select event_id from processed_events where event_id='${BOOKING_EVENT_ID}' and consumer_name='performance-booking-paid-consumer' and event_type='BOOKING_PAID';
```

```bash
unset PERFORMANCE_PROCESSED_EVENT_ID
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  PERFORMANCE_PROCESSED_EVENT_ID="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${BOOKING_EVENT_ID}' and consumer_name='performance-booking-paid-consumer' and event_type='BOOKING_PAID';")"
  [ "$PERFORMANCE_PROCESSED_EVENT_ID" = "$BOOKING_EVENT_ID" ] && break
  sleep_until_poll_deadline "$deadline" || break
done
test "$PERFORMANCE_PROCESSED_EVENT_ID" = "$BOOKING_EVENT_ID" || exit 1
```

Then query the original performance seat with this exact SQL and require `BOOKED`:

```sql
select status from seats where id='${SEAT_ID}' and performance_id='${PERFORMANCE_ID}';
```

```bash
unset PERFORMANCE_SEAT_STATUS
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  PERFORMANCE_SEAT_STATUS="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select status from seats where id='${SEAT_ID}' and performance_id='${PERFORMANCE_ID}';")"
  [ "$PERFORMANCE_SEAT_STATUS" = "BOOKED" ] && break
  sleep_until_poll_deadline "$deadline" || break
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
load_dotenv .env || exit 1
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml restart <dependency>
```

After the restart, poll infrastructure state and all impacted applications for up
to 180 seconds, at two-second intervals. Then poll the follow-up HTTP/database/event
condition for up to 60 seconds, also at two-second intervals. Do not substitute a
fixed sleep. Keep the Bash polling helpers from the application-health/E2E setup
in the current shell. This infrastructure helper requires the restarted
dependency's existing Compose healthcheck to return `healthy` within an actual
180-second wall-clock deadline:

```bash
poll_infra_health() {
  local service="$1"
  local deadline=$((SECONDS + 180))
  local container_id
  local health

  while (( SECONDS < deadline )); do
    container_id="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml ps -q "$service")"
    if [ -n "$container_id" ]; then
      health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
      [ "$health" = "healthy" ] && return 0
    fi
    sleep_until_poll_deadline "$deadline" || break
  done

  printf '%s did not become healthy within 180 seconds\n' "$service"
  return 1
}

poll_infra_health <dependency> || exit 1
```

Invoke `poll_health` for every impacted application listed in the matrix. Use the
60-second wall-clock loops from the functional E2E for fresh HTTP/event follow-up
checks, and save each command/result in the result record.

| Dependency | Pre-state to record | Expected impacted applications | Follow-up | Pass criteria |
| --- | --- | --- | --- | --- |
| `user-db` | A representative `users` row and the `flyway_schema_history` version/checksum | `user-service` | Query the same user row and Flyway state | Application health returns; the same user row and migration history remain |
| `performance-db` | Existing performance and seat IDs | `performance-service` | Read the old IDs, then create a fresh performance | Old IDs remain and the fresh create returns the expected HTTP result within 60 seconds |
| `booking-db` | Existing booking, payment, and outbox IDs | `booking-service` | Read the old IDs, then complete a fresh paid booking | Old IDs remain; a fresh booking reaches `PAID`, its payment reaches `SUCCEEDED`, and the event cycle completes |
| `notification-db` | A uniquely named Mongo sentinel document | `notification-service` | Read the same sentinel document | Application health returns and the document is byte-for-byte equivalent |
| `redis` | Distinct performance, booking, and queue sentinel keys | `performance-service`, `booking-service`, `queue-service` | Read the same keys, then exercise a fresh booking lock | Keys remain and the fresh lock/booking path completes without manual application restart |
| `zookeeper` | Topic list and consumer-group offsets | Kafka, then `performance-service`, `booking-service`, and `notification-service` | Run a fresh two-way `SEATS_CREATED` and `BOOKING_PAID` cycle | Topics/offsets are preserved, both new events are processed, and no application restart is needed |
| `kafka` | Topic end offsets and consumer-group offsets | `performance-service`, `booking-service`, and `notification-service` | Run a fresh two-way `SEATS_CREATED` and `BOOKING_PAID` cycle | Existing offsets are preserved/nondecreasing, both new events are processed, and no application restart is needed |

Complete one result row for every dependency drill. Store artifact paths, captured
command output, or exact IDs in the evidence cells rather than writing only a
summary:

| Dependency | Pre-state artifact/evidence | Infra/app deadline result | Follow-up result | `automatic recovery PASS\|FAIL` | Optional `manual recovery PASS\|FAIL` |
| --- | --- | --- | --- | --- | --- |
| `user-db` | `<user row + Flyway capture>` | `<180s result/evidence>` | `<same-row result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `performance-db` | `<performance/seat capture>` | `<180s result/evidence>` | `<fresh-performance result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `booking-db` | `<booking/payment/outbox capture>` | `<180s result/evidence>` | `<fresh-paid-booking result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `notification-db` | `<Mongo sentinel capture>` | `<180s result/evidence>` | `<same-document result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `redis` | `<three-key capture>` | `<180s result/evidence>` | `<fresh-lock result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `zookeeper` | `<topics/offsets capture>` | `<180s result/evidence>` | `<fresh-two-way-event result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |
| `kafka` | `<end/consumer-offset capture>` | `<180s result/evidence>` | `<fresh-two-way-event result/evidence>` | `<PASS\|FAIL>` | `<PASS\|FAIL\|N/A>` |

Any `automatic recovery FAIL` fails M4 even when manual recovery passes. If an
automatic result fails, manual recovery is optional diagnostic evidence; it cannot
turn the automatic result or M4 result into a pass.

### Persistence sentinels

Use one fresh lower-case UUID as the run ID for the PostgreSQL, MongoDB, and Redis
sentinels. Export `.env` in the same shell:

```bash
load_dotenv .env || exit 1
RUN_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
```

On a clean boot, wait for `user-service` application health first so Flyway has
created the schema. Then insert a user-database sentinel with fixed, non-secret
test values. This direct SQL row is only a persistence sentinel; it is not a user
signup flow and must not be presented as one:

```bash
USER_SENTINEL_INSERT="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T user-db \
  psql -v ON_ERROR_STOP=1 -U "$USER_DB_USERNAME" -d "$USER_DB_NAME" -Atc \
  "insert into users(id,email,password_hash,name,role,status,created_at,updated_at) values ('${RUN_ID}','task4-${RUN_ID}@example.invalid','task4-fixed-test-hash','Task 4 Sentinel','USER','ACTIVE',current_timestamp,current_timestamp) returning id,email,password_hash,name,role,status;")" || exit 1
test -n "$USER_SENTINEL_INSERT" || exit 1

USER_SENTINEL_ROW_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T user-db \
  psql -v ON_ERROR_STOP=1 -U "$USER_DB_USERNAME" -d "$USER_DB_NAME" -Atc \
  "select id,email,password_hash,name,role,status from users where id='${RUN_ID}';")" || exit 1
FLYWAY_STATE_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T user-db \
  psql -v ON_ERROR_STOP=1 -U "$USER_DB_USERNAME" -d "$USER_DB_NAME" -Atc \
  "select version,checksum from flyway_schema_history order by installed_rank;")" || exit 1
test -n "$USER_SENTINEL_ROW_BEFORE" || exit 1
test -n "$FLYWAY_STATE_BEFORE" || exit 1
```

After the `user-db` restart or full-stack down/up cycle, read and compare the exact
same row and migration state:

```bash
USER_SENTINEL_ROW_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T user-db \
  psql -v ON_ERROR_STOP=1 -U "$USER_DB_USERNAME" -d "$USER_DB_NAME" -Atc \
  "select id,email,password_hash,name,role,status from users where id='${RUN_ID}';")" || exit 1
FLYWAY_STATE_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T user-db \
  psql -v ON_ERROR_STOP=1 -U "$USER_DB_USERNAME" -d "$USER_DB_NAME" -Atc \
  "select version,checksum from flyway_schema_history order by installed_rank;")" || exit 1
test "$USER_SENTINEL_ROW_AFTER" = "$USER_SENTINEL_ROW_BEFORE" || exit 1
test "$FLYWAY_STATE_AFTER" = "$FLYWAY_STATE_BEFORE" || exit 1
```

Create three distinct Redis sentinels with representative, non-secret values and
capture them before the restart or full-stack down:

```bash
REDIS_PERFORMANCE_KEY="task4:persistence:${RUN_ID}:performance"
REDIS_BOOKING_KEY="task4:persistence:${RUN_ID}:booking"
REDIS_QUEUE_KEY="task4:persistence:${RUN_ID}:queue"

test "$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli SET "$REDIS_PERFORMANCE_KEY" task4-performance-preserve)" = "OK" || exit 1
test "$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli SET "$REDIS_BOOKING_KEY" task4-booking-preserve)" = "OK" || exit 1
test "$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli SET "$REDIS_QUEUE_KEY" task4-queue-preserve)" = "OK" || exit 1

REDIS_PERFORMANCE_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_PERFORMANCE_KEY")"
REDIS_BOOKING_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_BOOKING_KEY")"
REDIS_QUEUE_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_QUEUE_KEY")"
test "$REDIS_PERFORMANCE_BEFORE" = "task4-performance-preserve" || exit 1
test "$REDIS_BOOKING_BEFORE" = "task4-booking-preserve" || exit 1
test "$REDIS_QUEUE_BEFORE" = "task4-queue-preserve" || exit 1
```

After the restart or full-stack up, read the same keys and compare their values:

```bash
REDIS_PERFORMANCE_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_PERFORMANCE_KEY")"
REDIS_BOOKING_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_BOOKING_KEY")"
REDIS_QUEUE_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T redis redis-cli GET "$REDIS_QUEUE_KEY")"
test "$REDIS_PERFORMANCE_AFTER" = "$REDIS_PERFORMANCE_BEFORE" || exit 1
test "$REDIS_BOOKING_AFTER" = "$REDIS_BOOKING_BEFORE" || exit 1
test "$REDIS_QUEUE_AFTER" = "$REDIS_QUEUE_BEFORE" || exit 1
```

Upsert the Mongo sentinel, then capture a deterministic JSON projection containing
only `_id` and `value`. `--quiet` and the projection prevent unrelated document
fields from entering the evidence. The host passes only literal credential
variable names in the single-quoted `sh -ec` program; each credential expands
only inside the database container, and none of these commands prints it:

```bash
MONGO_SENTINEL_EXPECTED="{\"_id\":\"${RUN_ID}\",\"value\":\"preserve-me\"}"
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T \
  -e TASK4_RUN_ID="$RUN_ID" notification-db sh -ec '
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      notification_db \
      --eval "const runId=process.env.TASK4_RUN_ID; const result=db.m4_sentinels.updateOne({_id:runId},{\$set:{value:\"preserve-me\"}},{upsert:true}); if (!result.acknowledged) { quit(2); }"
  ' >/dev/null || exit 1

MONGO_SENTINEL_BEFORE="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T \
  -e TASK4_RUN_ID="$RUN_ID" notification-db sh -ec '
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      notification_db \
      --eval "const runId=process.env.TASK4_RUN_ID; const doc=db.m4_sentinels.findOne({_id:runId},{_id:1,value:1}); if (doc === null) { quit(2); } print(JSON.stringify({_id:doc._id,value:doc.value}));"
  ')" || exit 1
test -n "$MONGO_SENTINEL_BEFORE" || exit 1
test "$MONGO_SENTINEL_BEFORE" = "$MONGO_SENTINEL_EXPECTED" || exit 1
```

After the `notification-db` restart or full-stack down/up cycle, capture the same
canonical projection and require exact equality:

```bash
MONGO_SENTINEL_AFTER="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T \
  -e TASK4_RUN_ID="$RUN_ID" notification-db sh -ec '
    mongosh --quiet \
      --username "$MONGO_INITDB_ROOT_USERNAME" \
      --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      notification_db \
      --eval "const runId=process.env.TASK4_RUN_ID; const doc=db.m4_sentinels.findOne({_id:runId},{_id:1,value:1}); if (doc === null) { quit(2); } print(JSON.stringify({_id:doc._id,value:doc.value}));"
  ')" || exit 1
test -n "$MONGO_SENTINEL_AFTER" || exit 1
test "$MONGO_SENTINEL_AFTER" = "$MONGO_SENTINEL_EXPECTED" || exit 1
test "$MONGO_SENTINEL_AFTER" = "$MONGO_SENTINEL_BEFORE" || exit 1
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

This is an all-container-local-only controlled failure characterization, not a
resilience-success test. First complete and settle a normal E2E, including both
processed-event assertions. That cycle initializes the performance producer and
proves there is no unresolved normal functional step.

Read the effective producer timeouts from the latest sanitized `ProducerConfig`
lines in the performance-service container log. The pipeline retains only the two
numeric key/value lines; it neither prints nor stores the full log:

```bash
PRODUCER_TIMEOUT_LINES="$(
  docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml logs --no-color performance-service 2>/dev/null |
    sed -nE 's/^.*(max\.block\.ms|delivery\.timeout\.ms)[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1=\2/p'
)"
MAX_BLOCK_MS="$(printf '%s\n' "$PRODUCER_TIMEOUT_LINES" | awk -F= '$1 == "max.block.ms" { print $2 }' | tail -n 1)"
DELIVERY_TIMEOUT_MS="$(printf '%s\n' "$PRODUCER_TIMEOUT_LINES" | awk -F= '$1 == "delivery.timeout.ms" { print $2 }' | tail -n 1)"

case "$MAX_BLOCK_MS" in
  ''|*[!0-9]*)
    printf '%s\n' 'numeric max.block.ms was not found; aborting negative drill' >&2
    exit 1
    ;;
esac
case "$DELIVERY_TIMEOUT_MS" in
  ''|*[!0-9]*)
    printf '%s\n' 'numeric delivery.timeout.ms was not found; aborting negative drill' >&2
    exit 1
    ;;
esac
[ "$MAX_BLOCK_MS" -gt 0 ] || exit 1
[ "$DELIVERY_TIMEOUT_MS" -gt 0 ] || exit 1

NEGATIVE_DEADLINE_SECONDS=$(( (MAX_BLOCK_MS + DELIVERY_TIMEOUT_MS + 999) / 1000 + 30 ))
```

If either effective value is missing, nonnumeric, or zero, abort; do not infer a
library default. Host-dev logs are intentionally out of scope for this drill.

Immediately before stopping Kafka, require both outbox tables to have zero
`PENDING` rows:

```bash
PERFORMANCE_PENDING_COUNT="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
  psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
  "select count(*) from outbox_events where status='PENDING';")" || exit 1
BOOKING_PENDING_COUNT="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
  psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
  "select count(*) from outbox_events where status='PENDING';")" || exit 1
test "$PERFORMANCE_PENDING_COUNT" = "0" || exit 1
test "$BOOKING_PENDING_COUNT" = "0" || exit 1
```

This settled-E2E/zero-backlog gate removes sequential pending work from the
timeout-derived deadline. Stop Kafka through the merged project:

```bash
load_dotenv .env || exit 1
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml stop kafka
```

After Kafka is stopped, repeat only Phase 1 step 1 with fresh IDs: create one fresh
performance and one seat batch. That produces the single fresh negative-case
`SEATS_CREATED` event. Do not create a booking or any other outbox work during this
negative window.

Within `NEGATIVE_DEADLINE_SECONDS`, the new performance outbox row is expected to
become `FAILED|1`:

```bash
unset NEGATIVE_EVENT_ID NEGATIVE_OUTBOX_ROW
deadline=$((SECONDS + NEGATIVE_DEADLINE_SECONDS))
while (( SECONDS < deadline )); do
  NEGATIVE_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where aggregate_id='${PERFORMANCE_ID}' and event_type='SEATS_CREATED' order by created_at desc limit 1;")"
  case "$NEGATIVE_OUTBOX_ROW" in
    *'|FAILED|1')
      NEGATIVE_EVENT_ID="${NEGATIVE_OUTBOX_ROW%%|*}"
      break
      ;;
  esac
  sleep_until_poll_deadline "$deadline" || break
done
test -n "${NEGATIVE_EVENT_ID:-}" || exit 1
test "$NEGATIVE_OUTBOX_ROW" = "${NEGATIVE_EVENT_ID}|FAILED|1" || exit 1
```

Recover Kafka and apply the 180-second infrastructure/application-health gate:

```bash
load_dotenv .env || exit 1
docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml up -d --wait --wait-timeout 180 kafka
```

Re-run application health for the three applications included in the Kafka
recovery observation with the wall-clock-bounded `poll_health` helper. Each call
independently requires HTTP 200 and `.status == "UP"` within 180 seconds:

```bash
poll_health performance-service http://localhost:8002/actuator/health || exit 1
poll_health booking-service http://localhost:8003/actuator/health || exit 1
poll_health notification-service http://localhost:8004/actuator/health || exit 1
```

`notification-service` currently has no Kafka listener, producer behavior, or Kafka health contributor. Its HTTP result is only an
application-health observation; it does not prove broker reconnect behavior.
Broker reconnect proof comes from the required fresh performance-to-booking and
booking-to-performance event cycle.

After recovery, the exact same outbox row is expected to remain `FAILED|1`. Poll
for 60 seconds and require no matching booking `processed_events` row and no
`booking_seats` projection:

```bash
unset RECOVERED_OUTBOX_ROW BOOKING_NEGATIVE_PROCESSED BOOKING_NEGATIVE_SEAT
deadline=$((SECONDS + 60))
while (( SECONDS < deadline )); do
  RECOVERED_OUTBOX_ROW="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T performance-db \
    psql -U "$PERFORMANCE_DB_USERNAME" -d "$PERFORMANCE_DB_NAME" -Atc \
    "select id,status,retry_count from outbox_events where id='${NEGATIVE_EVENT_ID}';")"
  BOOKING_NEGATIVE_PROCESSED="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select event_id from processed_events where event_id='${NEGATIVE_EVENT_ID}' and consumer_name='booking-seats-created-consumer' and event_type='SEATS_CREATED';")"
  BOOKING_NEGATIVE_SEAT="$(docker compose --env-file .env -f docker-compose.yml -f docker-compose.app.yml exec -T booking-db \
    psql -U "$BOOKING_DB_USERNAME" -d "$BOOKING_DB_NAME" -Atc \
    "select seat_id from booking_seats where seat_id='${SEAT_ID}';")"
  test "$RECOVERED_OUTBOX_ROW" = "${NEGATIVE_EVENT_ID}|FAILED|1" || exit 1
  test -z "$BOOKING_NEGATIVE_PROCESSED" || exit 1
  test -z "$BOOKING_NEGATIVE_SEAT" || exit 1
  sleep_until_poll_deadline "$deadline" || break
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

Use the `*_BEFORE` captures from the persistence-sentinel procedures above for the
user row, Flyway state, all three Redis keys, and the canonical Mongo projection.
After the base stack returns, run the matching `*_AFTER` blocks and require every
equality assertion before starting host-dev applications.

Stop the merged project without `-v`:

```bash
load_dotenv .env || exit 1
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
load_dotenv .env || exit 1
docker compose --env-file .env -f docker-compose.yml up -d --wait --wait-timeout 180
```

Compare the same database rows, Mongo document, Redis keys, Kafka topics, end
offsets, and consumer offsets with the recorded pre-stop state. Then start the six
host-dev JVMs, run all six application-health polls, and run one fresh Phase 1 E2E
cycle. Stop the host JVMs and finish by taking down the base project:

```bash
load_dotenv .env || exit 1
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
- Application services have no Compose healthcheck or restart policy. The
  dependency drills verify reconnect behavior of surviving application processes;
  they do not verify application process or Docker daemon crash recovery.
- An outbox event that reaches `FAILED` has no automatic retry or DLQ path.
- Redis Commander uses a mutable `latest` tag, so supply-chain byte
  reproducibility is not claimed.
- Image build inputs, runtime behavior, and image digest evidence must be verified
  for the particular run being evaluated; this guide does not provide that
  evidence.
