# Phase 2: Docker and Docker Compose

## Key ideas

### Why Docker

- The problem it solves: "works on my machine". Without it, every developer installs the JDK, Postgres, Redis,
  Kafka and so on by hand, and each install can differ.
- Docker packages an app **with everything it needs** into an image that runs the same way everywhere.
- ThingsBoard ships official images (`thingsboard/tb-node`, `thingsboard/tb-postgres`) and Compose files for whole
  clusters. **The same image** runs in development, testing and production; only the configuration changes.

### Core concepts

| Concept | Meaning | Analogy |
|---|---|---|
| Image | A read-only package: base OS + runtime + app | A class |
| Container | A running instance of an image, isolated | An object |
| Dockerfile | The recipe for building an image | Source code |
| Layer | Each Dockerfile step is a cached layer; only the changed layers and the ones after them rebuild | Incremental build |
| Registry | Where images are stored (Docker Hub) | Maven Central |
| Volume | Storage that survives the container being deleted | An external drive |
| Network | A virtual LAN between containers | A private LAN |

- **Container vs VM:** a VM emulates a whole computer with its own kernel (heavy, minutes to boot). A container is
  an isolated process sharing the host's kernel (light, seconds to start). On a Mac, Docker Desktop runs one small
  Linux VM that all containers share.

### Networking: the key lesson

- **`localhost` inside a container means that container itself**, not your Mac and not other containers.
- Compose puts all services on one network and gives each service a **DNS name equal to its service name**. The app
  reaches Postgres at `postgres:5432`.
- `ports: "8080:8080"` (`host:container`) is only needed for access **from your Mac**. Containers talk to each
  other without it.

### Externalized configuration (one image, many environments)

- Spring Boot reads settings from several sources; **environment variables override `application.yml`**.
- `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/minitb}` → use the env var if it's set, otherwise the
  default.
- The defaults are for `./mvnw` on your Mac; Compose sets the env vars for containers. You build the image once and
  configure it per environment. ThingsBoard's `thingsboard.yml` uses the same pattern throughout.

### Dockerfile

| Instruction | Meaning |
|---|---|
| `FROM image AS name` | Start from a base image; name the stage |
| `WORKDIR /dir` | `cd`, creating the directory if needed |
| `COPY src dest` | Copy files from the build context into the image |
| `RUN cmd` | Run a command **while building**; the result becomes a layer |
| `RUN --mount=type=cache,target=/root/.m2` | Keep the Maven cache between builds |
| `COPY --from=build` | Copy a file from an earlier stage |
| `USER minitb` | Run as an ordinary user instead of root |
| `EXPOSE 8080` | Documentation only; doesn't publish the port |
| `ENTRYPOINT [...]` | The command run when the container starts |

- **Multi-stage build:** stage 1 (JDK + Maven + source) builds the JAR; stage 2 (JRE only) runs it. Build tools and
  source code never ship in the final image.
- `-DskipTests` in the image build: the tests need a database, which doesn't exist at build time. Tests run outside
  Docker.
- `-XX:MaxRAMPercentage=75`: the JVM detects the container's memory limit but uses only 25% of it for the heap by
  default.
- `.dockerignore` keeps `target/`, `.git/` and IDE files out of the build context (faster builds, no stale JARs).

### docker-compose.yml

| Key | Meaning |
|---|---|
| `services:` | One entry per service; the service name is also its hostname |
| `image:` / `build: .` | Use a ready-made image / build from the local Dockerfile |
| `environment:` | Env vars passed to the container (this is where `localhost` becomes `postgres`) |
| `ports:` | `host:container` port publishing |
| `volumes:` | Attach named volumes for data that must survive |
| `healthcheck:` | A command Docker runs to decide "healthy" vs merely "running" |
| `depends_on: condition: service_healthy` | Wait until a dependency is **healthy**, not just started |

- No `container_name`: in Phase 5 we run several replicas of `app`, and a fixed name would prevent it.
- `volumes: pgdata: name: minitb-pgdata` reuses an existing named volume, so the data survived the move to Compose.

## Where it is in the code

| Idea | File |
|---|---|
| Env var placeholders | [application.yml](../../src/main/resources/application.yml) |
| Multi-stage image | [Dockerfile](../../Dockerfile) |
| Build context exclusions | [.dockerignore](../../.dockerignore) |
| Services, network, health checks | [docker-compose.yml](../../docker-compose.yml) |

## Commands cheat sheet

```bash
docker compose up --build -d     # build the image(s) and start everything in the background
docker compose ps                # status, including (healthy)
docker compose logs -f app       # follow the logs of one service
docker compose exec app sh       # open a shell inside a running container
docker compose exec postgres psql -U minitb -d minitb   # psql inside the Postgres container
docker compose down              # stop + remove containers (volumes are kept)
docker compose down -v           # ...and DELETE the volumes (wipes the DB)
docker compose up -d postgres    # start only Postgres (to run the app with ./mvnw)

docker ps -a                     # all containers
docker images                    # local images
docker volume ls                 # volumes
```

## Gotchas

- `localhost` in a container's config → "connection refused". Use the service name.
- `depends_on` without `condition: service_healthy` only waits for the container to *start*; Postgres needs a few
  more seconds before it accepts connections.
- **Postgres 18 changed its data path:** mount the volume at `/var/lib/postgresql`, not `/var/lib/postgresql/data`
  (the path for 17 and older).
- **Never point two Postgres containers at the same volume:** it corrupts the data.
- Port already in use (`5432`/`8080`) → something else (an old container, or the app running locally) holds it.
  Find it with `lsof -i :8080`.
- `docker compose down -v` deletes your data. Plain `down` doesn't.
- After changing code, use `up --build`; without `--build`, Compose reuses the old image.

## Self-test

<details><summary>1. What's the difference between an image and a container?</summary>

An image is a read-only package (like a class); a container is a running instance of it (like an object). Many
containers can run from one image.
</details>

<details><summary>2. How is a container different from a VM?</summary>

A VM emulates a whole computer with its own kernel. A container is an isolated process sharing the host's kernel,
which makes it much lighter and faster to start.
</details>

<details><summary>3. Inside the app container, why doesn't <code>localhost:5432</code> reach Postgres? What works instead?</summary>

Each container has its own network stack, so `localhost` is the app container itself. Use the Compose service name
`postgres`, which Compose's DNS resolves to the Postgres container.
</details>

<details><summary>4. How does one JAR/image connect to <code>localhost</code> on your Mac and to <code>postgres</code> in Docker?</summary>

`application.yml` uses `${SPRING_DATASOURCE_URL:localhost-default}`. Env vars override the file, and Compose sets
the env var.
</details>

<details><summary>5. Why a multi-stage Dockerfile?</summary>

Building needs the JDK, Maven and the source; running needs only a JRE and the JAR. The final image is smaller and
contains no build tools or source code.
</details>

<details><summary>6. Why <code>-DskipTests</code> in the image build?</summary>

The tests need a running Postgres, which isn't available during `docker build`. Tests run outside Docker.
</details>

<details><summary>7. What does <code>EXPOSE</code> do? What actually makes the app reachable from your Mac?</summary>

`EXPOSE` is only documentation. `ports: "8080:8080"` in Compose (or `-p` with `docker run`) publishes the port.
</details>

<details><summary>8. Plain <code>depends_on</code> vs <code>condition: service_healthy</code>?</summary>

Plain waits only until the container starts. `service_healthy` waits until its health check passes (`pg_isready`),
when Postgres actually accepts connections.
</details>

<details><summary>9. Why run the app as a non-root user?</summary>

If someone exploits the app, they don't get root inside the container. It limits the damage.
</details>

<details><summary>10. Why no <code>container_name</code> on the app service?</summary>

Container names must be unique, so a fixed name would block running several replicas in Phase 5.
</details>

<details><summary>11. <code>docker compose down</code> vs <code>docker compose down -v</code>?</summary>

Both remove the containers and the network; `-v` also deletes the volumes, which wipes the database.
</details>

<details><summary>12. Why <code>-XX:MaxRAMPercentage=75</code>?</summary>

The JVM uses only 25% of the container's memory limit for the heap by default; 75% is a common setting when the
container runs just one app.
</details>
