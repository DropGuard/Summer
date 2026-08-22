# Summer Issue Tracker

A lightweight issue/defect tracker (mini Jira) built **as an external demo project**
on top of the [Summer](https://github.com/summer-framework) framework. It is intentionally
**not** a test of Summer internals — it consumes Summer purely as published artifacts and
owns its own business tests. The demo's purpose is to exercise real product requirements
(issue tracking, RBAC, audit trail, dynamic filtering) so that framework limitations
surface naturally, the way a real third-party user would hit them.

## Tech stack

- **Backend**: Summer framework (`summer-core`, `summer-web` + `summer-web-netty`,
  `summer-boot`, `summer-aop`, `summer-tx`, `summer-data-jdbc`), PostgreSQL, HikariCP,
  Jackson, JJWT, SLF4J + Logback.
- **Frontend**: React 19, React Router 7, TanStack Query 5, Zustand, Tailwind CSS 4, Vite 8.
- **Tests**: JUnit 5, Mockito, Testcontainers (real Postgres). The demo tests its **own**
  behavior, not Summer's capabilities.

## Build & run

```bash
# 1. Start PostgreSQL (auto-runs docker/init/01-schema.sql)
make db-start

# 2. Run the backend (RUNTIME engine, real DB)
make backend
# or: mvn compile && java -cp "target/classes:target/generated-sources/aot:$(mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt && cat /tmp/cp.txt)" summer.issuetracker.Application

# 3. In another terminal, run the frontend (proxies /api -> localhost:8080)
make frontend
# open http://localhost:5173
```

## Run the tests

```bash
make test          # mvn test — ITs spin up a real Postgres via Testcontainers
```

Unit tests (`*ServiceImplTest`) mock collaborators and assert the demo's pure logic.
Integration tests (`*IT`) start a real Netty server + real Postgres and assert business
behavior over HTTP.

## What the demo covers

- **Auth**: JWT login/register; each registration provisions its own org + ADMIN.
- **Projects**: create, list my projects, manage members (MANAGER/MEMBER/VIEWER).
- **Issues**: create, dynamic filter (status / priority / assignee / title / tag),
  status & priority change, assign, delete.
- **Associations modeled in the service layer**: tags (many-to-many via join table) and
  comments (nested stream) are assembled by the service because Summer's `@RowModel`
  only maps flat JDBC columns.
- **Audit trail**: every issue mutation writes an `audit_log` row **inside the same
  `@Transactional` boundary** as the mutation, so a rollback undoes both.
- **RBAC**: org-level role (ADMIN/MANAGER/MEMBER/GUEST) + project-level membership,
  enforced inline in the service. Tenant isolation: an admin is all-powerful only within
  their own org.

## What this demo taught us about Summer (and a framework change it drove)

The first cut of this demo followed a Gin-style habit: the authenticated user id was
**threaded through every service signature** (`createIssue(long actorId, ...)`)
and RBAC was **inlined** at the top of each method. It worked, but it was ugly
and it hid a real Summer gap:

> **Summer's request-scoped identity channel is the request attribute, not a static
> context holder.** The authenticated user id lives on the `HttpContext` request
> attribute (`RequestAttributes.USER_ID`), set by an `AuthMiddleware`. A service
> never sees the `HttpContext`, so coarse-grained, route-level RBAC has to be a
> middleware — not an interceptor on the service.

### Framework shape (`summer-web`): `AuthMiddleware` + request attribute

- `summer.web.AuthMiddleware` — an interface whose `authenticate(HttpContext)`
  resolves the user id from the request (e.g. a bearer token) and returns it.
  The framework's `apply` then stores it as the `RequestAttributes.USER_ID`
  request attribute — the single channel handlers and middleware read it from.
- `summer.web.RequestAttributes.USER_ID` — the attribute key.
- Middleware composes `handler = m.apply(handler)` in list order; the **last**
  list entry runs **first**.

### Demo side: coarse-grained gate as middleware, fine-grained rule in the service

- `security/JwtAuthMiddleware.java` — implements `AuthMiddleware`, resolves the
  user id from the JWT bearer token; public routes (register/login/health) return
  `null` so no attribute is set.
- `security/RbacMiddleware.java` — `@GlobalMiddleware @Order(1)` — the
  **coarse-grained** RBAC gate (tenant isolation + project membership /
  manager-or-lead) enforced at the HTTP layer before any handler runs. `@Order(2)`
  on auth means auth populates the attribute before this gate reads it.
- `security/Actors.java` — reads the user id from the request attribute
  (`RequestAttributes.USER_ID`) and centralizes the "authentication required"
  check. Controllers call `Actors.require(ctx)` and pass the `actorId` into the
  service methods.
- `IssueService` methods **do** take an explicit `actorId` parameter. The
  **fine-grained** rule ("a plain member may only mutate issues they reported or
  are assigned to") and the audit trail's actor live in the service, which holds
  the target resource at hand.

This is the corrected design and the proof that Summer's `AuthMiddleware` +
`@GlobalMiddleware` chain can do request-aware, route-level authorization.

### One real API constraint that remains

- **`QueryBuilder`/`Criteria` express single-entity columns only.** There is no
  sub-query or join construct, so many-to-many filters (e.g. "issues with tag
  X") cannot be expressed in the criteria API. The demo handles the
  single-entity dimensions (status / priority / assignee / title) with
  `QueryTemplate`/`QueryBuilder` and resolves the tag dimension with a `WHERE
  EXISTS` sub-query via `QueryBuilder.exists(...)` (which never multiplies root
  rows, keeping `count()` and pagination correct). Arbitrary join trees (outer
  joins, nested ON groups) are out of scope; express those as hand-written SQL
  through `JdbcTemplate`.

### Test boundary (intentional)

The demo tests **its own** behavior with JUnit + Mockito + Testcontainers, using
only the **public** `summer.test` API (`Testing.buildForTest`, `@Replaces`,
Testcontainers Postgres). It does **not** depend on `summer.test.internal`
(dual-engine `@DualEngine` / negative-fixture machinery) — that is the framework
TCK's job, not this external demo's. The dual-engine consistency of
`@Transactional` + auth under AOT is therefore exercised by Summer's own tck,
not here.

## Project layout

```
summer-issue-tracker/
├── pom.xml                     # external project: BOM + explicit jandex/AOT plugins
├── docker/
│   ├── docker-compose.yml      # Postgres for local dev
│   └── init/01-schema.sql      # schema (single source of truth)
├── src/main/java/summer/issuetracker/
│   ├── Application.java        # entry point
│   ├── config/                 # DataSource, test DB swap
│   ├── common/                 # IdGenerator, BusinessException, ErrorResponse
│   ├── security/               # Auth, JWT, RBAC, middleware
│   ├── org/ user/ project/ issue/ comment/ tag/ audit/   # entities + repos + services + controllers
│   └── web/                    # GlobalErrorHandler
├── src/test/...                # demo's own unit + integration tests
└── frontend/                   # React SPA
```
