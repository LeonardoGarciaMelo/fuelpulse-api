# FuelPulse API

**A multi-brand gas station and fuel price API, built with a security-first approach.**

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)
![PostGIS](https://img.shields.io/badge/PostGIS-16--3.4-blue.svg)
![Status](https://img.shields.io/badge/status-work%20in%20progress-yellow.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

> **Status: early development.** This README documents what is *implemented*, not
> what is planned. Planned work lives in [Roadmap](#roadmap) and is clearly marked
> as such. If a feature is not checked off, it does not exist yet.

---

## Background

During the first year of my degree, my class took part in an industry challenge
sponsored by the energy company Raízen. The brief was to build an application with
an interactive map that helps drivers find nearby gas stations.

At the time I had almost no programming experience, and the team shipped a rough
prototype built with visual block-based programming. It worked in the narrowest
possible sense and not much more.

Three years later I am rebuilding it properly. The goal is not to reproduce the
original submission but to use a problem I already understand as a vehicle for the
engineering I care about: spatial data, API security, and the kind of testing that
proves a control actually works.

This version is **brand-neutral** by design. It models gas stations from any
operator rather than a single chain.

> This project is an independent academic exercise. It is **not affiliated with,
> endorsed by, or sponsored by** Raízen, Shell, or any fuel retailer. No proprietary
> material from the original challenge is reproduced here.

---

## Current state

**Implemented**

- Project skeleton (Spring Boot 3.3, Java 21)
- Domain model: `User`, `GasStation`, `FuelPrice`, `AuditLog`
- Flyway schema migration with PostGIS `GEOMETRY(Point, 4326)` and a GiST spatial index
- Local infrastructure via Docker Compose (PostGIS + Redis)
- Configuration hardening: no default secrets, error detail suppressed outside dev

**Not implemented yet** — no endpoints, no authentication, no tests. See the roadmap.

---

## Roadmap

Nothing moves to ✅ without working code *and* a test covering it.

### Phase 1 — Proximity search
- [ ] `GET /api/v1/stations/nearby` — radius search via `ST_DWithin`
- [ ] Coordinate bounds validation (reject values outside WGS84 range)
- [ ] Enforced maximum radius and result cap, so the endpoint cannot be used to
      enumerate the full dataset
- [ ] Response DTOs — entities are never serialized directly

### Phase 2 — Authentication & authorization
- [ ] Registration and login
- [ ] RS256 (asymmetric) JWT with a public JWKS endpoint
- [ ] Role-based access control (`USER`, `ADMIN`, `AUDITOR`)
- [ ] Account lockout after repeated failed logins
- [ ] Per-IP and per-token rate limiting
- [ ] Security response headers (HSTS, CSP, `X-Content-Type-Options`, frame options)

### Phase 3 — Security testing
- [ ] Integration tests on a real PostGIS instance (Testcontainers)
- [ ] **BOLA/IDOR test**: user A cannot modify user B's price report
      *(OWASP API Security Top 10 — API1)*
- [ ] Unauthenticated request → `401`; wrong role → `403`; flood → `429`
- [ ] Tamper tests: altered signature, expired token, `alg: none`

### Phase 4 — Supply chain & CI
- [ ] GitHub Actions running build and test on every push
- [ ] Secret scanning (gitleaks)
- [ ] CodeQL static analysis
- [ ] OWASP Dependency-Check failing the build at CVSS ≥ 7
- [ ] Dockerfile (non-root, distroless) and Trivy image scan

### Phase 5 — Data integrity & documentation
- [ ] Append-only audit log enforced at the database level, not by convention
- [ ] Data retention policy for IP addresses and usernames (LGPD/GDPR)
- [ ] Threat model specific to this application
- [ ] `SECURITY.md` with a vulnerability disclosure policy

---

## Stack

| Component | Technology |
| :--- | :--- |
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3, Spring Security 6, Spring Data JPA |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Cache | Redis 7 |
| Migrations | Flyway |
| API docs | OpenAPI 3 / Swagger UI |

---

## Running locally

### Prerequisites
Java 21, Maven 3.9+, Docker and Docker Compose.

### Setup

```bash
git clone https://github.com/<your-username>/fuelpulse-api.git
cd fuelpulse-api

# Configure environment
cp .env.example .env
# Then edit .env and set DB_PASSWORD and JWT_SECRET.
# Generate a secret with:  openssl rand -hex 32

# Start PostGIS and Redis
docker compose up -d

# Run
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

> The app deliberately **fails to start** if `JWT_SECRET` or `DB_PASSWORD` are
> missing. Committing a working default secret is how development keys end up in
> production.

---

## Design notes

**Schema is owned by Flyway, not Hibernate.** `ddl-auto` is set to `validate`, so
Hibernate verifies that the entities match the migrated schema and refuses to
start on drift. It never alters tables.

**Radius is capped.** An uncapped proximity search is a database export endpoint
with extra steps. The maximum radius and result count are configuration, and
requests exceeding them are rejected rather than silently clamped.

**Audit logging is a first-class table**, not a log file. Authentication outcomes,
authorization failures, and rate limit violations are structured records so they
can be queried during an investigation.

---

## License

MIT — see [LICENSE](LICENSE).
