# Release v1.0.0: Full policy lifecycle with required CI

| | |
|---|---|
| Version | **v1.0.0** |
| Date | 2026-11-05 |
| Promotion | GitHub PR `dev` → `main`, "Create a merge commit"; tag `v1.0.0` on the merge commit |
| QA'd revision | `dev` @ `943359a` (PR #6 merged) |
| Previous release | [v0.1.0](RELEASE-v0.1.0.md) (Sprint 1: catalog and quote) |

## Included PRs

| PR | GitHub | Branch | Type | Review sign-off |
|---|---|---|---|---|
| [PR-007](../prs/PR-007.md) | #1 | `chore/gh-pr-workflow` | Substrate chore | No reviewer-agent run (see the note below) |
| [PR-008](../prs/PR-008.md) | #2 | `feat/application-underwriting` | Feature: Sprint 2 | Harness PASS 92/92. `security-reviewer` **APPROVE**, `test-engineer` **APPROVE** |
| [PR-009](../prs/PR-009.md) | #3 | `feat/issuance-endorsement` | Feature: Sprint 3 | Harness PASS 93/93. `security-reviewer` **APPROVE**, `test-engineer` **APPROVE** |
| [PR-010](../prs/PR-010.md) | #4 | `feat/renewal-cancellation` | Feature: Sprint 4 | Harness PASS 117/117. `security-reviewer` REQUEST CHANGES, then **APPROVE** after the fix loop. `test-engineer` **APPROVE** |
| [PR-011](../prs/PR-011.md) | #5 | `chore/ci-hardening` | CI: Sprint 5 | Harness PASS 74/74. `security-reviewer` REQUEST CHANGES, then **APPROVE** after the fix loop. `test-engineer` **APPROVE**. Live CI `claude-review` **APPROVE** on both passes |
| (follow-up to PR-011) | #6 | `chore/ci-hardening` | Baselines and docs | `-linux` visual baselines and KD-016..033. CI green, with every Linux visual asserted. No production code |

**Note on PR-007.** `/release` requires security and test-engineer approvals for every *feature* merged since the last
release. PR-007 changes only the `/pr` and `/release` commands, root `CLAUDE.md` rule 3 and KD-013, with no
application, hook or data-path code. It is treated as out of scope for the reviewer-agent requirement, and that is
recorded here rather than left implicit.

## Delivered

**Application, KYC and underwriting (Sprint 2: AC-03, AC-04, AC-09)**
- KYC stub with synthetic Aadhaar and PAN validation, and age matching the quote.
- Rule-based underwriting (AUTO_BIND / MANUAL_REVIEW / DECLINE, with reason codes).
- Underwriter workbench with masked KYC. ADMIN override of a DECLINE, with an audit entry carrying the actor ID.

**Issuance, endorsement and state machine (Sprint 3: AC-01.12/13, AC-03.9, AC-05, AC-06, AC-10)**
- Quote ownership for signed-in customers.
- Unique policy numbers, ACTIVE policies, and append-only versions and transitions.
- Address, nominee and sum-insured endorsements, with a preview and atomic writes.
- The full policy state machine with `InvalidPolicyStateException` returned as 409.

**Renewal, cancellation and dashboard (Sprint 4: AC-07, AC-08, AC-10.3, AC-11)**
- Idempotent renewal offers at the active rule version, renewal payment, and per-product grace-period lapse.
- Re-pricing of a pending offer when the sum insured changes.
- Pro-rata refunds with cooling-off and a 0.00 floor, append-only refund records, and no backdated cancellations.
- Admin portfolio dashboard and a synthetic sample-policy seed.
- Security hardening: no Basic challenge, a fixed problem `instance`, a sanitised key echo, a 503 when the policy-number
  sequence is exhausted, and a bounded admin job date.

**CI (Sprint 5: NFR-08.1, NFR-09.1–09.7)**
- **Platforms:** GitHub Actions on `origin` and an equivalent `.gitlab-ci.yml`.
- **Jobs:** `build`, `frontend-unit`, `e2e`, `quality-gates`, a required sandboxed Claude Code review, a key-free comment
  job and the `ci-gate` aggregator.
- **Pinning:** SHA-pinned actions, digest-pinned images, and lockfile and hash-locked installs.
- **The Claude review:**
  - The key lives only in the protected `claude-review` environment, which the owner approves on each run.
  - The CLI has no shell and runs in a disposable worktree inside a bubblewrap sandbox with an allowlisted environment,
    and the job fails closed if the real checkout changes.
- **Visual baselines:** `-linux` baselines generated in CI.
- **Branch ruleset:** `dev` and `main` now require `ci-gate` and accept merge commits only.

## QA results on `dev` @ `943359a` (2026-11-05)

| Command | Result |
|---|---|
| `./mvnw clean verify` (JDK 21.0.13, SPA packaged) | BUILD SUCCESS. Surefire 442 and failsafe 570 tests (per-class XML totals), 0 failures, 0 errors, 0 skipped. JaCoCo gates met: 94.0% instructions overall, 95.4% domain (gates 70% and 80%) |
| `npm test -- --run` | 17 files, **173/173** passed |
| `npm run build` | OK |
| `npx playwright test --update-snapshots=none` | **48 passed** (desktop-chromium + mobile-chromium) |
| `premium_check.py spec-examples` | **9/9 PASS**, including AC-06.2 1330.85, AC-08.1 4473.29, AC-08.2 6850.00 and AC-08.3 0.00 |
| `ac_audit.py --offline --no-write` | **93/93** covered (100%), 0 parent-only, 0 not covered |
| `redact_evidence.py --check docs sprint-contracts specs/reviews` | 0 findings |
| GitHub Actions on `dev` (push run after PR #5) and on PR #6 | All jobs green, including `claude-review` and `ci-gate`. 20/20 Linux visuals asserted |

## Known residuals and open items
- **Accepted v1 limitations (in the specs):**
  - SG-1: sum-insured endorsements don't re-run underwriting.
  - SG-3: renewal re-rates the stored inputs without ageing them.
  - SG-5: an early renewal payment closes the rest of the old term.
  - SG-6: `premiumCollected` isn't cut at `asOf`.
  - SG-7: a policy that the active rule version rejects gets no offer.
  - NFR-09.7: a same-repository PR can change its own workflow. The key is still released only after the owner's
    environment approval.
- **Security follow-ups (minor, from the Sprint 5 re-review):**
  - fail closed on a symlink that resolves outside the repository;
  - harden the tree check (`core.fsmonitor` is done; hash `.git/config` and the CLI binary);
  - keep the key out of the `git` and `ac_audit.py` child processes;
  - add a `/proc` canary pass;
  - on PRs, the reviewed copy is the merge commit rather than the head commit.
- **GitLab:** `.gitlab-ci.yml` has not run on a real GitLab project yet. Its review needs a runner that allows user
  namespaces, otherwise it fails closed by design. GitLab ignores `.github/CODEOWNERS`.
- **Process:**
  - Single owner, so code-owner review isn't enforced. The environment approval is the control.
  - Each PR needs one manual approval of the `claude-review` deployment.
- **Local environment:** the JDK moved to 21.0.13. The surefire per-class XML totals differ from Maven's console summary
  (same run counted two ways, KD noted in Sprint 2).
