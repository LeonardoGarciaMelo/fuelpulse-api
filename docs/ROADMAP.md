# Working notes

Internal build log. The public roadmap lives in the README; this file carries the
detail and the reasoning behind decisions.

**Purpose:** this is the resume point. Starting a fresh assistant session, share
this file plus the current source — it should be enough to pick up mid-stream.

**Status:** Phase 1 complete — 7 integration tests passing against a real PostGIS
container. Next: Phase 2 (authentication).

---

## Phase 1 — Proximity search ✅

One feature, built end to end, with the tests that prove it works. The goal was
never breadth: it was to establish the shape every later feature copies.

### What was built

| File | Role |
| :--- | :--- |
| `repository/GasStationRepository.java` | Native `ST_DWithin` query + interface projection |
| `service/NearbyStationService.java` | Radius validation against configuration |
| `controller/StationController.java` | `GET /api/v1/stations/nearby` with coordinate bounds |
| `dto/NearbyStationResponse.java` | Response type — entities are never serialized |
| `exception/ApiExceptionHandler.java` | RFC 7807 problem details, no internal leakage |
| `exception/RadiusExceededException.java` | Domain exception carrying the published limit |
| `config/GeoProperties.java` | `security.geo.*` bound and validated at startup |
| `config/SecurityConfig.java` | Deny-by-default chain, stateless, security headers |
| `db/migration/V2__phase1_indexes.sql` | Geography index + fuel price lookup index |
| `NearbyStationSearchIT.java` | 7 integration tests on real PostGIS |

### The geography cast — the thing that matters most here

`location` is `GEOMETRY(Point, 4326)`. In SRID 4326 **distance is measured in
degrees, not metres.** Calling `ST_DWithin` on the raw geometry with a radius of
5000 asks for everything within 5000 *degrees*: the entire table, returned with no
error, no warning, nothing in the log. The endpoint would look like it worked.

Casting both sides to `geography` switches the calculation to metres on the
spheroid. That is why the query reads:

```sql
ST_DWithin(
    gs.location::geography,
    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
    :radiusMeters
)
```

**This is also why `V2` exists.** The GiST index from `V1` is built on the
geometry column, and PostgreSQL will not use an index on `location` to answer a
predicate on `location::geography`. Without a matching functional index the search
falls back to a sequential scan — invisible on three seed rows, fatal on fifty
thousand stations. The geometry-to-geography cast is `IMMUTABLE`, so it can be
indexed:

```sql
CREATE INDEX idx_gas_stations_location_geog
    ON gas_stations USING GIST ((location::geography));
```

The test `reportsDistanceInMetres` exists specifically to catch a removed cast: it
asserts a real metre value, so the degrees version (~0.009) turns the test red
instead of silently corrupting the endpoint.

### Design decisions worth defending

**Deny by default.** `anyRequest().denyAll()` means a newly added controller is
unreachable until someone deliberately opens it. Forgetting a rule fails closed
rather than publishing an endpoint by accident.

**Radius is rejected, not clamped.** Returning results for a smaller radius than
the caller requested hides the fact that a limit exists. Refusing makes the
boundary explicit and tells the caller what the maximum is — a published API
constraint, not an internal detail.

**The cap is a security control, not a performance tweak.** An uncapped proximity
search is a full-table export endpoint with extra steps. That is why the limits
live under `security.geo.*` and are validated at startup.

**CSRF disabled, with the reason in the code.** CSRF defends against a browser
attaching ambient credentials — cookies — to a forged request. This API is
stateless and will carry its credential in an `Authorization` header, which a
cross-site form cannot set. If session cookies are ever introduced, it comes back.

**Validation is split deliberately.** Coordinate bounds sit on the controller,
where bean validation yields a clean 400 and documents itself in OpenAPI. The
radius check sits in the service, because the limit comes from configuration and
the rule should hold for any caller, not just HTTP.

**Errors say what the caller can fix and nothing about how the service is built.**
Validation failures name the offending parameter. Everything unrecognised becomes
a bare 500 with the detail going to the log. This complements
`server.error.include-message: never`, which only covers responses Spring itself
generates.

### The tests

1. `returnsStationsWithinRadius` — correct set, nearest first
2. `reportsDistanceInMetres` — the geography-cast regression test
3. `excludesStationsOutsideRadius` — the filter actually filters
4. `rejectsOutOfRangeLatitude` — 400 on latitude 91
5. `rejectsOversizedRadius` — 400 plus the published maximum
6. `errorResponsesCarryNoInternalDetail` — malformed input leaks nothing
7. `deniesUnmappedEndpointsByDefault` — deny-by-default holds

Fixtures are placed at known distances north of Praça da Sé. Note the constant:
a degree of latitude is **not** fixed — the meridian arc runs from about
110,574 m at the equator to 111,694 m at the poles. At São Paulo's latitude it is
roughly **110,760 m**. Using the equatorial figure puts the fixtures ~5 m off.

Integration tests use the `IT` suffix and run under Failsafe, so `mvn test` stays
fast and Docker-free while `mvn verify` runs everything.

---

## Phase 2 — Authentication & authorization

- [ ] Registration and login
- [ ] RS256 JWT via Spring Security's `JwtDecoder` / `JwtEncoder`, with a public
      JWKS endpoint. Not a hand-rolled JWT library: `JwtDecoder` validates
      signature, expiry, issuer and audience as one vetted unit and rejects
      `alg: none` by construction.
- [ ] Role-based access control (`USER`, `ADMIN`, `AUDITOR`)
- [ ] Account lockout after repeated failed logins
- [ ] Per-IP and per-token rate limiting
- [ ] Tighten the security headers (drop `unsafe-inline` once Swagger is disabled
      in production)

**Schema work this needs.** New columns on `users`, in a new migration
(`V3__auth_columns.sql` — never edit an applied migration, Flyway checksums them
and will refuse to run):

- `failed_login_attempts INT NOT NULL DEFAULT 0`
- `locked_until TIMESTAMPTZ`
- `token_version INT NOT NULL DEFAULT 0` — bumping it invalidates every
  outstanding token for that user, which is what makes revocation work without
  trusting the client

`UserPrincipal.from()` currently hardcodes `accountLocked = false`; it reads from
`locked_until` once the column exists.

---

## Phase 3 — Security testing

- [ ] **BOLA/IDOR test**: user A cannot modify user B's price report
      *(OWASP API Security Top 10 — API1, the most common real-world failure)*
- [ ] Unauthenticated → 401; wrong role → 403; flood → 429
- [ ] Token tamper tests: altered signature, expired token, `alg: none`

---

## Phase 4 — Supply chain & CI

- [ ] GitHub Actions running `mvn verify` on every push
- [ ] Secret scanning (gitleaks)
- [ ] CodeQL static analysis
- [ ] OWASP Dependency-Check failing at CVSS ≥ 7 (already configured, needs an
      `NVD_API_KEY` secret — required since v9, and the first sync is slow)
- [ ] Dockerfile (non-root, distroless) and Trivy image scan

---

## Phase 5 — Data integrity & documentation

- [ ] Append-only audit log enforced at the database level. Right now nothing
      enforces it — the entity has setters like any other. Options: revoke
      `UPDATE`/`DELETE` from the application's database role, a `BEFORE UPDATE`
      trigger that raises, or hash-chaining rows. Role-level revocation is the
      cheapest credible answer.
- [ ] Retention policy for IP addresses and usernames (LGPD/GDPR)
- [ ] Threat model specific to this application, not generic STRIDE
- [ ] `SECURITY.md` with a vulnerability disclosure policy

---

## Environment notes

Kept separate from the phases because these are traps, not milestones — and all
of them cost real debugging time.

**Spring Boot 4 split auto-configuration into per-technology modules.** The old
reflex — "the jar is on the classpath, so the feature is on" — no longer holds.
Three cases hit this project:

- `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`
  and needs `spring-boot-starter-webmvc-test`
- Flyway is **not** auto-configured by `flyway-core` alone; it needs
  `spring-boot-starter-flyway`. The tell is total silence — Flyway always prints
  its banner when wired, even with zero migrations.
- Health indicators, actuator pieces, and others follow the same rule

Diagnostic: search the startup condition report. If an auto-configuration appears
in neither *Positive* nor *Negative matches*, the class is not on the classpath and
a starter is missing.

**Testcontainers 2.0** renamed every module to a `testcontainers-` prefix and moved
container classes to `org.testcontainers.<module>`. `PostgreSQLContainer` now lives
in `org.testcontainers.postgresql`. Maven reports the old artifact names as a
missing *version*, which points at entirely the wrong problem. Versions are managed
by the Boot 4 BOM — do not pin them.

**Annotation processing is off by default since JDK 23.** Lombok silently stops
running and the compiler reports missing getters as if the source were wrong.
Fixed by declaring `annotationProcessorPaths` in the compiler plugin — note that
declaring it disables classpath discovery entirely, so every future processor must
be listed there too.

**Pinned to Java 25 LTS** rather than whatever the distro ships.

**Still unverified:** the `bucket4j` and `dependency-check-maven` versions in the
pom were carried over and may be stale. Check both before the first CI run.
Also: `bucket4j-core` is in-memory only, so the README must not claim distributed
rate limiting until the Redis module replaces it.
