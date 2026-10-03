# Phase 3: Caching with Redis

## Key ideas

### What Redis is

- A database that keeps all data **in memory**: a giant shared `HashMap` running as its own server.
- Reads take well under a millisecond, versus a Postgres query that parses SQL, plans, may read from disk and
  serializes rows.
- **Key-value:** look up by key (`deviceCredentials::<token>`); no SQL, no joins.
- **TTL:** each key can expire automatically.
- **Shared:** every app instance talks to the same Redis, so they all see the same data.
- A single-threaded core, optional persistence, and more data types (lists, sets, pub/sub) that we don't need yet.

### Why an IoT platform caches

- Every telemetry message starts with "which device owns this token?", e.g. 10,000 identical-shaped queries per
  second.
- That data almost never changes. Reading it from Postgres every time wastes database capacity that should go to
  *storing* telemetry.

### Cache-aside

```
lookup:    cache hit → return  |  miss → query DB → store in cache → return
on change: evict the old entry from the cache
```

Reading is easy. **Keeping the cache correct (eviction) is the hard part.**

### In-app cache vs shared cache

- An in-app cache (Caffeine, `ConcurrentHashMap`) is faster and simpler, but **each instance has its own copy**.
- With 3 instances: a token is revoked on app 1, which evicts only its own copy. Apps 2 and 3 keep accepting the
  stolen token until the TTL expires. **This is a security hole, not just stale data.**
- With Redis there is exactly one copy: evict once, and every instance sees the change.
- ThingsBoard: Caffeine for a single node, Redis for a cluster (chosen in config).

### Design decisions

| # | Decision | Why |
|---|---|---|
| 1 | Evict **after commit** | Evicting before lets another instance re-read the old row and cache it again |
| 2 | Key = **token**; evict the **old** token | The old token is what an attacker would use. Read it *before* overwriting it |
| 3 | Value = `DeviceIdentity(deviceId, tenantId)` | Both fields can never change, so only a token change makes an entry stale. A record serializes to JSON cleanly |
| 4 | TTL anyway (5 min credentials, 30 min profiles) | A safety net if an eviction is missed; it limits the race eviction can't close |
| 5 | Don't cache unknown tokens | Random tokens all differ, so caching misses wouldn't help. The defences are the unique index (cheap miss) and rate limiting (Phase 4/5) |
| 6 | Redis down → **fall back to Postgres** | The cache is an optimization; Postgres is the source of truth. Failing closed would make a cache outage a platform outage |
| 7 | Profile key = `tenantId:profileId` | A key of only `profileId` would serve one tenant's cached profile to another tenant |

### Spring cache abstraction

| Piece | Meaning |
|---|---|
| `@EnableCaching` | Turns the annotations on. **Without it, every cache annotation is silently ignored** |
| `@Cacheable(cacheNames, key, unless)` | Cache-aside in one annotation: on a hit, the method body never runs |
| `key = "#accessToken"` | SpEL; `#name` = a method parameter |
| `unless = "#result == null"` | Don't cache empty results; with `Optional`, `#result` is the value inside it |
| `RedisCacheManagerBuilderCustomizer` | Per-cache configuration: TTL, serializer, null handling |
| `JacksonJsonRedisSerializer<>(Type.class)` | JSON of one known type: readable, and no polymorphic type info (a security risk) |
| `CachingConfigurer.errorHandler()` + `LoggingCacheErrorHandler` | Cache errors are logged and treated as a miss |
| `@TransactionalEventListener` | Runs the listener only after the publishing transaction commits; skipped on rollback |
| `ApplicationEventPublisher` | Publishes an event that listeners receive |

- **Why an event instead of `@CacheEvict`:** `@CacheEvict` only sees the method's parameters (`deviceId`); the key
  to evict (the old token) only exists inside the method. This mirrors ThingsBoard's `DeviceCredentialsEvictEvent`.
- **Self-invocation:** caching works through a proxy, so `this.cachedMethod()` from inside the same class skips
  the cache.

### JPQL `@Query`

```java
SELECT new com.minitb.device.DeviceIdentity(d.id, d.tenantId)
FROM DeviceCredentials c JOIN Device d ON d.id = c.deviceId WHERE c.accessToken = :accessToken
```

- JPQL uses **entity and field names**, not table and column names.
- `SELECT new ...` (a constructor expression) builds a record directly from one query.
- `JOIN ... ON` joins entities that have no JPA relationship.
- Spring Data checks `@Query` syntax at startup, so a typo fails fast.

### Redis configuration

| Setting | Why |
|---|---|
| `spring.data.redis.timeout: 500ms` | **The default is 60 s**: a dead Redis would hang every request for a minute |
| `management.health.redis.enabled: false` | An optional cache must not mark the app DOWN (in Phase 5, Nginx would drop every instance) |
| `--save "" --appendonly no` | A cache needs no persistence; it refills from Postgres |
| `--maxmemory 256mb --maxmemory-policy allkeys-lru` | When full, delete the least-recently-used keys instead of rejecting writes |
| `redis-cli ping` health check | So the app waits for Redis at startup |

### The race eviction can't close

1. App 2 misses the cache and reads the old token from Postgres.
2. App 1 commits the new token and evicts.
3. App 2 writes the old value back into the cache.

The stolen token then works until the TTL expires, which is why credentials get a short TTL. The full fix is
**versioned cache entries** (a write is rejected if its version is older than the stored one), as newer
ThingsBoard versions do.

## Where it is in the code

| Idea | File |
|---|---|
| Cache setup, TTLs, serialization, error handler | [CacheConfig.java](../../src/main/java/com/minitb/common/CacheConfig.java) |
| Cached token lookup, publishing the event | [DeviceService.java](../../src/main/java/com/minitb/device/DeviceService.java) |
| JPQL constructor expression | [DeviceCredentialsRepository.java](../../src/main/java/com/minitb/device/DeviceCredentialsRepository.java) |
| Eviction after commit | [DeviceCredentialsCacheEvictor.java](../../src/main/java/com/minitb/device/DeviceCredentialsCacheEvictor.java) |
| Tenant-scoped profile key | [DeviceProfileService.java](../../src/main/java/com/minitb/deviceprofile/DeviceProfileService.java) |
| Proof of the behaviour | [DeviceCredentialsCacheTests.java](../../src/test/java/com/minitb/device/DeviceCredentialsCacheTests.java) |
| Redis settings | [application.yml](../../src/main/resources/application.yml) |
| Redis service | [docker-compose.yml](../../docker-compose.yml) |

## Commands cheat sheet

```bash
docker compose up -d postgres redis            # infrastructure only (for ./mvnw or tests)
docker compose exec redis redis-cli            # interactive Redis shell
docker compose exec redis redis-cli --scan     # list keys (prefer to KEYS * on big datasets)
docker compose exec redis redis-cli GET 'deviceProfiles::<tenantId>:<profileId>'
docker compose exec redis redis-cli TTL '<key>'   # seconds until it expires
docker compose exec redis redis-cli FLUSHALL   # empty the cache (safe: it refills from Postgres)
docker compose stop redis                      # simulate a Redis outage
```

## Gotchas

- Forgetting `@EnableCaching` → no error, no caching.
- A key without the tenant → a cross-tenant data leak through the cache.
- `@EventListener` instead of `@TransactionalEventListener` → eviction before commit, so revocation can silently
  fail.
- Reading the old token *after* `setAccessToken` → evicting the wrong key.
- The default Redis timeout of 60 s → a Redis outage hangs every request.
- Redis in the app's health check → a cache outage takes the whole cluster offline.
- Testing eviction in a `@Transactional` test → no real commit, so the after-commit listener never runs.
- **Debug agent in the Dockerfile** → it ships to production. Put it in `JAVA_TOOL_OPTIONS` in Compose instead.
  Having it in both places → "Cannot load this JVM TI agent twice".
- `.editorconfig` only applies to its own folder and below (it goes in the repo root), and VS Code needs the
  EditorConfig extension to read it.
- A formatter can split `//` comments but never join them back.

## Self-test

<details><summary>1. Why not cache credentials in a <code>ConcurrentHashMap</code> inside each app?</summary>

With several instances, revoking a token evicts only one instance's copy; the others keep accepting the stolen
token until their TTL. A shared Redis has a single copy: evict once, and everyone sees it.
</details>

<details><summary>2. Should eviction happen before or after the transaction commits? What goes wrong otherwise?</summary>

After. If you evict before, another instance can miss the cache in the gap, read the still-committed old token
from Postgres, and cache it again.
</details>

<details><summary>3. When a token is regenerated, which key is evicted, and how does the code know it?</summary>

The old token's key. The code reads `credentials.getAccessToken()` before overwriting it and passes it in the
event.
</details>

<details><summary>4. Why cache <code>DeviceIdentity(deviceId, tenantId)</code> and not just <code>deviceId</code>, or the entity?</summary>

Telemetry needs the tenant too. Both fields can never change, so only a token change makes an entry stale. A small
record serializes to JSON cleanly; an entity brings Hibernate state with it.
</details>

<details><summary>5. If eviction works, why set a TTL?</summary>

A safety net for missed evictions (e.g. Redis down during the evict), and it limits the race eviction can't close.
</details>

<details><summary>6. An attacker sends random tokens. Does caching help? What does?</summary>

No: every random token is different, so each one misses anyway. The unique index makes each miss cheap; rate
limiting per IP and per tenant stops the flood.
</details>

<details><summary>7. Redis goes down. Fail closed or fall back to Postgres? Why?</summary>

Fall back. Redis is an optimization; Postgres is the source of truth and must handle the load anyway. Failing
closed turns a cache outage into a platform outage.
</details>

<details><summary>8. What happens if the profile cache key is only <code>#profileId</code>?</summary>

Tenant A loads the profile, filling the cache. Tenant B asks for the same id and gets it from the cache, which
skips the tenant check in the query. A cross-tenant leak.
</details>

<details><summary>9. Why <code>spring.data.redis.timeout: 500ms</code>?</summary>

The default is 60 s. When Redis dies, every request would hang for a minute before falling back.
</details>

<details><summary>10. Why disable the Redis health indicator?</summary>

The app works without Redis. With it enabled, a Redis outage marks every instance DOWN, and the load balancer
removes them all.
</details>

<details><summary>11. Why does the Redis container have no volume or persistence?</summary>

It's a cache: if it restarts, it refills from Postgres. It needs a memory limit with an LRU eviction policy
instead.
</details>

<details><summary>12. Why an event + <code>@TransactionalEventListener</code> instead of <code>@CacheEvict</code>?</summary>

`@CacheEvict` only sees the method's parameters; the old token exists only inside the method. The listener also
runs only after commit, and not at all on rollback.
</details>

<details><summary>13. You added <code>@Cacheable</code>, but every call still hits the database. Name two likely causes.</summary>

`@EnableCaching` is missing, or the method is called from inside the same class (self-invocation skips the proxy).
</details>

<details><summary>14. Why must the cache test not be <code>@Transactional</code>?</summary>

The test transaction would roll back instead of committing, so the after-commit eviction never runs.
</details>

<details><summary>15. Describe the race that eviction can't close, and the real fix.</summary>

A reader loads the old token, the writer commits and evicts, then the reader writes the old value back into the
cache. A TTL limits how long it lasts; versioned cache entries fix it.
</details>
