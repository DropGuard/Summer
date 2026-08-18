# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Project knowledge** (module structure, layer access rules, conventions, anti-patterns, API surface,
test infrastructure, DI engine semantics, containerization): see **AGENTS.md** — it is the single
knowledge base. This file holds only the Claude-session workflow (build commands, local tooling,
decisions, pending work).

## Build Commands

Canonical command list lives in **AGENTS.md → COMMANDS** (one source of truth); the mandatory
`summer-parent` build contract is documented there too.

## Local Workflow Iteration (act)

- Local-only act runner image: `.github/act/Dockerfile` (header holds the build/run commands).
- **Never pass `--bind`**: copy mode keeps the container's writes in its own volume; a bind run
  writes root-owned files into the repo's `target/` dirs and breaks local builds.
- The act container's central egress is Cloudflare-blocked (it does not share the host's proxy) —
  the image bakes an aliyun mirror into its global Maven settings.
- act catches real fresh-repo CI bugs (the GitHub runner fails identically): run it before pushing
  workflow/publish changes.

## CLI decision (2026-08-08)

- **No standalone CLI in 0.x.** The command surface already exists as Maven goals:
  `mvn summer:create-app` / `mvn summer:dev` / `mvn package` (with the one-time settings.xml
  pluginGroup). A standalone CLI would wrap Maven (the build + dev are Maven-bound), adding only
  ergonomics — plus a second distribution channel (a runnable artifact + installer + versioning,
  parallel to the Maven repo).
- Revisit when: (a) tool-agnosticism arrives (Gradle support — the CLI becomes a cross-tool entry
  point), or (b) a real demand signal.

## Publish runbook (Maven Central)

- **Milestone release**: Tag `v*` fires `.github/workflows/publish.yml` → `mvn -B clean deploy -P release -DskipTests`.
- All artifacts are automatically signed with GPG and published to Sonatype Central Portal (`central.sonatype.com`).
- Secrets required in GitHub repository:
  - `MAVEN_CENTRAL_USERNAME`: Sonatype Central Portal API user
  - `MAVEN_CENTRAL_TOKEN`: Sonatype Central Portal API token
  - `GPG_PRIVATE_KEY`: ASCII-armored PGP private key
  - `GPG_PASSPHRASE`: PGP key passphrase

## Current Work / Pending

- **Audit complete (2026-08-07)** — the SPI refactor + two audit rounds (§11-§14, previously tracked in the deleted `CODE-AUDIT.md`) are fully resolved; durable decisions live in code comments/javadoc.
- **Do NOT commit without explicit user permission.**
- Done (2026-08-08, verification-granularity round): the `*IT` tests were silently skipped for a
  long time (a bare failsafe declaration never binds its goals) — failsafe is now bound in
  summer-parent's active plugins + a CI step fails if the IT-bearing modules run 0 tests.
  `@TestResource` gained the Quarkus lifecycle (init/inject/order + initArgs); the dotted-key
  override contract is enforced by `TestResourceContractTest` (the RedisTestResource's env-style
  key never matched — a latent bug the silent-skip hid). Whole-universe-invisible fixtures (the
  narrow-seeded sad-path beans + the narrow-only positive configs) live in
  `summer-tck-invisible-fixtures` — no jandex.idx, so the jar carries the .class bytes but the
  whole-universe index never sees them (the Quarkus Arc model: the boundary is the archive's
  absence from the indexed path, not an exclude list). Dual-engine real-stack coverage:
  `RealPostgresAotIntegrationIT` (aot-engine, real PG × both engines) + `RedisIntegrationIT`
  (@DualEngine). Moved to the tck by semantics: `RedisPropertiesDualEngineTest`,
  `WebSocketBroadcasterTest`, `WebSocketInterceptorIntegrationTest`, `RowModelMetadataNarrowDualEngineTest`.
