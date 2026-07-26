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

> **Summer had no request-scoped context holder.** There was no static accessor
> a service (or, crucially, a method-level AOP interceptor) could call to read
> "the current user". The user id only lived on the `HttpContext` request
> attribute, which a service never sees. So declarative, method-level authorization
> was impossible — RBAC had to be hand-written inside every service method.

That is exactly the kind of rough edge a third-party user hits. So instead of just
documenting it, we closed it in the framework:

### Framework change (`summer-web`): `RequestContextHolder`

- `summer.web.RequestContext` — a per-request view (holds the resolved user id).
- `summer.web.RequestContextHolder` — `static set / current / currentUserId / clear`.
- The framework's `AuthMiddleware.apply` now publishes the context the moment a
  request is authenticated.
- `NettyHttpServerHandler.processRequest` wraps `handler.handle(ctx)` in a
  `try/finally` that calls `RequestContextHolder.clear()`. Because each request
  runs on its **own virtual thread** (no pooling), the binding is released
  deterministically with no cross-request bleed.

This is safe under Summer's execution model and costs nothing for request paths
that don't need it.

### Demo side: declarative, AOP-based RBAC

With a request context available, the demo's RBAC moved out of the service
bodies and into an interceptor:

- `security/RequireRole.java` — an `@InterceptorBinding` marker (no members; the
  interceptor routes by method name, avoiding reflection on annotation values).
- `security/RbacInterceptor.java` — `@Interceptor @RequireRole`, reads the
  current user from `RequestContextHolder`, enforces the **coarse-grained** gate
  (tenant isolation + project membership / manager-or-lead) before the method runs.
- `IssueService` methods now carry **no `actorId` parameter**. The interceptor
  does the gate; the service reads the user only for the **fine-grained** rule
  ("a member may only mutate issues they reported or are assigned to") and for the
  audit trail's actor — all from `RequestContextHolder`.

This is the corrected design and the proof that Summer's AOP can now do
request-aware, method-level authorization.

### One real API constraint that remains

- **`QueryBuilder`/`Criteria` express single-entity columns only.** There is no
  sub-query or join construct, so many-to-many filters (e.g. "issues with tag
  X") cannot be expressed in the criteria API. The demo handles the
  single-entity dimensions (status / priority / assignee / title) with
  `QueryTemplate`/`QueryBuilder` and resolves the tag dimension with a separate
  `JdbcTemplate` IN-lookup in Java. Worth a `join(...)` / sub-query builder
  in `summer-data-jdbc` down the line.

### Test boundary (intentional)

The demo tests **its own** behavior with JUnit + Mockito + Testcontainers, using
only the **public** `summer.test` API (`Testing.buildForTest`, `@Replaces`,
Testcontainers Postgres). It does **not** depend on `summer.test.internal`
(dual-engine `@DualEngine` / negative-fixture machinery) — that is the framework
TCK's job, not this external demo's. The dual-engine consistency of
`@Transactional` + `@RequireRole` under AOT is therefore exercised by Summer's
own tck, not here.

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
