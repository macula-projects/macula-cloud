# Review: 邮件和短信发送渠道与模板配置优化

Date: 2026-10-18

`REVIEW.md` was found. The review used the required Bugs, Security, and Compliance passes with a five-Nit cap.

## Fifth independent review

### Important

1. Mail preview rendered sender and body but omitted the actual subject, so a custom subject could not be verified.
2. Client-side preview samples still recognized the old `days` variable and produced invalid values for `user_email`, `now_time`, `expire_time`, and `expire_days`.
3. SMS template requests were not bound to the initiating provider/language. An older or failed response could leave stale rows that would then be saved under the current selection.

### Nits

1. The plan claimed validity content for every verification-code mail while the accepted catalog documents it only for the applicable scenes.

### Security

No new findings. Server-side HTML sanitization, variable escaping, the empty iframe sandbox, and sender-name escaping remain in place.

### Compliance

No new findings. New UI text remains in the existing locale resources and no additional project compliance requirement applies.

### Disposition

Returned to Build. All four findings are addressed in the working tree and require fresh Stage 4 verification before another independent review.

## Sixth independent review

`REVIEW.md` was found and applied with the required Bugs, Security, and Compliance passes and a five-Nit cap. Scope covered `origin/main...HEAD` plus the current working-tree fixes, excluding the platform/user-owned `AGENTS.md` and `.agent_context/`.

### Bugs

No findings. The review confirmed that the mail preview renders the escaped actual subject and uses server-side canonical samples, while SMS template results are bound to the initiating request, provider, language, and channel. Stale responses are ignored, failures clear the rows and surface an error, and save/test actions remain disabled while loading. Cypress covers subject rendering, request reordering, and load failure; renewed verification passes 25/25.

### Security

No findings. Subject and sender values are HTML-escaped, preview content remains server-sanitized, and the iframe retains an empty sandbox. No credential or sensitive value was added to preview state, errors, or logs.

### Compliance

No findings. The narrowed validity statement matches the implemented template catalog. Existing internationalization, authorization, audit-data minimization, and preview accessibility behavior remain intact.

### Earlier findings

All earlier findings remain closed: the IAM prefix-safe SMS URL, trusted-proxy/IP throttling, retained failure throttles, disabled-template rejection, success/failure audit records, proxy-compatible CSRF handling, HMAC target hashes, required deployment audit key, removed obsolete allowlist, mail subject rendering, canonical preview samples, and SMS request identity/failure isolation.

### Disposition

No Important findings and no Nits. The reviewed implementation and green verification evidence are ready for the human delivery/approval gate.
