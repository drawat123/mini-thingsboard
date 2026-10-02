# Mini ThingsBoard

A small IoT platform for managing devices and collecting their telemetry, built step by step to learn how
[ThingsBoard](https://github.com/thingsboard/thingsboard)'s monolithic architecture works.

## Goal

Rebuild the core ideas of ThingsBoard at a small scale:

- **Multi-tenancy:** each tenant owns device profiles and devices, and tenants can never see each other's data.
- **Device provisioning:** every device gets an access token that it uses to send data.
- **High-volume ingestion:** devices send telemetry, which goes through a message queue before it reaches the
  database.
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
| **Spring Boot app** | REST API, business logic, Kafka producer and consumer |
| **PostgreSQL** | Source of truth for tenants, profiles, devices, credentials and telemetry |
| **Redis** | Cache for data read on every message (device credentials, profiles) |
| **Kafka + ZooKeeper** | Absorbs bursts of telemetry so ingestion never waits on the database |
| **Nginx** | Spreads requests across the app instances |

Everything runs locally with Docker Compose.

## Roadmap

The project is built in phases. Each phase is developed on its own branch and merged into `main` with a pull
request, so the history shows how the architecture grew.

- [x] **Phase 1, Foundation:** Spring Boot, PostgreSQL, domain model, device provisioning API
- [ ] **Phase 2, Docker:** containerize the app; run it with Postgres using Docker Compose
- [ ] **Phase 3, Caching:** Redis in front of device credentials and profiles
- [ ] **Phase 4, Ingestion:** telemetry through Kafka, saved by a consumer
- [ ] **Phase 5, Cluster:** 3 app replicas behind Nginx, sharing Postgres, Redis and Kafka

## Tech stack

Java 21 · Spring Boot · PostgreSQL · Flyway · Redis · Kafka · ZooKeeper · Nginx · Docker Compose · Maven

## Getting started

**Prerequisites:** JDK 21 and Docker.

> These steps run the app directly on your machine. From Phase 2 onwards, everything starts with
> `docker compose up`.

```bash
# 1. Start PostgreSQL
docker run -d --name minitb-postgres \
  -e POSTGRES_USER=minitb -e POSTGRES_PASSWORD=minitb -e POSTGRES_DB=minitb \
  -p 5432:5432 -v minitb-pgdata:/var/lib/postgresql \
  postgres:18

# 2. Run the app (Flyway creates the schema on first startup)
./mvnw spring-boot:run

# 3. Check that it's up
curl http://localhost:8080/actuator/health
```

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
