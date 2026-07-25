# M4 and Other Mac Integration Design

## Goal

Create one integration branch based on the verified M4 container transition and
merge the preserved other-Mac branch without regressing the local runtime
contract.

## Inputs

- Base: `codex/m4-container-transition@3ef891938dcb78da39656c3b409283e507fe044e`
- Merge source: `codex/other-mac-local-changes-20260725@098ab3c4d97aa6d13e4200cf584491971b4c9339`
- Integration branch: `codex/m4-container-integration`

The source branches remain unchanged and available for rollback or audit.

## Merge policy

Use a real merge commit so the preserved branch is represented in the integration
history. Resolve the resulting tree selectively:

- Keep the M4 versions of `.env.example`, `docker-compose.yml`, and
  `docker-compose.app.yml`.
- Keep the M4 local operations runbook in `README.md`; append only the compatible
  CI and EC2-first deployment guidance from the preserved branch.
- Exclude `.idea` changes. IntelliJ settings remain isolated for later integration.
- Accept the six Dockerfile operational cleanups (`WORKDIR`, `EXPOSE`, and the
  absolute application path), but retain `eclipse-temurin:21-jre`. The Alpine base
  image transition is a separate supply-chain and runtime decision.
- Accept the GitHub Actions image-build slice after adapting it to the M4 contract.

## GitHub Actions contract

The workflow must:

1. run for pull requests, pushes to `main`, and manual dispatch;
2. use Java 21 and the Gradle build action;
3. run `./gradlew clean test bootJar --no-daemon`;
4. require exactly one executable jar for each of the six services;
5. validate the combined M4 Compose files with `.env.example`;
6. build all six images with the commit SHA as the tag;
7. stop before ECR push or EC2 deployment.

## Invariants

The integration must preserve these verified M4 properties:

- host development continues to use localhost-oriented `.env.example` values;
- application-container dependencies use `condition: service_healthy`;
- required credentials fail closed through `${VAR:?}`;
- published service ports bind only to `127.0.0.1`;
- the README continues to require `.env.example` to be copied to a private `.env`;
- the full local runbook and bounded health procedures remain present.

## Verification

Before committing the merge:

- prove that pre-change acceptance probes fail for the missing Dockerfile and CI
  behavior;
- run `git diff --check`;
- compare the final environment and Compose files byte-for-byte with M4;
- validate the combined Compose configuration;
- run `./gradlew clean test bootJar --no-daemon`;
- require exactly six executable jars;
- build all six Docker images;
- cold-start the stack and verify all six application health endpoints;
- run a fresh Phase 1 performance-to-booking and booking-to-performance cycle;
- inspect the final merge parents, changed-file scope, and clean worktree.

## Non-goals

- IntelliJ project settings
- Alpine base images
- ECR publishing
- EC2 deployment automation
- RDS, load balancers, NAT gateways, Nginx, HTTPS, or CodeDeploy
