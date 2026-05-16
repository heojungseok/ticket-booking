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
