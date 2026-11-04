> Archived from `.sprint/20261103-223107/` on 2026-11-04 · branch `feat/renewal-cancellation` · harness agent-harness 2.8.0. Content below is unchanged from the harness artifact.

# Sprint progress summary (20261103-223107)

| Task ID | Status | Summary |
|---|---|---|
| TASK-001 | DONE | Built the pure domain rules for renewal grace and offers, pro-rata refunds, cancellation dates, refund premium basis, portfolio figures and the echoed-key sanitiser, all registered as beans, with the 322-test suite green and the oracle values matching. |
| TASK-002 | DONE | Added the typed API client and the Renew and Cancel panels on Policy Detail, a populated Refunds tab and the Admin Dashboard page (not yet mounted), with 145 Vitest tests passing. |
| TASK-003 | DONE | Removed the Basic challenge from 401 responses, added fixed per-type problem instance URNs (including the container error path), wired the shared key sanitiser into every caller-key echo, and added the 503 for an exhausted policy-number sequence, with the full verify suite green. |
| TASK-004 | DONE | Delivered the renewal backend: V10 rule-version column, current paid term logic, the renewal cycle and scheduler, the admin renewal job, and the pay endpoint, with 455 unit and 466 integration tests green and 81 of 81 ACs covered. |
| TASK-005 | DONE | Delivered the cancellation backend: V11 append-only refund record, refund preview and cancel endpoints with the P-5 check order, and refunds in the policy detail, with 474 unit and 521 integration tests green. |
| TASK-006 | DONE | Added the admin portfolio dashboard API (C6) and the idempotent synthetic sample-policy seed (C10) through the real services, with the backdated issuance path kept package-private and guarded by ArchUnit, and the full verify suite green. |
| TASK-007 | DONE | Added the Admin Dashboard route and nav link, the renewal, cancellation and dashboard Playwright journeys with eight committed visual baselines, and the sprint-wide verification (148 Vitest and 44 Playwright tests passing, 81 of 81 ACs covered). |
