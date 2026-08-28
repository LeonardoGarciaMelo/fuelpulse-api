# Working notes

Internal build log. The public roadmap lives in the README; this file carries the
detail and the reasoning behind decisions.

**Purpose:** this is the resume point. Starting a fresh assistant session, share
this file plus the current source — it should be enough to pick up mid-stream.

---

## Where things stand

**Last updated:** Phase 0 complete.

Phase 0 fixed two defects that prevented the project from building at all, plus
several configuration choices that contradicted the project's stated security goals.

| Fix | Detail |
| :--- | :--- |
| `../pom.xml` | `<n>` → `<name>`. Maven rejects unknown model elements; the build aborted. |
| `../src/main/resources/application.yml` | Removed `database-platform: ...PostgisDialect` — a Hibernate 5 class. On Hibernate 6, `PostgreSQLDialect` handles PostGIS when `hibernate-spatial` is present. |
| `../src/main/resources/application.yml` | Removed the hardcoded default `JWT_SECRET`. The app now fails fast if it is absent. |
| `../src/main/resources/application.yml` | `server.error.include-message` and `include-binding-errors` were `always` — information disclosure. Now `never`, relaxed only in the `dev` profile. |
| `../src/main/resources/application.yml` | Access token lifetime 24h → 15min. |
| `../src/main/resources/application.yml` | Added `open-in-view: false`. |
| `../src/main/resources/application.yml` | Added `security.geo.max-radius-meters` / `max-results` as a domain-level control. |
| `../docker-compose.yml` | Credentials moved to `.env`; ports bound to `127.0.0.1` instead of `0.0.0.0`. |
| `../pom.xml` | `dependency-check` now fails at CVSS ≥ 7 (was `failOnError: false` — decorative). |
| `../pom.xml` | Removed `spring-boot-devtools`; added Testcontainers. |
| `../README.md` | Rewritten to describe only what exists. Everything else moved to an explicit roadmap. |

---

## Open questions

- **Rate limiting scope.** `bucket4j-core` is in-memory, so limits are not shared
  across instances. Fine for single-instance development; must move to the
  distributed Bucket4j module before the README can claim distributed rate limiting.
- **`User implements UserDetails`.** Works, but couples the persistence entity to
  Spring Security and risks leaking `password_hash` if the entity is ever
  serialized. Consider a separate `UserPrincipal` adapter in Phase 2.
- **Audit log immutability.** Currently enforced by nothing. Options: revoke
  `UPDATE`/`DELETE` from the application's database role, a `BEFORE UPDATE` trigger
  that raises, or hash-chaining rows. Database-level revocation is the cheapest
  credible answer. Phase 5.

---

## Schema changes still needed

Phase 2 requires columns that do not exist yet, on `users`:

- `failed_login_attempts INT NOT NULL DEFAULT 0`
- `locked_until TIMESTAMPTZ`
- `token_version INT NOT NULL DEFAULT 0` — bumping it invalidates all outstanding
  tokens for that user, which is what makes revocation work without trusting the
  client

These go in a new migration (`V2__auth_columns.sql`). Never edit `V1`: Flyway
checksums applied migrations and will refuse to run against a modified one.

Also worth adding in Phase 1:

- `CREATE INDEX idx_fuel_prices_lookup ON fuel_prices (station_id, fuel_type, reported_at DESC);`
  The existing single-column index does not serve the actual query, which is
  "latest price per station per fuel type".

---

## Next up — Phase 1

Build one vertical slice end to end before starting anything else:

1. `GasStationRepository` with a `ST_DWithin` native query
2. `NearbyStationService` — validates coordinates, clamps or rejects the radius
3. `StationController` — `GET /api/v1/stations/nearby?lat=&lon=&radius=`
4. `NearbyStationResponse` DTO with distance in meters
5. `@RestControllerAdvice` returning RFC 7807 problem details, no internal detail
6. Testcontainers integration test with real seeded coordinates

One complete, tested feature is worth more than ten half-built ones.
