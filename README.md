# Mini ThingsBoard

A small IoT platform for managing devices and collecting their telemetry, built step by step to learn how
[ThingsBoard](https://github.com/thingsboard/thingsboard)'s monolithic architecture works.

## Goal

Rebuild the core ideas of ThingsBoard at a small scale:

- **Multi-tenancy:** each tenant owns device profiles and devices, and tenants can never see each other's data.
- **Device provisioning:** every device gets an access token that it uses to send data.
- **High-volume ingestion:** devices send telemetry over HTTP or MQTT, which goes through a message queue
  before it reaches the database.
- **Horizontal scaling:** several identical copies of one application, sharing the same database, cache and queue.

## Target architecture

```
                 ┌─────────┐
  devices / ───► │  Nginx  │  load balancer
  users          └────┬────┘
           ┌──────────┼──────────┐
           ▼          ▼          ▼
      ┌────────┐ ┌────────┐ ┌────────┐
      │  app 1 │ │  app 2 │ │  app 3 │   identical Spring Boot monoliths
      └───┬────┘ └───┬────┘ └───┬────┘
          └──────────┼──────────┘
     ┌───────────────┼────────────────┐
     ▼               ▼                ▼
┌──────────┐    ┌─────────┐    ┌──────────────────┐
│PostgreSQL│    │  Redis  │    │ Kafka + ZooKeeper│
│ entities │    │  cache  │    │ telemetry queue  │
└──────────┘    └─────────┘    └──────────────────┘
```

| Component | Role |
|---|---|
| **Spring Boot app** | REST API, device transports (HTTP, MQTT), business logic, Kafka producer and consumer |
| **PostgreSQL** | Source of truth for tenants, profiles, devices, credentials and telemetry |
| **Redis** | Cache for data read on every message (device credentials, profiles) |
| **Kafka + ZooKeeper** | Absorbs bursts of telemetry so ingestion never waits on the database |
| **Nginx** | Spreads requests across the app instances |

Everything runs locally with Docker Compose.

## Roadmap

The project is built in phases. Each phase is developed on its own branch and merged into `main` with a pull
request, so the history shows how the architecture grew.

- [x] **Phase 1, Foundation:** Spring Boot, PostgreSQL, domain model, device provisioning API
- [x] **Phase 2, Docker:** containerize the app; run it with Postgres using Docker Compose
- [x] **Phase 3, Caching:** Redis in front of device credentials and profiles
- [ ] **Phase 4, Ingestion:** telemetry through Kafka, saved by a consumer
- [ ] **Phase 5, Cluster:** 3 app replicas behind Nginx, sharing Postgres, Redis and Kafka
- [ ] **Phase 6, MQTT transport:** devices send telemetry over MQTT into the same Kafka pipeline; Nginx balances
  long-lived TCP connections
- [ ] **Phase 7, UI (optional):** Angular dashboard with login, device management and live telemetry over
  WebSockets

## Tech stack

Java 21 · Spring Boot · PostgreSQL · Flyway · Redis · Kafka · ZooKeeper · MQTT · Nginx · Docker Compose · Maven

## Getting started

**Prerequisites:** Docker.

```bash
docker compose up --build -d    # build the app image and start the whole stack
docker compose ps               # wait until every service shows "(healthy)"
curl http://localhost:8080/actuator/health
```

Stop it with `docker compose down`. Data is kept in a Docker volume; `docker compose down -v` also deletes it.

**Running the app outside Docker** (for development, needs JDK 21): start only the infrastructure with
`docker compose up -d postgres`, then run `./mvnw spring-boot:run`. The app's defaults point at `localhost`, and
`docker-compose.yml` overrides them with environment variables when the app runs in a container.

## Design principles

These hold for every phase:

- **Tenant isolation is enforced in the service layer.** Every tenant-scoped query filters by tenant. A resource
  that belongs to another tenant returns `404`, so its existence is not revealed.
- **The schema is hand-written.** Flyway applies versioned SQL migrations; Hibernate only validates that the
  entities match. A migration is never edited after it has been applied.
- **The database enforces invariants.** Uniqueness and references are database constraints, not
  checks done in Java before saving.
- **Entities reference each other by UUID**, not JPA relationships. This keeps queries explicit and entities
  easy to cache.
- **Every app instance is stateless.** All shared state lives in Postgres, Redis or Kafka, so any instance can
  handle any request.

## Project structure

Code is organized by feature, not by layer:

```
src/main/java/com/minitb/
├── common/       shared building blocks (base entity, base controller, error handling)
└── <feature>/    one package per feature: entity, repository, service, controller, DTOs
src/main/resources/
└── db/migration/ Flyway migrations
```
