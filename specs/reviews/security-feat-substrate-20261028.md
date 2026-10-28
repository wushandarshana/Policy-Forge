# Security + test review: feat/substrate (2026-10-28)

**Reviewer:** security-reviewer + test-engineer (review mode). **Scope:** `git diff main...feat/substrate` (76 files, substrate only, no production code).
**Method:** read all hooks, settings, specs, agents, skills and commands. Exercised every hook with crafted JSON, including a scratch git repo on `main` for `no_direct_main_commit.py`. Ran `premium_check.py spec-examples` (9/9 PASS) and `ac_audit.py --offline` (runs, 0/66 covered as expected).

## Checklist
| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | Auth boundary (NFR-04) | FAIL (spec) | app_spec.md:78-79 vs :106. See F1 |
| 2 | Ownership | PASS (guidance) | web/CLAUDE.md:9-10 (returns 404 for other customers' resources) |
| 3 | Audit | PASS (spec) | underwriting_spec.md:68-70, product-catalog_spec.md:57 |
| 4 | PII in logs | PARTIAL | Hook works for direct field logging. Bypass and false-block cases in F6 |
| 5 | Injection | N/A | No code yet. Hooks never pass input to a shell (no `shell=True`, wrappers take no args) |
| 6 | Append-only | PARTIAL | Hook misses Lombok setters (F7) |
| 7 | Secrets / synthetic data | PASS | Only `XXXX-XXXX-1234`, `ABCDE1234F`, "Asha Test" and the synthetic users in app_spec §2. No tokens or keys |
| 8 | Error leakage | PASS (guidance) | web/CLAUDE.md:14-15 (ProblemDetail) |
| T | Money examples | PASS | AC-01.1/2/3/6, 06.2, 08.1/2/3 and the dashboard sums (20550/30300/22700/73550) all recompute exactly |

## Findings
| ID | Sev | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| F1 | BLOCKER | specs/app_spec.md:106, :78-79; specs/quote-engine_spec.md:14-16, :25 | `GET /api/quotes/{id}` is listed as public in §8, but NFR-04.1 makes every `/api/**` endpoint except `GET /api/products**` and `POST /api/quotes` return 401. The stored quote holds `riskInputs`, including `healthDeclarations`, `bmi` and `age` (health PII under NFR-03). As written, a generator will expose it without auth, and the `Q-…` ids could be enumerated. | Choose one rule and apply it in both places. Either require auth plus ownership, or keep the endpoint public but use unguessable UUID quote ids, never return `healthDeclarations` or `bmi`, and add the endpoint to the NFR-04.1 exception list. |
| F2 | MAJOR | .claude/hooks/lib/no_direct_main_commit.py:26 | Bypasses (tested, exit 0 on `main`): `git -C . commit`, `git --no-pager commit`, `git cherry-pick`, `git revert`, `git reset --hard`, `git pull`, `git rebase`. From a feature branch, `git checkout main && git commit -m x` also passes, because the branch is read before the command runs. | Match `git` followed by any global options (`(-C\s+\S+\|-c\s+\S+\|--[\w-]+(=\S+)?)\s+`). Also cover cherry-pick, revert, reset, pull and rebase. Block any command that switches to main (`checkout\|switch main`) and then runs a commit-creating subcommand. |
| F3 | MAJOR | no_direct_main_commit.py:26, :29 | False blocks on legitimate work (tested, exit 2). On `main`: `git checkout -b feat/x && git commit`, which is the exact remedy the hook's own message suggests, and `echo "git commit"`. On a feature branch: `git push -u origin feat/domain-main`, and any command where `main` appears after `git push`. This review's own test command was blocked this way. | Split the command on `&&`, `;` and `\|\|`. Track `checkout`/`switch` within the chain. Match `main` only as a push refspec (`\s(main\|HEAD:main\|\S+:(refs/heads/)?main)\b`). |
| F4 | MAJOR | .claude/hooks/lib/hookio.py:24-30 (all path-scoped hooks) | `rel_path` strips `cwd` from the path, and `cwd` is the session's current directory. After a `cd backend` (which `/pr` step 2 does, pr.md:17), paths become `src/main/java/...`. The precision and PII hooks then exit 0 for `double` money or `log.info(kyc.aadhaar())` (tested). The migration-immutability check is bypassed the same way. | Resolve the root from `$CLAUDE_PROJECT_DIR`, or from `git rev-parse --show-toplevel`, instead of `cwd`. Also pass that root to `tracked_on_main`. |
| F5 | MAJOR | .claude/hooks/lib/premium_precision_check.py:13, :23 | The money check covers only `domain` and `service`. JPA entities live in `repository/` (repository/CLAUDE.md:3) and DTOs in `web/`. `private double premium;` in `QuoteEntity` passes, as do `BigDecimal.valueOf(0.1d)` and `new BigDecimal(1e-3)` (all tested). Meanwhile `"cannot float this"` in a string literal false-blocks. | Widen to `backend/src/main/java/**`. Strip string literals before matching. Extend the literal regex to cover `[dDfF]` suffixes and exponents. |
| F6 | MINOR | .claude/hooks/lib/pii_redaction_check.py:15-22 | Misses: the fluent API `log.atInfo().log(..., kyc.aadhaar())`, and whole-object logging `log.info("{}", request)`. False blocks: `log.info("KYC passed for {}", id)` (matched by `\bkyc\b` inside the message text) and `maskedFullName`. The split only on `;\n` joins two statements that share a line. | Strip string literals before the PII match. Add `\.at(Info\|Debug\|...)\(`. Keep the log-capture test (NFR-03.1) as the real gate. |
| F7 | MINOR | .claude/hooks/lib/policy_immutability_check.py:32 | `@Setter` and `@Data` (Lombok) on an append-only entity pass (tested). Edits to published migrations made through Bash (`sed -i`, `rm`) are never inspected. | Also block `@Setter\|@Data` in append-only entities. Note the Bash gap in hooks/CLAUDE.md. Flyway validate-on-migrate stays the backstop. |
| F8 | MAJOR | CLAUDE.md:29-30 vs test-engineer.md:17, spec-to-test-generator/SKILL.md:24, ac_tag_check.py:24; scripts/ac_audit.py:46, :122, :167 | The root rule prescribes `@DisplayName("AC-NN: …")` with no sub-id. `ac_audit` fails only on MISSING, so a single `@Tag("AC-01")` turns all eight AC-01.x into PARENT-ONLY and passes the gate. ac-coverage.md:18 applies a stricter rule. The result is a weak, inconsistent traceability gate. | Change CLAUDE.md rule 8 to `AC-NN.M`. Make `ac_audit` exit 1 on PARENT-ONLY too, or behind a `--strict` flag used in CI. |
| F9 | MINOR | scripts/ac_audit.py:155-158, :163 | Only `ImportError` is caught. SDK runtime errors (CLI not found, auth) crash without writing a report. Every run, CI included, writes a new timestamped file into `specs/reviews/`, and one generated report is committed (`ac-audit-20261028-1023.md`). With `--offline` it exits 1 whenever any AC is MISSING, so CI stays red until all 66 ACs have tests. NFR-01/02/05/08 tags on ArchUnit tests will be reported as orphans, because those NFRs have no `.M` criteria. | Catch `Exception`, write the report and mark the review skipped. Add `--out`/`--no-write` for CI. Decide how per-sprint scoping works (`--spec`). Teach the script about NFR parents. |
| F10 | MINOR | specs/renewal-cancellation_spec.md:30 vs :34 | `ProRataRefundCalculator.calculate(...)` has no `policyEffectiveDate` or first-term parameter, but the cooling-off rule needs both. | Add `policyEffectiveDate` and `isFirstTerm` to the signature. |
| F11 | MINOR | specs/renewal-cancellation_spec.md:71 vs app_spec.md:98-120 | `GET /api/policies/{number}/cancel/preview` is not in the §8 role table, so the security reviewer cannot check its role. | Add it as `owner`. |
| F12 | MINOR | specs/portfolio-dashboard_spec.md:23-24 | "plus positive adjustments, minus negative adjustments" double-negates if negative adjustments are stored as negative numbers. | Write it as "plus signed endorsement adjustments, minus refunds". |
| F13 | MINOR | specs/policy-issuance_spec.md:52 | AC-05.2 requires the 50 concurrent sequence numbers to be "contiguous". DB sequences can leave gaps on rollback, so this test can flake. | Assert uniqueness and format only, or state that issuance is serialised. |
| F14 | MINOR | domain/CLAUDE.md:15 vs policy-issuance_spec.md:4; CLAUDE.md:40 | The domain file names `PolicyNumberFormat`, while the spec names `PolicyNumberGenerator`. CLAUDE.md:40 cites `.gitlab-ci.yml`, which does not exist. | Align the class name. Add the CI file or mark it as planned. |
| F15 | MINOR | .claude/hooks/*.sh:9; hookio.py:13-17 | With no Python, every Write, Edit and Bash call is blocked (exit 2), which also blocks the fix. Malformed JSON and git failures fail open, which is acceptable. If both the plugin hooks.json and settings.json are active, every hook runs twice. | Document the Python requirement in harness-setup.md. Do not enable the plugin inside this repo. |

## Notes
- The live `no-direct-main-commit` hook blocked one of this review's own test commands, which confirms F3 in practice.
- The wrappers are safe: LF line endings are enforced (.gitattributes), stdin is parsed only as JSON, and no input reaches a shell.

VERDICT: CHANGES_REQUESTED

---

## Resolution (implementer, same branch, before merge)

| Finding | Status | Change |
|---|---|---|
| F1 BLOCKER | Fixed | `GET /api/quotes/{id}` stays public but uses `Q-<UUIDv4>` ids and omits `bmi`/`healthDeclarations`. Added NFR-04.2 and listed the endpoint in the NFR-04.1 exceptions (`specs/app_spec.md`, `specs/quote-engine_spec.md`) |
| F2 MAJOR | Fixed | `no_direct_main_commit.py` rewritten: per-segment parsing, git global options skipped, in-command branch switches tracked; cherry-pick, revert, am, rebase, reset and pull also blocked on main; refspec-aware push check |
| F3 MAJOR | Fixed | Only segments whose program is git are inspected (`echo "git commit"` allowed); `checkout -b … && commit` allowed; `push` blocked only when the destination ref is main |
| F4 MAJOR | Fixed | `hookio.project_root` uses `$CLAUDE_PROJECT_DIR`, then `git rev-parse --show-toplevel`; the session cwd is no longer used |
| F5 MAJOR | Fixed | Precision check covers all of `backend/src/main/java`; floating literals with exponent or d/f suffix caught in `new BigDecimal`/`valueOf`; string literals stripped first |
| F8 MAJOR | Fixed | Root CLAUDE.md rule 8 requires `AC-NN.M` in names. `ac_audit.py` treats PARENT-ONLY as not covered |
| F6 MINOR | Fixed | `log.atInfo()` fluent API and whole request/KYC/DTO arguments are now caught; string literals stripped; identifiers matched on word boundaries |
| F7 MINOR | Fixed / documented | Lombok `@Setter`/`@Data` blocked on append-only entities. Shell-made edits documented as a known limit, with ArchUnit and Flyway as the backstop |
| F9 MINOR | Fixed | Broad exception handling around the agent call; `--no-write` and `--report-only` flags; NFR table ids are known parents |
| F10 MINOR | Fixed | `ProRataRefundCalculator.calculate` signature now has `policyEffectiveDate` and `firstTerm` |
| F11 MINOR | Fixed | `GET /api/policies/{number}/cancel/preview` added to the §8 role table |
| F12 MINOR | Fixed | Dashboard formula: signed adjustments, no double negation |
| F13 MINOR | Fixed | AC-05.2 split: uniqueness under concurrency, contiguity for sequential issue |
| F14 MINOR | Fixed | `PolicyNumberFormat` (domain) vs `PolicyNumberGenerator` (service) clarified; `.gitlab-ci.yml` marked "added in Sprint 5" |
| F15 MINOR | Documented | Failing closed without Python is deliberate; warning added against double-enabling plugin hooks (`.claude/hooks/CLAUDE.md`) |

**Verification:** `python .claude/hooks/tests/test_hooks.py` covers 45 cases, including every reproduced bypass and false
block from this review: ALL PASSED. `premium_check.py spec-examples`: 9/9 PASS. `ac_audit.py` was probed with a
synthetic test file: exact tag counted COVERED, parent-only counted as a gap, NFR-08 not orphaned, report-only exits 0.

**Implementer self-assessment (not a reviewer verdict):** no BLOCKER or MAJOR finding remains open. The verdict above
stays CHANGES_REQUESTED for the reviewed revision, and the fixes need a re-review before merge.

---

## Re-review (reviewer, 2026-10-28)

**Scope:** `git diff aac48a7..HEAD` (bd8a2d1, 7a42ee5, f7bf316). `test_hooks.py`: ALL PASSED. `premium_check.py spec-examples`: 9/9 PASS.
I also ran about 45 adversarial cases that the suite does not cover, all exercised against the live hooks.

**Closed:**
- F1, F10, F11, F12, F13, F14: the spec edits agree with each other. NFR-04.1 and NFR-04.2, the §8 table, and quote-engine_spec all say the same thing. The new `calculate` signature appears nowhere else. `PolicyNumberFormat` (domain) and `PolicyNumberGenerator` (service) now match domain/CLAUDE.md:15.
- F2 and F3: the bypasses and false blocks I reported are fixed.
- F4: `project_root` gives the right root with `$CLAUDE_PROJECT_DIR` set, without it when `cwd=backend`, and for non-normalised (`..`) paths.
- F5 scope, F7, F8, F9, F15: fixed as described in the Resolution table.

**New or still open:**

| ID | Sev | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| R1 | MAJOR | .claude/hooks/lib/hookio.py:43-45 (used by precision, PII and immutability) | Comments are stripped **before** strings. Any string containing `/*` opens a fake comment that runs to the next `*/`, for example `"/api/products/**"` in SecurityConfig or `produces = "*/*"`. Everything up to the next Javadoc close is then removed. Verified: `requestMatchers("/api/products/**") … double premium = 1; … /** doc */` exits 0, and `"*/*"` hides `log.info(…, a.aadhaar())`. | Use a single-pass tokenizer that skips string and char literals while it removes comments, or blank strings first and then strip comments. Add both cases to test_hooks.py. |
| R2 | MAJOR | .claude/hooks/lib/pii_redaction_check.py:23-24 | Regression from the earlier substring match. With `\b…\b` word matching, JavaBean getters on a non-`kyc` object pass: `log.info("{}", applicant.getAadhaar())`, `applicant.getPanNumber()` and `e.getDob()` all exit 0. These are the normal JPA entity accessors. | Match PII stems inside identifiers, e.g. `(?i)(aadhaar\|healthdeclaration\|dateofbirth\|fullname\|\bkyc)` as substrings, plus `(?<![a-z])(get)?(pan\|dob)(number)?(?![a-z])`. |
| R3 | MINOR | no_direct_main_commit.py:21, :106 | False blocks on main: `git pull --ff-only`, `git reset HEAD <file>`, `git rebase --abort`. `merge --ff-only` is also blocked, so main can't be brought up to date from the remote after a GitLab MR. | Allow `pull --ff-only`, `merge --ff-only origin/main`, `reset` with paths or without `--hard`, and the `--abort`/`--continue`/`--quit` forms of rebase, cherry-pick, revert and am. |
| R4 | MINOR | no_direct_main_commit.py:58-71 | `git checkout main -- README.md && git commit` on a feature branch is falsely blocked: a path checkout is read as a branch switch. `git checkout -` is not tracked. | Treat it as a switch only when there is no `--` and no extra positional argument. Resolve `-` with `git rev-parse --symbolic-full-name @{-1}`. |
| R5 | MINOR | no_direct_main_commit.py:41-55, :74-83 | Not covered (all exit 0): <br>• Wrapped commands: `bash -c "git commit"`, `(git commit)`, `{ git commit; }`, `$(…)`, `env/command/time/xargs git …`, and an unquoted `C:\…\git.exe` (shlex removes the backslashes). <br>• Commands that move main without a commit: `git branch -f main X`, `git update-ref refs/heads/main`, `git fetch . X:main`, `git push --all/--mirror`, and glob refspecs. <br>These would mostly be deliberate evasion, and the hook is a guardrail, not a boundary. | Also block a write to `refs/heads/main` via `branch -f`, `update-ref` or `fetch …:main`, and `push --all/--mirror`. Search for the `git` token after the wrapper prefixes `env\|command\|time\|xargs\|bash -c\|sh -c\|(\|{`. Document what remains. |
| R6 | MINOR | premium_precision_check.py:25-29 | `premium.doubleValue() * 1.05`, `BigDecimal.valueOf(Math.pow(1.1, 2))` and `var rate = 0.025; BigDecimal.valueOf(rate)` all pass. | Flag `\.(double\|float)Value\s*\(`, `Math\.(pow\|sqrt\|exp\|log)` and `var x = <FLOAT_LITERAL>`. Make `MoneyPrecisionArchTest` forbid calls to `BigDecimal.doubleValue/floatValue` in production code. |
| R7 | MINOR | policy_immutability_check.py:24-25 with hookio.py:5-7 | In a git worktree under `.claude/worktrees/<n>/`, with `$CLAUDE_PROJECT_DIR` set to the main checkout, `rel_path` keeps the `.claude/worktrees/<n>/` prefix. `tracked_on_main` then never finds the file on main, so the published-migration and rule-file protection is silently skipped. | Compute the git top-level from the file's own directory (`git -C <dir> rev-parse --show-toplevel`) for the immutability path, and check `main:<path relative to that worktree>`. |
| R8 | MINOR | specs/quote-engine_spec.md:3 | The header lists only NFR-01, although the spec now carries an NFR-04.2 rule. | Add NFR-03 and NFR-04 to the header. |

**Not a finding:** every case listed under "Closed" behaves correctly against the live hooks, not only in the suite. None of R1–R8 affects production code in this PR, since there is none. R1 and R2 are silent bypasses of non-negotiables 5 and 7 that ordinary generated code (Spring Security config, JPA getters) will hit, and each is a small fix.

VERDICT: CHANGES_REQUESTED

---

## Third pass (reviewer, 2026-10-28)

**Scope:** 2ea0a31, 21cd2ab. `test_hooks.py`: ALL PASSED (69 cases). I ran about 45 additional cases the suite does not cover, against the live hooks.

**Closed:**
- **R1.** `strip_code` handles the following correctly: a `"/api/**"` string followed by Javadoc, escaped `\"` and `'\''`, text blocks, and literals inside Javadoc or annotations. I found no new bypass caused by the scanner.
- **R6.** `doubleValue`/`floatValue`, `Math.pow`, `3D` and bare float literals are caught. There are no false positives on hex, `1_000L`, `floorMod`/`ceilDiv`, `v2` package names, or decimal strings.
- **R7.** Resolved; R3 and R4 are fixed as reported.
- **R8.** The quote-engine header lists NFR-04.2.
- **R5.** Accepted as documented known limits; the backstop is a protected `main` on the remote.

**New or still open:**

| ID | Sev | File:line | Issue | Suggested fix |
|---|---|---|---|---|
| T1 | MAJOR | .claude/hooks/lib/no_direct_main_commit.py:95, :112, :104-105 | The R3 fix opened a bypass: `merge --ff-only` and `pull --ff-only` accept **any** remote ref. On main, `git merge --ff-only origin/feat/x`, `git pull --ff-only origin feat/x` and `git merge --ff-only FETCH_HEAD` all exit 0, which fast-forwards main to an unreviewed branch. The block message itself points the agent at "a remote-tracking ref". | Allow `merge --ff-only` only for `origin/main`, `refs/remotes/origin/main` or `@{u}`. Allow `pull --ff-only` only with no refspec or with refspec `main`. Drop FETCH_HEAD. |
| T2 | MAJOR | .claude/hooks/lib/pii_redaction_check.py:43, :37 | False blocks on idiomatic service code. The text inspected is everything since the previous `;`, so identifiers **before** the log call count. Exit 2 on `submit(String customerId, KycDto kyc) {\n log.info("… {}", customerId);` and on `if (kyc == null) { log.warn("… {}", applicationId); }`. `Locale.JAPAN` (`PAN` suffix) is also blocked. This will hit `ApplicationService` on its first log line. | Inspect only the log call's argument list: from the `LOG_CALL` match to its balanced closing parenthesis. Require `PII_SUFFIX` to come after a lowercase letter, so all-caps constants are not matched. |
| T3 | MINOR | pii_redaction_check.py:26-29 | Still not caught; the NFR-03.1 log-capture test is the backstop: <br>• `getPanNo()`, `getHealthDecl()` and `"…" + kycRecord`. <br>• A message pre-built from `kyc.aadhaar()` and then logged. <br>• TS template interpolation `` console.log(`${form.aadhaar}`) ``, because the whole template is blanked. | Add the `panno`/`healthdecl` stems and a `kyc` prefix. Keep `${…}` expressions when blanking template literals. |
| T4 | MINOR | premium_precision_check.py:32, :36 | Still not caught: `StrictMath.pow`, `Math.random/exp/log`, and a statically imported `pow(…)`. A false positive: non-money doubles anywhere in `backend/src/main/java` (e.g. Micrometer `publishPercentiles(0.5, 0.95)`) are blocked, with no opt-out. | Use `\b(Strict)?Math\.\w+\(` minus the int-only helpers, and add `import static java.lang.(Strict)?Math`. Exempt a `config/metrics` package, or document the rule. |
| T5 | MINOR | no_direct_main_commit.py:74-92, :98-110 | Remaining false blocks: `git reset --hard` on main with no target, `git reset HEAD~3 <file>`, and `git checkout main README.md` (path given without `--`). `git checkout -` inside the same command chain resolves against the branch before the command ran, so `git checkout main && git checkout - && git commit` is blocked. A minor bypass: `git checkout --track origin/main && git commit` is not treated as switching to main. | `reset --hard` with no commit-ish never moves HEAD, so allow it. Treat a second positional argument as a path. Track `-` within the chain. |

There is still no production code in this PR. T1 reopens the PR-only-merge bypass that F2 closed, and T2 will block the KYC service's ordinary logging. Both are fixes of a few lines.

VERDICT: CHANGES_REQUESTED

---

## Merge-gate verification (reviewer, 2026-10-28)

**Scope:** ae6eb32, limited to T1, T2 and regressions. `test_hooks.py` passes all 77 cases. I ran 43 more spot checks against the live hooks; all gave the expected result.

**T1: closed.**
- On main, `--ff-only` from main's own upstream is allowed: `origin/main`, `upstream/main`, `refs/remotes/origin/main`, `@{u}`, `pull --ff-only` with no remote or refspec, and `pull --ff-only origin main` (exit 0).
- Everything else is blocked (exit 2):
  - merge: `origin/feat/x`, `FETCH_HEAD`, `feat/x`, `origin/mainline`, and `origin/main origin/feat/x` (octopus);
  - pull: `origin feat/x`, `origin feat/x:main`, `. feat/x`, `origin main feat/x`, and `--depth 1 origin feat/x`;
  - the same `ff-only origin/feat/x` merge reached via `checkout main &&` from a feature branch.

**T2: closed.**
- Only the log call's own text is inspected (no_direct_main_commit is unaffected). These now pass (exit 0): a `KycDto kyc` parameter followed by a log of an id, `if (kyc == null) { log.warn(…, applicationId); }`, `var p = kyc.pan();` followed by a log of an id, and `Locale.JAPAN`.
- Detection is intact (exit 2): `applicant.getAadhaar()`, `applicantPan`, `getDob()`, a multi-line call, fluent `atInfo().addKeyValue`, a lambda argument, a whole `request`, and PII inside the call when an `if` sits on the same line.

**No regressions:** the R1 `"*/*"` case is still blocked; `merge --no-ff`, `reset HEAD <file>`, `rebase --abort`, `checkout -b … && commit` and `checkout main -- file` are still allowed; `push origin HEAD:main` and `checkout main && commit` are still blocked.

**Note, not a finding (MINOR, follow-up with T3–T5):** `git pull --ff-only <other-local-repo> main` is not checked, because only the refspec is inspected, not the repository argument. Exploiting it requires deliberate evasion.

T3–T5 stay accepted as follow-ups for a chore/ branch. There are no open BLOCKER or MAJOR findings.

VERDICT: APPROVE
