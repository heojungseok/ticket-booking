# M4 and Other Mac Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce one verified M4-based branch containing only the compatible Dockerfile, CI, and documentation parts of the preserved other-Mac changes.

**Architecture:** Create a real merge commit from the other-Mac preservation branch into an M4-based integration branch, then curate the merge tree. M4 remains authoritative for runtime environment, Compose topology, and the local runbook; selected packaging and CI changes are layered on top.

**Tech Stack:** Git, Java 21, Gradle 8.10, Spring Boot 3.3, Docker, Docker Compose v2, GitHub Actions

---

### Task 1: Establish executable acceptance probes

**Files:**
- Verify: `api-gateway/Dockerfile`
- Verify: `user-service/Dockerfile`
- Verify: `performance-service/Dockerfile`
- Verify: `booking-service/Dockerfile`
- Verify: `notification-service/Dockerfile`
- Verify: `queue-service/Dockerfile`
- Verify: `.github/workflows/build-images.yml`

- [ ] **Step 1: Run the Dockerfile probe before implementation**

Run a shell loop that requires `WORKDIR /app`, the correct `EXPOSE` port,
`/app/app.jar`, and the non-Alpine Java 21 runtime in all six Dockerfiles.

Expected: FAIL because the M4 Dockerfiles do not yet contain `WORKDIR` or `EXPOSE`.

- [ ] **Step 2: Run the workflow probe before implementation**

Require `.github/workflows/build-images.yml` to exist and contain
`clean test bootJar`, Compose validation, and six Docker build commands.

Expected: FAIL because the M4 branch has no workflow file.

### Task 2: Merge and curate preserved changes

**Files:**
- Preserve from M4: `.env.example`
- Preserve from M4: `docker-compose.yml`
- Preserve from M4: `docker-compose.app.yml`
- Exclude: `.idea/compiler.xml`
- Exclude: `.idea/gradle.xml`
- Exclude: `.idea/modules.xml`
- Exclude: `.idea/vcs.xml`

- [ ] **Step 1: Start the merge without committing**

Run:

```bash
git merge --no-ff --no-commit codex/other-mac-local-changes-20260725
```

Expected: conflicts in `README.md` and `docker-compose.app.yml`.

- [ ] **Step 2: Restore protected runtime files from M4**

Restore `.env.example`, `docker-compose.yml`, and `docker-compose.app.yml` from
the first parent (`HEAD`). Remove the other-Mac `.idea` additions and restore
tracked IntelliJ files from `HEAD`.

- [ ] **Step 3: Confirm protected-file equality**

Run:

```bash
git diff --quiet 3ef891938dcb78da39656c3b409283e507fe044e -- .env.example docker-compose.yml docker-compose.app.yml
```

Expected: exit 0.

### Task 3: Apply Dockerfile cleanup

**Files:**
- Modify: `api-gateway/Dockerfile`
- Modify: `user-service/Dockerfile`
- Modify: `performance-service/Dockerfile`
- Modify: `booking-service/Dockerfile`
- Modify: `notification-service/Dockerfile`
- Modify: `queue-service/Dockerfile`

- [ ] **Step 1: Retain the existing runtime base**

Every file begins with:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
```

- [ ] **Step 2: Add service metadata and the absolute application path**

Each file exposes its service port (`8000` through `8005`) and ends with:

```dockerfile
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Re-run the Dockerfile acceptance probe**

Expected: PASS for all six modules and no `21-jre-alpine` occurrence.

### Task 4: Adapt image-build CI

**Files:**
- Create: `.github/workflows/build-images.yml`

- [ ] **Step 1: Build and test executable jars**

Use:

```yaml
- name: Build and test executable jars
  run: ./gradlew clean test bootJar --no-daemon
```

- [ ] **Step 2: Verify exactly one jar per service**

Loop over the six service directories and fail unless each `build/libs`
directory contains exactly one `*.jar`.

- [ ] **Step 3: Validate M4 Compose and build six images**

Validate with `.env.example`, then build the six Dockerfiles with
`${{ github.sha }}` as `IMAGE_TAG`. Do not add registry login, image push, SSH,
or cloud deployment.

- [ ] **Step 4: Re-run the workflow acceptance probe**

Expected: PASS with exactly six Docker build commands.

### Task 5: Reconcile README documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Resolve the merge conflict with M4 as the base**

Keep the complete M4 local prerequisites, dotenv validation, health, E2E,
recovery, persistence, and shutdown sections.

- [ ] **Step 2: Add a bounded CI and deployment section**

Document the workflow's build-only scope, its three verification stages, and
the explicit absence of ECR push and EC2 deployment. Keep EC2/ECR as the next
manual deployment slice and defer RDS, NAT gateway, and load balancer work.

- [ ] **Step 3: Reject unsafe runtime instructions**

Confirm the README never instructs application startup with
`--env-file .env.example`; application startup must continue to use private
`.env`.

### Task 6: Verify and commit the integration

**Files:**
- Verify: all changed files

- [ ] **Step 1: Run static verification**

Run `git diff --check`, the protected-file equality check, Dockerfile probes,
workflow probes, and `docker compose ... config --quiet`.

- [ ] **Step 2: Build application artifacts**

Run:

```bash
./gradlew clean test bootJar --no-daemon
```

Expected: `BUILD SUCCESSFUL` and exactly six executable jars.

- [ ] **Step 3: Build and cold-start containers**

Build all six images, start the combined stack with `--wait`, and require HTTP
200 plus `UP` from all six actuator health endpoints.

- [ ] **Step 4: Run a fresh Phase 1 E2E cycle**

Create a fresh performance and seat, verify booking projection, complete a
booking and payment, and verify the paid-booking projection in the performance
database.

- [ ] **Step 5: Commit and inspect**

Commit the resolved merge, verify its first parent descends from the M4 commit
and its second parent is the other-Mac preservation commit, inspect the
first-parent diff, and require a clean worktree.

- [ ] **Step 6: Push**

Push `codex/m4-container-integration` to `origin`. Do not create a pull request
or merge it into `main`.
