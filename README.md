# Ticket Booking

Ticket booking MSA playground for FCFS, raffle, and general booking flows.

## Stack

- Java 21
- Gradle Groovy DSL
- Spring Boot 3.3.x
- PostgreSQL, MongoDB, Redis, Kafka, Zookeeper
- Swagger via springdoc-openapi 2.5.0

## Services

| Service | Port | Role |
| --- | ---: | --- |
| api-gateway | 8000 | Routing, JWT boundary, rate limiting entrypoint |
| user-service | 8001 | Signup, login, JWT issuing |
| performance-service | 8002 | Performances, seats, sale type management |
| booking-service | 8003 | Booking, stock, holds, idempotency, raffle |
| notification-service | 8004 | Kafka consumer and notification storage |
| queue-service | 8005 | FCFS queue and entry token issuing |

## Local Infra

```bash
docker compose up -d
```

Kafka UI: http://localhost:8080
Redis Commander: http://localhost:8081

## Local App Containers

Build the Spring Boot jars before building service images:

```bash
./gradlew build
```

Run the infra stack plus all six application containers:

```bash
docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.app.yml up --build -d
```

Health checks:

```bash
curl http://localhost:8000/health
curl http://localhost:8001/health
curl http://localhost:8002/health
curl http://localhost:8003/health
curl http://localhost:8004/health
curl http://localhost:8005/health
```

Stop the full local stack:

```bash
docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.app.yml down
```

Keep `.env` local. Use `.env.example` as the deployment template and replace every `change-me` value before using it on a server.

## Gradle

```bash
./gradlew projects
./gradlew build
./gradlew :api-gateway:bootRun
```

## IntelliJ

Open this directory as a Gradle project:

```text
/Users/heojungseok/Desktop/workspace/ticket-booking
```

Use `graalvm-jdk-21` / Java 21. This project pins Gradle to:

```text
/Users/heojungseok/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
```

The Gradle build is also configured with Java 21 toolchains.

Service run configurations are stored under `.idea/runConfigurations` and run each service through Gradle `bootRun`.

Start Docker Compose infra before running services that need PostgreSQL, MongoDB, Redis, or Kafka.

## CI/CD Slice

The first deployment slice keeps Java application behavior unchanged and verifies the packaging path:

1. `./gradlew build`
2. `docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.app.yml config --quiet`
3. Docker image build for each service

GitHub Actions runs this flow in `.github/workflows/build-images.yml` on pull requests, pushes to `main`, and manual dispatches.

AWS deployment is intentionally not automated yet. For the first cloud deployment, use one short-lived EC2 instance and this Docker Compose stack. Avoid RDS, NAT Gateway, and Load Balancer until the EC2-only deployment has been verified and the cost impact is clear.

When EC2 is ready, store server access and runtime secrets in GitHub Actions secrets instead of committing them:

```text
EC2_HOST
EC2_USER
EC2_SSH_KEY
USER_DB_PASSWORD
PERFORMANCE_DB_PASSWORD
BOOKING_DB_PASSWORD
MONGO_INITDB_ROOT_PASSWORD
```
