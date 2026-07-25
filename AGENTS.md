# Ticket Booking Agent Guide

## Scope and Sources of Truth

This file contains durable repository-specific rules. Use the personal `project-change-analysis` skill for the common change-analysis workflow and the Wiki protocol at:

```text
/Users/heojungseok/Library/Mobile Documents/com~apple~CloudDocs/wiki/harness/project-change-analysis-review-protocol.md
```

Use `README.md` for maintained local build, run, health, E2E, recovery, persistence, shutdown, and CI procedures. Put one-off plans, review packets, and execution logs in the Personal OS Wiki, not this repository.

## Repository Facts

- Java 21, Gradle Groovy DSL, Spring Boot 3.3.x
- Modules: `api-gateway`, `user-service`, `performance-service`, `booking-service`, `notification-service`, `queue-service`
- `docker-compose.yml` contains infrastructure.
- `docker-compose.app.yml` adds the six application containers.
- `.github/workflows/build-images.yml` invokes the Gradle build and test tasks, validates Compose, and builds six images.
- At baseline `main@23d3e9f`, no tracked `src/test` files exist and every Gradle test task is `NO-SOURCE`; this provides no test coverage evidence.

Inspect the current revision before relying on any of these facts. If code and this file differ, treat the discrepancy as a finding and update the durable rule only after verification.

## Repository Artifact Placement

Do not create `docs/superpowers/`, agent-only checklists, or temporary review documents in this repository. Add durable operator procedures to `README.md`, concise agent conventions here, and one-off evidence to the Wiki.

## Project-Specific Verification

Run only the rows relevant to the approved change, but never omit the final-revision verification row for an in-scope behavior.

| Change scope | Minimum verification | Claim boundary |
|---|---|---|
| Documentation only | `git diff --check` plus links, paths, and command snippets inspected against the current tree | Documentation consistency only |
| Java, Gradle, or build configuration | `./gradlew clean test bootJar --no-daemon` | Compilation, Gradle task result, and six executable JARs; report any `NO-SOURCE` tasks |
| Compose syntax or environment mapping | `docker compose --env-file .env.example -f docker-compose.yml -f docker-compose.app.yml config --quiet` | Compose parsing and variable resolution only |
| Image or container-runtime behavior | Follow `README.md`: build, `up --wait`, six application-health checks, in-scope Phase 1 E2E, and shutdown | Only the runtime paths actually exercised |
| Dependency recovery or persistence | Follow the README recovery drill and persistence sentinels for each affected dependency | The tested failure, deadline, recovery, and data state only |
| Remote push or CI | Verify the remote ref/SHA and the workflow run's target SHA, status, conclusion, and jobs | The exact remote revision and workflow only |
| Deployment | Verify the deployed revision, environment health, and required smoke/recovery checks | Never infer deployment readiness from image-build CI |

Use the repository's fail-closed `.env` validator and loader before local runtime checks. Never print, commit, or paste expanded secret values. `.env.example` is suitable for CI-style rendering and builds; it is not evidence that real credentials or a deployed environment work.

## Remote and CI Closure

When push or CI is in scope:

```bash
git ls-remote --heads origin <branch>
gh run list --workflow build-images.yml --branch <branch> --limit 5
gh run view <run-id> --json headSha,status,conclusion,url,jobs
```

The local final SHA, remote branch SHA, and workflow `headSha` must identify the same intended revision before reporting remote verification. A successful `git push`, a run for an earlier SHA, or local Docker success is insufficient.

## Integration and Cleanup

Integration completion and destructive cleanup are separate decisions. After integration, inventory exact branch names, stash identifiers, and worktree paths with their adoption and recovery status. Preserve them until the user separately approves those exact cleanup targets.

Do not force-push, rewrite history, delete branches, drop stashes, remove worktrees, reset broad state, or delete local recovery files from a general instruction to “finish” or “clean up.”

## Completion Report

Report:

- final local revision and, when remote work is in scope, the remote revision; otherwise record the `N/A` reason
- changed files and approved scope
- commands, environments, exit codes, and essential results
- what each check proves and does not prove
- `NO-SOURCE`, unrun checks, residual risks, and follow-up work
- CI run URL and target SHA when in scope
- preserved cleanup candidates and their separate approval state

Reviewer agreement and documentation are not substitutes for commands, tests, runtime observations, or remote evidence.
