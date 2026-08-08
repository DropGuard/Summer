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

## Publish runbook (GitHub Packages)

- Tag `v*` fires `.github/workflows/publish.yml` → `mvn -B deploy -DskipTests` (all modules).
- gh token needs `read:packages` + `delete:packages` to list/clean partial deploys.
- **Known failure mode**: deploy is module-by-module; a mid-reactor deploy failure leaves a partial
  0.x set on the registry, and re-deploying the same version fails with 409 Conflict (versions are
  immutable). Recovery: delete the partial packages (`gh api -X DELETE /user/packages/maven/<name>`),
  re-tag, re-push. **Decision (2026-08-08): no deploy preflight** — the missing-distributionManagement
  class is fixed (root + summer-parent + summer-dependencies all declare it) and the recovery is
  documented + fast; a static check would guard a low-probability future regression at the cost of
  workflow complexity.

## Current Work / Pending

- **Audit complete (2026-08-07)** — the SPI refactor + two audit rounds (§11-§14, previously tracked in the deleted `CODE-AUDIT.md`) are fully resolved; durable decisions live in code comments/javadoc.
- **Do NOT commit without explicit user permission.**
- Pending: `PostgresTestResource` for demo ITs (only `RedisTestResource` exists today).
