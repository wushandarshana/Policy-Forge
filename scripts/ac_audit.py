"""AC traceability and spec/code drift audit driven by the Claude Agent SDK.

Phase 1 (deterministic): extract every AC-NN.M / NFR-NN.M from specs/*_spec.md and every
reference to those IDs from backend/frontend tests -> coverage matrix.
Phase 2 (agent): a read-only Claude agent inspects each covered AC's tests against the spec's
Given/When/Then and flags weak or drifting tests (wrong literals, missing assertions).

Usage:
    python scripts/ac_audit.py                 # full audit (needs ANTHROPIC_API_KEY or Claude Code login)
    python scripts/ac_audit.py --offline       # deterministic matrix only (CI without a key)
    python scripts/ac_audit.py --spec specs/quote-engine_spec.md

Writes specs/reviews/ac-audit-<YYYYMMDD-HHMM>.md. Exit code 1 if any AC has no test.
"""
import argparse
import asyncio
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AC_DEF = re.compile(r"\*\*((?:AC|NFR)-\d{2}\.\d+)\*\*\s*(.+)")
AC_REF = re.compile(r"\b((?:AC|NFR)-\d{2})(?:\.(\d+))?")
TEST_GLOBS = [
    "backend/src/test/java/**/*Test.java",
    "backend/src/test/java/**/*IT.java",
    "frontend/src/**/*.test.ts",
    "frontend/src/**/*.test.tsx",
    "frontend/e2e/**/*.spec.ts",
]


@dataclass
class Criterion:
    ac_id: str
    spec: str
    text: str
    exact: list = field(default_factory=list)   # file:line referencing AC-NN.M
    parent: list = field(default_factory=list)  # file:line referencing only AC-NN

    @property
    def status(self):
        return "COVERED" if self.exact else ("PARENT-ONLY" if self.parent else "MISSING")


def collect_criteria(spec_paths):
    criteria = {}
    for spec in spec_paths:
        for line in spec.read_text(encoding="utf-8").splitlines():
            m = AC_DEF.search(line)
            if m:
                criteria[m.group(1)] = Criterion(m.group(1), spec.relative_to(ROOT).as_posix(), m.group(2).strip())
    return criteria


def collect_references(criteria):
    orphans = []
    known_parents = {c.ac_id.split(".")[0] for c in criteria.values()}
    for pattern in TEST_GLOBS:
        for path in ROOT.glob(pattern):
            if "/fixtures/" in path.as_posix():
                continue
            rel = path.relative_to(ROOT).as_posix()
            for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                for parent, sub in AC_REF.findall(line):
                    loc = f"{rel}:{n}"
                    if sub:
                        crit = criteria.get(f"{parent}.{sub}")
                        if crit:
                            crit.exact.append(loc)
                        else:
                            orphans.append((f"{parent}.{sub}", loc))
                    elif parent in known_parents:
                        for crit in criteria.values():
                            if crit.ac_id.startswith(parent + "."):
                                crit.parent.append(loc)
                    else:
                        orphans.append((parent, loc))
    return orphans


async def agent_review(criteria):
    """Ask a read-only Claude agent to judge whether the referencing tests really prove each AC."""
    from claude_agent_sdk import AssistantMessage, ClaudeAgentOptions, ResultMessage, TextBlock, query

    covered = [c for c in criteria.values() if c.exact]
    if not covered:
        return "_No covered ACs to review yet._"
    listing = "\n".join(f"- {c.ac_id} ({c.spec}): {c.text}\n  tests: {', '.join(c.exact[:4])}" for c in covered)
    prompt = (
        "You are auditing AC traceability for the PolicyForge repository (Spec-Is-Truth rule).\n"
        "For each acceptance criterion below, open the referenced test locations and decide whether the test "
        "actually proves the criterion's Then clause: same literals (amounts, dates, codes), real assertions, "
        "fixed clock for dates, BigDecimal comparisons for money.\n"
        "Return ONLY a markdown table with columns: AC | verdict (PROVES / WEAK / DRIFT) | evidence (file:line) | note.\n"
        "DRIFT means the test asserts something different from the spec. Be terse.\n\n" + listing
    )
    options = ClaudeAgentOptions(
        cwd=str(ROOT),
        allowed_tools=["Read", "Glob", "Grep"],
        permission_mode="default",
        max_turns=40,
        model=os.environ.get("AC_AUDIT_MODEL") or None,
        system_prompt="You are a meticulous, read-only test auditor. Never modify files.",
    )
    chunks, result = [], None
    async for message in query(prompt=prompt, options=options):
        if isinstance(message, AssistantMessage):
            chunks.extend(b.text for b in message.content if isinstance(b, TextBlock))
        elif isinstance(message, ResultMessage):
            result = message.result
    return (result or "\n".join(chunks)).strip()


def render(criteria, orphans, review):
    rows = sorted(criteria.values(), key=lambda c: [int(x) for x in re.findall(r"\d+", c.ac_id)] + [c.ac_id[:2]])
    total = len(rows)
    covered = sum(c.status == "COVERED" for c in rows)
    missing = [c.ac_id for c in rows if c.status == "MISSING"]
    out = [
        f"# AC audit — {datetime.now():%Y-%m-%d %H:%M}",
        "",
        f"**Covered {covered} / {total} ACs ({(100 * covered // total) if total else 0}%)**"
        f" · parent-only {sum(c.status == 'PARENT-ONLY' for c in rows)} · missing {len(missing)}",
        "",
        "| AC | Spec | Status | Tests |",
        "|---|---|---|---|",
    ]
    for c in rows:
        refs = ", ".join((c.exact or c.parent)[:3]) or "—"
        out.append(f"| {c.ac_id} | {c.spec} | {c.status} | {refs} |")
    if orphans:
        out += ["", "## Orphan references (test → unknown AC id)", ""]
        out += [f"- `{ac}` at {loc}" for ac, loc in orphans]
    out += ["", "## Agent review (Claude Agent SDK)", "", review]
    return "\n".join(out) + "\n", missing


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--spec", action="append", help="limit to these spec files")
    parser.add_argument("--offline", action="store_true", help="skip the Claude agent review")
    args = parser.parse_args()

    specs = [ROOT / s for s in args.spec] if args.spec else sorted((ROOT / "specs").glob("*_spec.md"))
    criteria = collect_criteria(specs)
    orphans = collect_references(criteria)

    if args.offline:
        review = "_Skipped (--offline)._"
    else:
        try:
            review = asyncio.run(agent_review(criteria))
        except ImportError:
            review = "_Skipped: `claude-agent-sdk` not installed (pip install -r scripts/requirements.txt)._"

    report, missing = render(criteria, orphans, review)
    out_dir = ROOT / "specs" / "reviews"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / f"ac-audit-{datetime.now():%Y%m%d-%H%M}.md"
    out_file.write_text(report, encoding="utf-8")
    print(report.split("\n\n")[1])
    print(f"Report: {out_file.relative_to(ROOT).as_posix()}")
    if missing:
        print("MISSING: " + ", ".join(missing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
