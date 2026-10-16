# Review

Date: 2026-10-16

Independent staged-diff review completed against `REVIEW.md` after verification.

## Bugs

Clear. A failed initial load keeps all save actions disabled and exposes a retry action; successful retry is required before writes are enabled.

## Security

Clear. The migration scopes legacy menu/permission replacement and grants to tenant 1, preserves other tenants' rows, and does not create cross-tenant relations. API forms reject client-supplied tenant identifiers and persistence continues to rely on the authenticated tenant context.

## Compliance

Clear. All three mutations use the platform audit mechanism with request and response capture disabled. Audit titles identify the fixed key/key-group and the audit event records the operation result without CSP, weak-password, or other configuration values.

## Nits

- Non-blocking coverage gap: the staged tests do not run the complete anonymous/unauthorized/authorized/ROOT security matrix or a service-level two-tenant context integration test. Existing coverage verifies unknown tenant fields are rejected, database uniqueness permits the same key across tenants, and the migration preserves cross-tenant boundaries.

No Important findings remain.

## Accepted UI refinement review

Review scope: committed diff `3435cea..a9a5a68`, after human acceptance and clean-worktree verification.

- Bugs: clear.
- Security: clear.
- Compliance: one Nit. In `macula-cloud-admin/src/views/system/securityPolicy/index.vue` (first occurrence around lines 37–50, repeated for the other controls), units and help text are visually adjacent but are not programmatically associated with their controls through `aria-describedby`. A screen reader may therefore omit constraints such as the unit or “0 disables this check” while the control is focused. Suggested follow-up: give each unit/help element a stable ID and bind the relevant IDs to each input-number, switch, textarea, and radio-group.

No Important findings were found. The accessibility Nit is non-blocking and remains within the five-Nit cap from `REVIEW.md`.
