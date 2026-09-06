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

## CLI

- The official `summer` binary ships from `DropGuard/summer-cli` (Go, tag-driven releases via
  its `release.yml`) and is the primary entry point — the framework README's Quick Start is
  CLI-first: `summer create` scaffolds from the embedded archetype; `summer dev` / `summer
  build` drive the Maven toolchain (`pkg/runner/maven`).
- The Maven-level surface stays the source of truth for the build itself: `mvn summer:dev` and
  `mvn package` are what the CLI drives (one-time settings.xml pluginGroup). Scaffolding is the
  CLI/archetype path — there is no `summer:create-app` framework mojo.

## Publish runbook (Maven Central)

- **Milestone release**: Tag `v*` fires `.github/workflows/publish.yml` → `mvn -B clean deploy -P release -DskipTests`.
- All artifacts are automatically signed with GPG and published to Sonatype Central Portal (`central.sonatype.com`).
- Secrets required in GitHub repository:
  - `MAVEN_CENTRAL_USERNAME`: Sonatype Central Portal API user
  - `MAVEN_CENTRAL_TOKEN`: Sonatype Central Portal API token
  - `GPG_PRIVATE_KEY`: ASCII-armored PGP private key
  - `GPG_PASSPHRASE`: PGP key passphrase

## Current Work / Pending

- **Do NOT commit without explicit user permission.**
