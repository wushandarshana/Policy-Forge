"""Regression tests for PolicyForge guardrail hooks.

Run: python .claude/hooks/tests/test_hooks.py   (exit 1 on any failure)
Cases include every bypass / false-block reported in specs/reviews/security-feat-substrate-20261028.md.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HOOKS = Path(__file__).resolve().parents[1]
LIB = HOOKS / "lib"
ROOT = HOOKS.parents[1]
J = "backend/src/main/java/com/horizon/policyforge"
failures = []


def run(script, payload, env_root=True, cwd=None):
    env = dict(os.environ)
    if env_root:
        env["CLAUDE_PROJECT_DIR"] = str(ROOT)
    else:
        env.pop("CLAUDE_PROJECT_DIR", None)
    p = subprocess.run([sys.executable, str(LIB / script)], input=json.dumps(payload), capture_output=True,
                       text=True, env=env, cwd=cwd)
    return p.returncode


def expect(name, script, payload, code, **kw):
    got = run(script, payload, **kw)
    status = "ok  " if got == code else "FAIL"
    if got != code:
        failures.append(name)
    print(f"{status} {name} (exit {got}, want {code})")


def write(rel, content, cwd=None):
    return {"cwd": str(cwd or ROOT), "tool_input": {"file_path": str(ROOT / rel), "content": content}}


# --- premium_precision_check (NFR-01) ---
P = "premium_precision_check.py"
expect("double field in domain", P, write(f"{J}/domain/X.java", "class X { double r; }"), 2)
expect("double in repository entity", P, write(f"{J}/repository/QuoteEntity.java", "class Q { Double premium; }"), 2)
expect("float in web DTO", P, write(f"{J}/web/QuoteResponse.java", "record R(float premium) {}"), 2)
expect("BigDecimal.valueOf(0.1d)", P, write(f"{J}/domain/X.java", "var a = BigDecimal.valueOf(0.1d);"), 2)
expect("new BigDecimal(1e-3)", P, write(f"{J}/domain/X.java", "var a = new BigDecimal(1e-3);"), 2)
expect("BigDecimal from string ok", P, write(f"{J}/domain/X.java", 'var a = new BigDecimal("0.025");'), 0)
expect("BigDecimal.valueOf(long) ok", P, write(f"{J}/domain/X.java", "var a = BigDecimal.valueOf(1000);"), 0)
expect("'float' inside string ok", P, write(f"{J}/web/X.java", 'String m = "no float allowed";'), 0)
expect("DOUBLE column in migration", P, write("backend/src/main/resources/db/migration/V9__x.sql", "premium DOUBLE"), 2)
expect("R1: double after '/**' string not hidden", P, write(f"{J}/web/SecurityConfig.java",
       'String p = "/api/products/**";\nclass C { double premium; }\n/** doc */'), 2)
expect("R1: '*/*' string does not end a fake comment", P, write(f"{J}/web/C.java",
       '/* c */ String t = "*/*"; double x;'), 2)
expect("R1: double inside a real comment ok", P, write(f"{J}/domain/X.java", "/* never use double here */ class X {}"), 0)
expect("R6: doubleValue()", P, write(f"{J}/service/S.java", "var d = premium.doubleValue();"), 2)
expect("R6: Math.pow", P, write(f"{J}/domain/X.java", "var p = Math.pow(base, n);"), 2)
expect("R6: bare float literal", P, write(f"{J}/domain/X.java", "var rate = 0.025;"), 2)
expect("R6: int and hex literals ok", P, write(f"{J}/domain/X.java", "int a = 365; int m = 0x1F; long b = 10L;"), 0)
expect("violation detected when session cwd is backend/", P,
       write(f"{J}/domain/X.java", "class X { double r; }", cwd=ROOT / "backend"), 2, env_root=False)

# --- pii_redaction_check (NFR-03) ---
P = "pii_redaction_check.py"
expect("aadhaar in log.info", P, write(f"{J}/service/A.java", 'log.info("x {}", kyc.aadhaar());'), 2)
expect("multi-line log with pan", P, write(f"{J}/service/A.java", 'log.warn("x {} {}",\n id,\n k.pan());'), 2)
expect("fluent atInfo with dob", P, write(f"{J}/service/A.java", 'log.atInfo().addArgument(dateOfBirth).log("x");'), 2)
expect("whole request object", P, write(f"{J}/web/A.java", 'log.info("got {}", request);'), 2)
expect("whole KYC dto", P, write(f"{J}/service/A.java", 'log.debug("kyc {}", applicantKyc);'), 2)
expect("'KYC' in message text ok", P, write(f"{J}/service/A.java", 'log.info("KYC submitted for {}", applicationId);'), 0)
expect("maskedFullName ok", P, write(f"{J}/service/A.java", 'log.info("name {}", maskedFullName);'), 0)
expect("ids only ok", P, write(f"{J}/service/A.java", 'log.info("issued {} for {}", policyNumber, applicationId);'), 0)
expect("R2: getAadhaar() getter", P, write(f"{J}/service/A.java", 'log.info("x {}", applicant.getAadhaar());'), 2)
expect("R2: getPanNumber() getter", P, write(f"{J}/service/A.java", 'log.info("x {}", applicant.getPanNumber());'), 2)
expect("R2: getDob() getter", P, write(f"{J}/service/A.java", 'log.warn("x {}", e.getDob());'), 2)
expect("R2: applicantPan variable", P, write(f"{J}/service/A.java", 'log.info("x {}", applicantPan);'), 2)
expect("R2: company/expand identifiers ok", P, write(f"{J}/service/A.java", 'log.info("x {} {}", company, expandedId);'), 0)
expect("T2: KYC param in signature, log of id ok", P, write(f"{J}/service/A.java",
       'void submit(KycDto kyc, String customerId) {\n log.info("submitted {}", customerId);\n}'), 0)
expect("T2: kyc null-check before log ok", P, write(f"{J}/service/A.java",
       'if (kyc == null) { log.warn("missing kyc for {}", applicationId); }'), 0)
expect("T2: Locale.JAPAN ok", P, write(f"{J}/service/A.java", 'log.info("locale {}", Locale.JAPAN);'), 0)
expect("R1: PII after '*/*' string not hidden", P, write(f"{J}/web/A.java",
       '/* c */ String t = "*/*"; log.info("x {}", a.aadhaar());'), 2)

# --- policy_immutability_check (NFR-02/05) ---
P = "policy_immutability_check.py"
expect("setter on append-only entity", P, write(f"{J}/repository/EndorsementEntity.java", "public void setType(T t){}"), 2)
expect("Lombok @Data on append-only entity", P, write(f"{J}/repository/RefundRecordEntity.java", "@Data class R {}"), 2)
expect("JpaRepository for append-only repo", P,
       write(f"{J}/repository/AuditEntryRepository.java", "interface A extends JpaRepository<A, Long> {}"), 2)
expect("mutable aggregate method ok", P, write(f"{J}/repository/PolicyEntity.java", "public void applyTransition(S s){}"), 0)

# --- no_direct_main_commit (PR-only merge) ---
P = "no_direct_main_commit.py"
repo = Path(tempfile.mkdtemp(prefix="pf-hooktest-"))
try:
    git = ["git", "-C", str(repo), "-c", "user.email=t@example.invalid", "-c", "user.name=t"]
    subprocess.run(git[:3] + ["init", "-q", "-b", "main"], check=True)
    subprocess.run(git + ["commit", "-q", "--allow-empty", "-m", "init"], check=True)

    def bash(cmd):
        return {"cwd": str(repo), "tool_input": {"command": cmd}}

    # Branching strategy: feature -> dev -> main. Both main and dev are protected.
    for prot in ("main", "dev"):
        subprocess.run(git[:3] + ["checkout", "-q", "-B", prot], check=True)
        for cmd in ["git commit -m x", "git -C . commit -m x", "git --no-pager commit -m x", "git cherry-pick abc",
                    "git revert HEAD", "git reset --hard HEAD~1", "git pull", "git rebase feat/x",
                    "git reset HEAD~1", "git reset --hard", "git merge --ff-only FETCH_HEAD",
                    f"git merge feat/x", "git merge --ff-only feat/x", "git merge --ff-only origin/feat/x",
                    "git pull --ff-only origin feat/x", f"git push --force origin {prot}", f"git push origin +{prot}",
                    f"git push -f", f"git push origin --delete {prot}", f"git push origin :{prot}",
                    "git push --mirror origin", f"git push --force-with-lease origin {prot}"]:
            expect(f"on {prot}: block `{cmd}`", P, bash(cmd), 2)
        for cmd in ["git merge --abort", "git status", "echo \"git commit\"",
                    "git checkout -b feat/x && git commit -m x", f"git log --oneline {prot}",
                    "git pull --ff-only", f"git pull --ff-only origin {prot}", f"git merge --ff-only origin/{prot}",
                    "git merge --ff-only @{u}", "git reset HEAD README.md", "git reset", "git rebase --abort",
                    "git cherry-pick --abort", f"git push origin {prot}", "git push", f"git push -u origin {prot}"]:
            expect(f"on {prot}: allow `{cmd}`", P, bash(cmd), 0)
    # merge sources: dev <- feat/fix/chore ; main <- dev or hotfix/* (always --no-ff)
    subprocess.run(git[:3] + ["checkout", "-q", "dev"], check=True)
    for cmd in ['git merge --no-ff feat/x -m "Merge PR-003: thing (feat/x)" -m "Reviewed-by: a"',
                "git merge --no-ff fix/y", "git merge --no-ff chore/z --no-edit"]:
        expect(f"on dev: allow `{cmd}`", P, bash(cmd), 0)
    for cmd in ["git merge --no-ff main", "git merge --no-ff hotfix/h", "git merge --no-ff experiment",
                "git merge --no-ff -m feat/x random-branch"]:
        expect(f"on dev: block `{cmd}`", P, bash(cmd), 2)
    subprocess.run(git[:3] + ["checkout", "-q", "main"], check=True)
    for cmd in ['git merge --no-ff dev -m "Release: S1"', "git merge --no-ff hotfix/h"]:
        expect(f"on main: allow `{cmd}`", P, bash(cmd), 0)
    for cmd in ["git merge --no-ff feat/x", "git merge --no-ff chore/z", "git merge dev"]:
        expect(f"on main: block `{cmd}`", P, bash(cmd), 2)
    subprocess.run(git + ["checkout", "-q", "-b", "feat/domain-main"], check=True)
    for cmd in ["git commit -m x", "git push -u origin feat/domain-main", "git push origin HEAD",
                "git checkout main -- README.md && git commit -m x", "git push --force origin feat/domain-main"]:
        expect(f"on feature: allow `{cmd}`", P, bash(cmd), 0)
    for cmd in ["git checkout main && git commit -m x", "git checkout dev && git commit -m x",
                "git push origin HEAD:main", "git push origin +feat/x:main", "git push origin HEAD:dev",
                "git push origin feat/domain-main:dev"]:
        expect(f"on feature: block `{cmd}`", P, bash(cmd), 2)
    # R7: published migration edited inside a git worktree under .claude/worktrees/<n>/
    mig = "backend/src/main/resources/db/migration/V1__init.sql"
    subprocess.run(git[:3] + ["checkout", "-q", "main"], check=True)
    (repo / mig).parent.mkdir(parents=True)
    (repo / mig).write_text("create table t(id int);\n")
    subprocess.run(git[:3] + ["add", mig], check=True)
    subprocess.run(git + ["commit", "-q", "-m", "v1"], check=True)
    wt = repo / ".claude" / "worktrees" / "w1"
    subprocess.run(git[:3] + ["worktree", "add", "-q", "-b", "feat/w1", str(wt)], check=True)
    payload = {"cwd": str(wt), "tool_input": {"file_path": str(wt / mig), "content": "create table t(id bigint);"}}
    env = dict(os.environ, CLAUDE_PROJECT_DIR=str(repo))
    rc = subprocess.run([sys.executable, str(LIB / "policy_immutability_check.py")], input=json.dumps(payload),
                        capture_output=True, text=True, env=env).returncode
    ok = rc == 2
    failures.extend([] if ok else ["R7 worktree migration edit"])
    print(f"{'ok  ' if ok else 'FAIL'} R7: published migration edited in a worktree is blocked (exit {rc}, want 2)")
finally:
    subprocess.run(["git", "-C", str(repo), "worktree", "prune"], capture_output=True)
    shutil.rmtree(repo, ignore_errors=True)

print(f"\n{'ALL PASSED' if not failures else f'{len(failures)} FAILED: ' + ', '.join(failures)}")
sys.exit(1 if failures else 0)
