# Verification

Date: 2026-10-16

## Backend

- `mvn test -Plocal` — exit 0, reactor BUILD SUCCESS across all 11 modules. Relevant totals include `macula-cloud-system` 41 tests, `macula-cloud-iam` 37 tests, and `macula-cloud-tinyid` 33 tests, with zero failures or errors.
- `SYSTEM_SECURITY_POLICY_MIGRATION_IT=true MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_DATABASE=<ephemeral> MYSQL_USERNAME=<test-user> MYSQL_PASSWORD=<redacted> mvn -pl macula-cloud-system -am -Dtest=SecurityPolicyMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test -Plocal` — exit 0, 1 MySQL 8.4 integration test passed. The test verifies duplicate preflight rejection, repair and successful V4 migration, `TEXT` value storage, per-tenant key uniqueness, menu replacement, and Flyway version 4.

## Admin

- `npm run test:unit -- --run` — exit 0, 4 files and 16 tests passed.
- `npm run build` — exit 0, 2258 modules transformed. Vite emitted only the existing large-chunk advisory.
- `npm run test:e2e:ci` — exit 0, 4 specs and 23 tests passed, including load-failure save blocking and retry.

## Repository checks

- `git diff --cached --check` — exit 0.
- The focused staged change does not modify agent instructions, skills, hooks, or protected configuration, so no configuration eval suite is applicable.

Verification is green

## UI acceptance refinement

Commit: `5dad8fb` (verified in a detached clean worktree after human acceptance)

- `npm ci` — exit 0; installed the locked dependency graph. npm reported 60 existing audit findings; this refinement does not change dependencies or the lockfile.
- `npm run test:unit -- --run` — exit 0: `Test Files 3 passed (3)` and `Tests 12 passed (12)`.
- `npm run build:docker` — exit 0: `✓ 2244 modules transformed.` Vite emitted only the existing large-chunk advisory.
- `npm run test:e2e:ci` — exit 0: `All specs passed!`, 3 specs and 10 tests passed, including both committed security-policy scenarios.
- Docker image `sha256:cf7059c7becae61efe5a7987df0f6ea7c112a4469c4654be92b16a0f0f720c66` was built from the clean commit, the local Admin container reached `healthy`, and `http://127.0.0.1:5900/` returned HTTP 200.
- The accepted refinement changes only Admin page markup, responsive CSS, localized help text, and its source-level unit assertions. Backend, migration, API, agent instructions, skills, hooks, and protected configuration are unchanged; the earlier full backend/MySQL evidence remains applicable and no configuration eval suite is required.

Verification is green
