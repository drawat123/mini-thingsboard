# Phase 1: Foundation (Spring Boot + PostgreSQL)

## Key ideas

### Schema: Flyway instead of `ddl-auto`

- **Flyway** runs versioned SQL files (`V1__create_tenant.sql`, `V2__...`) in order and records each one in the
  `flyway_schema_history` table, so each migration runs only once.
- The file name format is strict: capital `V`, a version number, **two** underscores, a description.
- **Never edit a migration after it has run.** Flyway stores a checksum of each file and refuses to start if an
  applied file changes. To change the schema, add a new `V5__...` file.
- Migrations can also change **data**: V4 filled in tokens for devices that already existed.
- `ddl-auto: validate`: Hibernate only *checks* that the entities match the tables, and the app fails at startup if
  they don't. You own the schema; Hibernate double-checks it.
- `ddl-auto: update` is fine for quick experiments, but don't use it for real: it can't rename columns, never drops
  anything, and leaves no record of what changed.

### Schema design choices

| Choice | Why |
|---|---|
| `UUID` primary keys | Any app instance can create an id without asking the DB (needed for Phase 5); ids can't be guessed |
| `created_time BIGINT` (epoch ms) | Same as ThingsBoard; no time-zone issues, easy to sort |
| Named constraints (`device_name_unq`) | Readable errors; the name can be mapped to an API message |
| `UNIQUE (tenant_id, name)` | Names are unique *per tenant*. The constraint also creates an index starting with `tenant_id`, which speeds up "list this tenant's X" |
| An explicit index on `device.device_profile_id` | Postgres does **not** index foreign keys automatically; without it, deleting a profile scans the whole `device` table |
| FK `NO ACTION` for profile → device | You can't delete a profile that devices still use (`CASCADE` would silently delete devices) |
| FK `ON DELETE CASCADE` for device → credentials | Credentials have no meaning without their device |
| `TEXT` vs `VARCHAR` | Same performance in Postgres; use `TEXT` when there's no meaningful length limit |
| Credentials in their own table | The token is looked up on every message and device details rarely; separating them makes the token easy to cache and replace |

### JPA entities

| Annotation | Meaning |
|---|---|
| `@Entity` / `@Table(name=...)` | This class maps to that table |
| `@MappedSuperclass` | No table of its own; its fields are copied into each subclass's table (`BaseEntity`) |
| `@Id` | Primary key |
| `@GeneratedValue(strategy = UUID)` | Hibernate generates the UUID just before INSERT. Leave the id `null` so Spring Data knows the entity is **new** |
| `@Column(nullable=false, updatable=false)` | Describes the column; `updatable=false` leaves it out of UPDATE statements |
| `@PrePersist` | A callback run just before INSERT (sets `createdTime`) |
| A `protected` no-arg constructor | Required by JPA (Hibernate creates an empty object, then fills it); `protected` stops your own code using it |

- **Entities reference each other by `UUID` fields, not `@ManyToOne`**, like ThingsBoard. Benefits: no hidden
  extra queries (N+1), entities convert to JSON cleanly for Redis, clear module boundaries. The cost: you look up
  related data yourself.
- **A missing setter is a rule.** `tenantId` has no setter, so a device can never change tenant.

### Lombok

- Use `@Getter`, `@Setter` **on individual fields**, and `@NoArgsConstructor(access = PROTECTED)`.
- **Never use `@Data` on entities:**
  1. `hashCode()` changes when the id is set by `save()` or a field changes, so objects get lost in a `HashSet`.
  2. `toString()` reads lazy relationships, which causes extra queries, a `LazyInitializationException`, or (with
     two-way relationships) a `StackOverflowError`.
  3. It generates setters for everything, which removes the rule "this field can't change".
- Avoid `@AllArgsConstructor` when two fields have the same type: reordering the fields silently swaps the
  arguments.

### Repositories, services, transactions

- `interface DeviceRepository extends JpaRepository<Device, UUID>`: Spring generates the implementation.
- **Derived queries:** the method name becomes SQL. `findByIdAndTenantId` → `WHERE id = ? AND tenant_id = ?`.
- `@Transactional`: the whole method is one DB transaction; any exception rolls everything back. Device and
  credentials are created together, or not at all.
- `@Transactional(readOnly = true)` for reads: a small speed-up, and it states the intent.
- **Dirty checking:** an entity loaded inside a transaction is *managed*. Change a field, and Hibernate UPDATEs it at
  commit; no `save()` call needed (see `regenerateCredentials`).
- Constructor injection with `final` fields; Spring passes in the dependencies.
- Watch for circular dependencies between services (A needs B, B needs A). We avoided one by keeping credentials
  in `DeviceService`.

### The REST layer

| Annotation | Meaning |
|---|---|
| `@RestController` | Return values are converted to JSON |
| `@RequestMapping("/api/devices")` | URL prefix for the class |
| `@GetMapping("/{id}")`, `@PostMapping` | Map an HTTP method and path to a Java method |
| `@PathVariable` / `@RequestBody` | Take a value from the URL / the JSON body |
| `@Valid` | Run the Bean Validation rules (`@NotBlank`, `@Size`, `@NotNull`) before the method runs |
| `@ResponseStatus(CREATED)` | Return 201 instead of 200 |
| `@RestControllerAdvice` + `@ExceptionHandler` | Global exception → HTTP response mapping |
| `ProblemDetail` | The standard JSON error format (RFC 9457) |

- **Request/response DTOs (records), never entities:** this blocks *mass assignment* (a client setting `tenantId`)
  and keeps internal fields from leaking out.
- `@Size(max = 255)` matches `VARCHAR(255)`, so an overlong value gets a clean 400 instead of a DB error.
- **Let the DB enforce uniqueness.** "Check if the name exists, then insert" has a race: two requests can both
  pass the check. Insert, and turn a constraint violation into 409 using the constraint name.

### Multi-tenancy and security

- **The tenant comes from the logged-in user, never from the URL.** For now: the `X-Tenant-Id` header read in
  `BaseController.getCurrentTenantId()`. Later: the security context, like ThingsBoard's `getTenantId()`.
- **Tenant isolation lives in the service layer**, so it also applies to non-HTTP callers (e.g. the Kafka
  consumer).
- Every tenant-scoped read uses `findByIdAndTenantId`; plain `findById` is never used for tenant data.
- **404, not 403**, for another tenant's resources: a 403 would confirm that the id exists.
- Tokens come from `SecureRandom` (unpredictable), never `Random` (predictable).

## Where it is in the code

| Idea | File |
|---|---|
| Migrations | [db/migration/](../../src/main/resources/db/migration/) |
| Base entity, `@PrePersist` | [BaseEntity.java](../../src/main/java/com/minitb/common/BaseEntity.java) |
| Entity with a read-only field | [Device.java](../../src/main/java/com/minitb/device/Device.java) |
| Derived queries | [DeviceRepository.java](../../src/main/java/com/minitb/device/DeviceRepository.java) |
| Transactions, tenant check, dirty checking | [DeviceService.java](../../src/main/java/com/minitb/device/DeviceService.java) |
| DTOs + validation | [DeviceDtos.java](../../src/main/java/com/minitb/device/DeviceDtos.java) |
| Tenant from the request | [BaseController.java](../../src/main/java/com/minitb/common/BaseController.java) |
| Constraint name → 409 | [ApiExceptionHandler.java](../../src/main/java/com/minitb/common/ApiExceptionHandler.java) |
| Token generation | [AccessTokenGenerator.java](../../src/main/java/com/minitb/device/AccessTokenGenerator.java) |

## Gotchas

- Editing an applied migration (even adding a comment) → Flyway checksum mismatch at startup.
- A `TEXT` column + a `String` field without `columnDefinition = "TEXT"` → `validate` fails (Hibernate expects
  `varchar`).
- A table without a primary key can't be mapped by JPA or referenced by a foreign key.
- `open-in-view: false` (our setting) means lazy data must be loaded inside the service, not in the controller.
- Spring Boot 4 renamed several starters (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`); older
  tutorials won't match.
- Flyway 10+ needs `flyway-database-postgresql` in addition to the starter.

## Self-test

<details><summary>1. Why must you never edit a migration that has already been applied? What do you do instead?</summary>

Flyway stores each applied file's checksum and fails at startup if it changes, and other environments would end up
with different schemas. Add a new migration (`V5__...`) instead.
</details>

<details><summary>2. Why UUID primary keys instead of auto-increment numbers?</summary>

Any app instance can generate an id without asking the DB (important with 3 replicas), and ids can't be guessed by
counting.
</details>

<details><summary>3. Does Postgres index foreign key columns automatically? Why does it matter for <code>device_profile_id</code>?</summary>

No. Without an index, "devices with profile X" queries and every profile delete (the FK check) scan the whole
`device` table.
</details>

<details><summary>4. Why does <code>UNIQUE (tenant_id, name)</code> make "list this tenant's devices" fast?</summary>

The unique constraint creates an index starting with `tenant_id`, so `WHERE tenant_id = ?` can use it.
</details>

<details><summary>5. Why leave the entity's id <code>null</code> before <code>save()</code>?</summary>

Spring Data uses a `null` id to decide the entity is new and runs an INSERT. With an id set, it assumes an existing
row and does a SELECT first (merge).
</details>

<details><summary>6. Give three reasons not to use Lombok's <code>@Data</code> on an entity.</summary>

`hashCode` changes after `save()` or a field change (lost in `HashSet`); `toString` triggers lazy loading or
recursion; setters for everything remove read-only rules like `tenantId`.
</details>

<details><summary>7. What is dirty checking? Where do we rely on it?</summary>

Hibernate snapshots managed entities and UPDATEs changed fields at commit. `regenerateCredentials` changes the token
without calling `save()`.
</details>

<details><summary>8. Why accept a <code>CreateDeviceRequest</code> record instead of the <code>Device</code> entity?</summary>

Mass assignment: a client could set `tenantId`/`id`. A DTO contains only the fields a client may send, and the
response DTO controls what goes out.
</details>

<details><summary>9. Why not check "does this name exist?" before inserting?</summary>

Race condition: two requests can both pass the check and both insert. The unique constraint is the only reliable
check; we map its violation to 409.
</details>

<details><summary>10. Why must the tenant come from the logged-in user and not the URL?</summary>

A URL value is chosen by the client and can be changed to another tenant's id. The authenticated user's tenant
can't be faked.
</details>

<details><summary>11. Why do tenant checks belong in the service, not the controller?</summary>

It's a business rule; non-HTTP callers (the Kafka consumer, scheduled jobs) go through services too.
</details>

<details><summary>12. Why return 404 instead of 403 for another tenant's device?</summary>

A 403 confirms the id exists, which leaks information. A 404 reveals nothing.
</details>

<details><summary>13. Why are credentials in a separate table with a unique token?</summary>

Token lookup happens on every message, device details are read rarely. Separating them allows caching and token
replacement. Uniqueness keeps devices apart, and its index makes the lookup instant.
</details>

<details><summary>14. Why <code>SecureRandom</code> and not <code>Random</code> for tokens?</summary>

`Random` is predictable from a few outputs; `SecureRandom` uses the OS's cryptographic randomness.
</details>

<details><summary>15. Why entities with <code>UUID tenantId</code> instead of <code>@ManyToOne Tenant tenant</code>?</summary>

No hidden extra queries (N+1), clean JSON for Redis caching, no lazy-loading surprises, clear module boundaries.
The same approach as ThingsBoard.
</details>
