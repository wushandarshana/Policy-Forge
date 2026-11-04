"""Tests for scripts/ci/claude_review.py, the required Claude Code review runner.

They drive the real entry point with a stub CLI (scripts/tests/fixtures/fake_claude.py, selected through
PF_CLAUDE_CLI), so no API is ever called and no real credential is read (plan P-15).
"""
import json
import re
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import (CI_DIR, FAKE_API_KEY, FAKE_CLAUDE, FAKE_LEAK_KEY, ROOT, SCHEMA_PATH, agent_files,  # noqa: E402
                             clean_env, commit_all, git, init_repo, load_script, run_python, write_bytes)

CLAUDE_REVIEW = CI_DIR / "claude_review.py"
FAKE_TOKEN_VALUE = "not-a-real-" + "token-value-0001"
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbd0f2d"
FRONTMATTER = re.compile(rb"\A---\r?\n(.*?)\r?\n---\r?\n(.*)\Z", re.DOTALL)
AGENT_NAMES = ("security-reviewer", "test-engineer")
ALLOWED_TOOLS = ["Read", "Grep", "Glob", "Bash(git diff *)", "Bash(git log *)", "Bash(git show *)",
                 "Bash(git ls-files *)", "Bash(grep *)", "Bash(rg *)"]
DISALLOWED_TOOLS = ["Edit", "Write", "NotebookEdit", "WebFetch", "WebSearch"]


def agent_parts(name):
    """(frontmatter model, body bytes) of a real agent file, computed independently of the runner."""
    data = agent_files()[f"{name}.md"]
    match = FRONTMATTER.match(data)
    model = re.search(r"^model:\s*(\S+)\s*$", match.group(1).decode("utf-8"), re.M).group(1)
    return model, match.group(2)


def values_after(argv, flag):
    """The tokens that follow `flag` up to the next `--option`."""
    start = argv.index(flag) + 1
    end = start
    while end < len(argv) and not argv[end].startswith("--"):
        end += 1
    return argv[start:end]


def read_calls(log):
    path = Path(log) / "calls.jsonl"
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def pass_calls(log):
    """The calls that carried a prompt (not `--version`), with their stdin bytes and child environment."""
    calls = [c for c in read_calls(log) if not c["version_call"]]
    for call in calls:
        call["stdin"] = (Path(log) / f"pass-{call['n']}.stdin").read_bytes()
        call["env"] = json.loads((Path(log) / f"pass-{call['n']}.env.json").read_text(encoding="utf-8"))
    return calls


class ReviewTestCase(unittest.TestCase):
    """A temporary repository with a base and a head commit, and a helper that runs the runner against it."""

    @classmethod
    def setUpClass(cls):
        cls.holder, cls.repo = init_repo()
        for name, data in agent_files().items():
            write_bytes(cls.repo, f".claude/agents/{name}", data)
        write_bytes(cls.repo, "CLAUDE.md", b"# Test repository\n")
        write_bytes(cls.repo, "src/a.txt", b"one\ntwo\n")
        cls.base = commit_all(cls.repo, "base")
        write_bytes(cls.repo, "src/a.txt", b"one\ntwo\nthree\n")
        write_bytes(cls.repo, "src/b.txt", b"new file\n")
        cls.head = commit_all(cls.repo, "head")

    @classmethod
    def tearDownClass(cls):
        cls.holder.cleanup()

    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="pf-ci-review-")
        self.addCleanup(work.cleanup)
        self.out = Path(work.name) / "out"
        self.log = Path(work.name) / "log"

    def run_review(self, security="approve", test="approve", env=None, version=None, key=FAKE_API_KEY,
                   base=None, head=None, event="pull_request", repo_flag=True):
        values = {
            "PF_CLAUDE_CLI": str(FAKE_CLAUDE),
            "FAKE_CLAUDE_LOG": str(self.log),
            "FAKE_CLAUDE_SECURITY": security,
            "FAKE_CLAUDE_TEST": test,
            "GITHUB_TOKEN": FAKE_TOKEN_VALUE,
            "GH_TOKEN": FAKE_TOKEN_VALUE,
            "CI_JOB_TOKEN": FAKE_TOKEN_VALUE,
            "PF_GITLAB_REVIEW_TOKEN": FAKE_TOKEN_VALUE,
        }
        if key is not None:
            values["ANTHROPIC_API_KEY"] = key
        if version is not None:
            values["FAKE_CLAUDE_VERSION"] = version
        values.update(env or {})
        args = [CLAUDE_REVIEW, "run", "--base", base or self.base, "--head", head or self.head,
                "--event", event, "--out", self.out]
        if repo_flag:
            args += ["--repo", self.repo]
        proc = run_python(args, env=clean_env(**values), cwd=self.repo)
        return proc

    def report(self):
        path = self.out / "claude-review.md"
        self.assertTrue(path.exists(), f"claude-review.md was not written to {self.out}")
        return path.read_text(encoding="utf-8")

    def all_outputs(self, proc):
        """stdout, stderr and the text of every file the run wrote."""
        texts = [proc.stdout_text, proc.stderr_text]
        if self.out.exists():
            texts += [p.read_text(encoding="utf-8", errors="replace") for p in self.out.rglob("*") if p.is_file()]
        return "\n".join(texts)


class ReviewVerdictTests(ReviewTestCase):
    def test_two_approve_passes_exit_zero(self):
        """NFR-09.4: two APPROVE passes exit 0, write both verdicts and a report ending in GATE: PASS."""
        proc = self.run_review()
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        report = self.report()
        self.assertEqual(report.rstrip().splitlines()[-1], "GATE: PASS (0 BLOCKER)")
        self.assertEqual([line for line in report.splitlines() if line.startswith("VERDICT:")],
                         ["VERDICT: APPROVE", "VERDICT: APPROVE"])
        for name in AGENT_NAMES:
            verdict = json.loads((self.out / f"{name}.json").read_text(encoding="utf-8"))
            self.assertEqual(verdict["agent"], name)
            self.assertEqual(verdict["verdict"], "APPROVE")
        self.assertEqual(len(pass_calls(self.log)), 2)

    def test_one_blocker_exits_one(self):
        """NFR-09.4: a single BLOCKER finding fails the job with exit 1 and names the finding in the report."""
        proc = self.run_review(security="blocker")
        self.assertEqual(proc.returncode, 1, proc.stderr_text)
        report = self.report()
        self.assertEqual(report.rstrip().splitlines()[-1], "GATE: FAIL (1 BLOCKER)")
        self.assertIn("SEC-BLOCKER-1", report)
        self.assertIn("Fake blocker finding 1 from security-reviewer", report)

    def test_blockers_from_both_passes_are_counted(self):
        """NFR-09.4: BLOCKER findings of both passes count towards the gate."""
        proc = self.run_review(security="blocker", test="blocker")
        self.assertEqual(proc.returncode, 1, proc.stderr_text)
        self.assertEqual(self.report().rstrip().splitlines()[-1], "GATE: FAIL (2 BLOCKER)")

    def test_major_and_minor_findings_alone_exit_zero_and_are_published(self):
        """NFR-09.4: MAJOR and MINOR findings do not fail the job, and every one appears in claude-review.md."""
        proc = self.run_review(security="major_minor", test="minor_only")
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        report = self.report()
        for finding_id in ("SEC-MAJOR-1", "SEC-MINOR-1", "TEST-MINOR-1"):
            self.assertIn(finding_id, report)
        self.assertEqual(report.rstrip().splitlines()[-1], "GATE: PASS (0 BLOCKER)")

    def test_changes_requested_without_blocker_exits_zero_with_a_warning(self):
        """NFR-09.4: CHANGES_REQUESTED with no BLOCKER passes (plan G-4) but warns in the log and the report."""
        proc = self.run_review(security="changes_no_blocker")
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertIn("WARNING", proc.stderr_text)
        self.assertIn("CHANGES_REQUESTED", proc.stderr_text)
        self.assertIn("WARNING", self.report())

    def test_no_structured_output_and_no_fenced_json_fails(self):
        """NFR-09.4: a pass with neither structured_output nor a fenced JSON block fails closed (exit 2)."""
        proc = self.run_review(security="no_output")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)
        self.assertTrue((self.out / "claude-review.md").exists())
        self.assertIn("GATE: FAIL", self.report().rstrip().splitlines()[-1])

    def test_last_fenced_json_block_is_used_when_structured_output_is_absent(self):
        """NFR-09.4: without structured_output the last fenced json block of the result is the verdict."""
        proc = self.run_review(security="fenced")
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        verdict = json.loads((self.out / "security-reviewer.json").read_text(encoding="utf-8"))
        self.assertEqual(verdict["verdict"], "APPROVE")
        self.assertNotIn("decoy", verdict)

    def test_schema_invalid_output_fails(self):
        """NFR-09.4: output that fails the verdict schema (a verdict outside the enum) exits 2."""
        proc = self.run_review(test="invalid_schema")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)

    def test_missing_required_key_in_the_verdict_fails(self):
        """NFR-09.4: a verdict that lacks a required key (summary) exits 2."""
        proc = self.run_review(security="missing_key")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)

    def test_cli_reported_error_fails(self):
        """NFR-09.4: a result flagged is_error (for example a hit turn cap) has no verdict, so the job fails."""
        proc = self.run_review(security="is_error")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)

    def test_cli_exit_status_one_fails(self):
        """NFR-09.4: a CLI that exits with status 1 fails the job with a non-zero exit."""
        proc = self.run_review(security="exit1")
        self.assertNotEqual(proc.returncode, 0)
        self.assertEqual(proc.returncode, 2, proc.stderr_text)

    def test_a_failed_pass_stops_the_review_before_the_second_pass(self):
        """NFR-09.4: when the first pass fails the second is not started, so no more API money is spent."""
        self.run_review(security="exit1")
        self.assertEqual(len(pass_calls(self.log)), 1)

    def test_version_other_than_the_pinned_one_fails_before_any_pass(self):
        """NFR-09.4: a CLI that is not 2.1.311 fails closed and no prompt is ever sent to it."""
        proc = self.run_review(version="2.1.318")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)
        self.assertIn("2.1.311", proc.stderr_text)
        self.assertEqual(pass_calls(self.log), [])

    def test_version_match_is_exact(self):
        """NFR-09.4: 2.1.3110 and 12.1.311 do not satisfy the 2.1.311 pin."""
        for fake in ("2.1.3110", "12.1.311"):
            with self.subTest(version=fake):
                proc = self.run_review(version=fake)
                self.assertEqual(proc.returncode, 2, proc.stderr_text)

    def test_missing_api_key_fails_without_calling_the_cli(self):
        """NFR-09.4: with ANTHROPIC_API_KEY unset the run exits 2 with a fixed message and never starts the CLI."""
        proc = self.run_review(key=None)
        self.assertEqual(proc.returncode, 2, proc.stderr_text)
        self.assertIn("ANTHROPIC_API_KEY is not set", proc.stderr_text)
        self.assertEqual(read_calls(self.log), [])

    def test_blank_api_key_counts_as_missing(self):
        """NFR-09.4: an empty ANTHROPIC_API_KEY (an unset GitHub secret renders as empty) is a missing key."""
        proc = self.run_review(key="   ")
        self.assertEqual(proc.returncode, 2, proc.stderr_text)
        self.assertEqual(read_calls(self.log), [])

    def test_empty_diff_passes_without_calling_the_cli(self):
        """NFR-09.4: an empty diff writes a no-changes report, exits 0 and never starts the CLI."""
        proc = self.run_review(base=self.head, head=self.head)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertEqual(read_calls(self.log), [])
        report = self.report()
        self.assertIn("No changes", report)
        self.assertEqual(report.rstrip().splitlines()[-1], "GATE: PASS (0 BLOCKER)")

    def test_repository_defaults_to_the_working_directory(self):
        """NFR-09.4: without --repo the runner reviews the repository it is started in, as the CI jobs do."""
        proc = self.run_review(repo_flag=False)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertEqual(len(pass_calls(self.log)), 2)


class ReviewPromptTests(ReviewTestCase):
    def test_each_pass_receives_the_agent_body_byte_for_byte(self):
        """NFR-09.4: each pass sends the agent file body, unchanged, on stdin and never on the command line."""
        proc = self.run_review()
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        calls = pass_calls(self.log)
        self.assertEqual(len(calls), 2)
        for call, name in zip(calls, AGENT_NAMES):
            _, body = agent_parts(name)
            self.assertIn(body, call["stdin"], f"body of {name} is not in the prompt byte for byte")
            self.assertNotIn(body[:60].decode("utf-8", "replace"), " ".join(call["argv"]))
        self.assertNotIn(agent_parts("test-engineer")[1], calls[0]["stdin"])
        self.assertNotIn(agent_parts("security-reviewer")[1], calls[1]["stdin"])

    def test_preamble_states_the_range_the_rules_and_the_changed_files(self):
        """NFR-09.4: the preamble names the diff range and files, says read-only, and marks the diff untrusted."""
        self.run_review()
        stdin = pass_calls(self.log)[0]["stdin"].decode("utf-8")
        preamble = stdin.split(agent_parts("security-reviewer")[1].decode("utf-8"))[0]
        self.assertIn(f"{self.base}..{self.head}", preamble)
        for fragment in ("read-only", "untrusted", "CLAUDE.md", "already passed", "src/a.txt", "src/b.txt"):
            self.assertIn(fragment, preamble)

    def test_command_line_matches_the_contract(self):
        """NFR-09.4: the CLI argv has the exact flag set, the frontmatter model and both caps for each pass."""
        proc = self.run_review()
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8")) if SCHEMA_PATH.exists() else None
        self.assertIsNotNone(schema, "review-verdict.schema.json is missing")
        calls = pass_calls(self.log)
        self.assertEqual(len(calls), 2)
        for call, name in zip(calls, AGENT_NAMES):
            argv = call["argv"]
            with self.subTest(agent=name):
                for flag in ("--bare", "-p", "--no-session-persistence", "--strict-mcp-config"):
                    self.assertIn(flag, argv)
                self.assertEqual(values_after(argv, "--output-format"), ["json"])
                self.assertEqual(json.loads(values_after(argv, "--json-schema")[0]), schema)
                self.assertEqual(values_after(argv, "--model"), [agent_parts(name)[0]])
                self.assertEqual(values_after(argv, "--tools"), ["Read,Grep,Glob,Bash"])
                self.assertEqual(values_after(argv, "--allowedTools"), ALLOWED_TOOLS)
                self.assertEqual(values_after(argv, "--disallowedTools"), DISALLOWED_TOOLS)
                self.assertEqual(values_after(argv, "--permission-mode"), ["dontAsk"])
                self.assertEqual(values_after(argv, "--max-turns"), ["40"])
                self.assertEqual(values_after(argv, "--max-budget-usd"), ["5.00"])

    def test_models_come_from_the_agent_frontmatter(self):
        """NFR-09.4: security-reviewer runs on its frontmatter model and test-engineer on its own."""
        self.run_review()
        models = [values_after(c["argv"], "--model")[0] for c in pass_calls(self.log)]
        self.assertEqual(models, [agent_parts("security-reviewer")[0], agent_parts("test-engineer")[0]])
        self.assertNotEqual(models[0], models[1])

    def test_permissions_are_never_skipped(self):
        """NFR-09.4: no pass uses --dangerously-skip-permissions or a bypass permission mode."""
        self.run_review()
        self.assertEqual(len(pass_calls(self.log)), 2)
        for call in pass_calls(self.log):
            joined = " ".join(call["argv"])
            self.assertNotIn("--dangerously-skip-permissions", joined)
            self.assertNotIn("--allow-dangerously-skip-permissions", joined)
            self.assertNotIn("bypassPermissions", joined)

    def test_caps_can_be_set_through_the_environment(self):
        """NFR-09.4: PF_REVIEW_MAX_TURNS and PF_REVIEW_MAX_BUDGET_USD set the caps of every pass."""
        self.run_review(env={"PF_REVIEW_MAX_TURNS": "12", "PF_REVIEW_MAX_BUDGET_USD": "2.50"})
        self.assertEqual(len(pass_calls(self.log)), 2)
        for call in pass_calls(self.log):
            self.assertEqual(values_after(call["argv"], "--max-turns"), ["12"])
            self.assertEqual(values_after(call["argv"], "--max-budget-usd"), ["2.50"])

    def test_blank_cap_variables_use_the_defaults(self):
        """NFR-09.4: an unset repository variable reaches the job as an empty string and means the default."""
        self.run_review(env={"PF_REVIEW_MAX_TURNS": "", "PF_REVIEW_MAX_BUDGET_USD": ""})
        self.assertEqual(len(pass_calls(self.log)), 2)
        for call in pass_calls(self.log):
            self.assertEqual(values_after(call["argv"], "--max-turns"), ["40"])
            self.assertEqual(values_after(call["argv"], "--max-budget-usd"), ["5.00"])

    def test_malformed_caps_fail_before_the_cli_is_started(self):
        """NFR-09.4: a cap that is not a number fails closed instead of being passed to the CLI."""
        for name, value in (("PF_REVIEW_MAX_BUDGET_USD", "5; rm -rf /"), ("PF_REVIEW_MAX_TURNS", "forty"),
                            ("PF_REVIEW_MAX_BUDGET_USD", "-1")):
            with self.subTest(name=name, value=value):
                proc = self.run_review(env={name: value})
                self.assertEqual(proc.returncode, 2, proc.stderr_text)
                self.assertEqual(read_calls(self.log), [])

    def test_only_the_test_engineer_pass_gets_the_offline_ac_audit_summary(self):
        """NFR-09.4: the test-engineer prompt carries the offline AC-audit summary line, the other does not."""
        write_bytes(self.repo, "scripts/ac_audit.py", (ROOT / "scripts" / "ac_audit.py").read_bytes())
        try:
            self.run_review()
        finally:
            (self.repo / "scripts" / "ac_audit.py").unlink()
            (self.repo / "scripts").rmdir()
        security, test_engineer = (c["stdin"].decode("utf-8") for c in pass_calls(self.log))
        self.assertRegex(test_engineer, r"AC audit \(offline\): \*\*Covered \d+ / \d+ ACs")
        self.assertNotIn("AC audit (offline)", security)


class DiffRangeTests(unittest.TestCase):
    """Plan P-7: how the reviewed range is derived for pull requests, pushes and manual runs."""

    @classmethod
    def setUpClass(cls):
        cls.review = load_script("claude_review")
        cls.holder, cls.repo = init_repo()
        repo = cls.repo
        write_bytes(repo, "f.txt", b"m0\n")
        cls.m0 = commit_all(repo, "m0")
        write_bytes(repo, "f.txt", b"m1\n")
        cls.m1 = commit_all(repo, "m1")
        git(repo, "switch", "-q", "-c", "feat")
        write_bytes(repo, "feat1.txt", b"1\n")
        cls.f1 = commit_all(repo, "f1")
        write_bytes(repo, "feat2.txt", b"2\n")
        cls.f2 = commit_all(repo, "f2")
        git(repo, "switch", "-q", "work")
        write_bytes(repo, "f.txt", b"m2\n")
        cls.m2 = commit_all(repo, "m2")
        git(repo, "merge", "-q", "--no-ff", "-m", "merge feat", "feat")
        cls.merge = git(repo, "rev-parse", "HEAD")
        git(repo, "switch", "-q", "-c", "other", cls.m0)
        write_bytes(repo, "other.txt", b"o\n")
        cls.o1 = commit_all(repo, "o1")
        git(repo, "switch", "-q", "work")

    @classmethod
    def tearDownClass(cls):
        cls.holder.cleanup()

    def resolve(self, event, base, head):
        return self.review.resolve_range(event, base, head, self.repo)

    def test_pull_request_range_starts_at_the_merge_base(self):
        """NFR-09.4: a pull request reviews merge-base(base, head)..head, not the moved base tip."""
        self.assertEqual(self.resolve("pull_request", self.m2, self.f2), (self.m1, self.f2))

    def test_merge_request_is_an_alias_of_pull_request(self):
        """NFR-09.4: a GitLab merge request uses the same merge-base rule."""
        self.assertEqual(self.resolve("merge_request", self.m2, self.f2), (self.m1, self.f2))

    def test_push_range_is_before_to_after(self):
        """NFR-09.4: a push whose before is an ancestor of after reviews before..after."""
        self.assertEqual(self.resolve("push", self.m1, self.m2), (self.m1, self.m2))
        self.assertEqual(self.resolve("push", self.m2, self.merge), (self.m2, self.merge))

    def test_push_with_all_zero_before_falls_back_to_the_first_parent(self):
        """NFR-09.4: a push that creates a branch (before is all zeros) reviews after^1..after."""
        self.assertEqual(self.resolve("push", "0" * 40, self.merge), (self.m2, self.merge))
        self.assertEqual(self.resolve("push", "", self.m2), (self.m1, self.m2))

    def test_push_with_a_non_ancestor_before_falls_back_to_the_first_parent(self):
        """NFR-09.4: after a force push the old tip is not an ancestor, so the range is after^1..after."""
        self.assertEqual(self.resolve("push", self.o1, self.m2), (self.m1, self.m2))

    def test_push_with_an_unknown_before_falls_back_to_the_first_parent(self):
        """NFR-09.4: a before commit that the checkout does not have falls back to the first parent."""
        self.assertEqual(self.resolve("push", "1" * 40, self.merge), (self.m2, self.merge))

    def test_root_commit_is_compared_with_the_empty_tree(self):
        """NFR-09.4: a commit without a parent is reviewed against the empty tree."""
        self.assertEqual(self.resolve("push", "0" * 40, self.m0), (EMPTY_TREE, self.m0))

    def test_auto_event_uses_the_range_when_base_is_an_ancestor_else_the_merge_base(self):
        """NFR-09.4: with no event given, an ancestor base is used as is and any other base gives the merge base."""
        self.assertEqual(self.resolve("auto", self.m1, self.m2), (self.m1, self.m2))
        self.assertEqual(self.resolve("auto", self.o1, self.m2), (self.m0, self.m2))
        self.assertEqual(self.resolve("auto", "0" * 40, self.m2), (self.m1, self.m2))

    def test_manual_run_uses_the_merge_base_with_origin_dev(self):
        """NFR-09.4: a manual run reviews merge-base(origin/dev, HEAD)..HEAD."""
        git(self.repo, "update-ref", "refs/remotes/origin/dev", self.m2)
        try:
            self.assertEqual(self.resolve("manual", "", self.f2), (self.m1, self.f2))
        finally:
            git(self.repo, "update-ref", "-d", "refs/remotes/origin/dev")

    def test_manual_run_on_dev_or_main_reviews_the_head_commit_alone(self):
        """NFR-09.4: when HEAD is origin/dev or origin/main a manual run reviews HEAD^1..HEAD."""
        for ref in ("refs/remotes/origin/dev", "refs/remotes/origin/main"):
            with self.subTest(ref=ref):
                git(self.repo, "update-ref", ref, self.merge)
                try:
                    self.assertEqual(self.resolve("manual", "", self.merge), (self.m2, self.merge))
                finally:
                    git(self.repo, "update-ref", "-d", ref)

    def test_manual_run_without_origin_dev_falls_back_to_the_first_parent(self):
        """NFR-09.4: with no origin/dev to compare with, a manual run reviews HEAD^1..HEAD."""
        self.assertEqual(self.resolve("manual", "", self.f2), (self.f1, self.f2))

    def test_unknown_head_is_an_error(self):
        """NFR-09.4: a head that is not a commit stops the run instead of reviewing nothing."""
        with self.assertRaises(self.review.ReviewError):
            self.resolve("push", self.m1, "2" * 40)


class VerdictSchemaTests(unittest.TestCase):
    """Plan C6: the verdict schema file and the standard-library validator that enforces it."""

    @classmethod
    def setUpClass(cls):
        cls.review = load_script("claude_review")
        cls.schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8")) if SCHEMA_PATH.exists() else {}

    def valid(self):
        return {
            "agent": "security-reviewer",
            "verdict": "CHANGES_REQUESTED",
            "checklist": [{"item": "Injection", "status": "PASS", "evidence": "none found"}],
            "findings": [{"id": "SEC-1", "severity": "MAJOR", "title": "t", "file": "a.java", "line": 12,
                          "detail": "d"}],
            "summary": "s",
        }

    def errors(self, instance):
        return self.review.validate(instance, self.schema)

    def test_schema_file_defines_the_c6_shape(self):
        """NFR-09.4: the schema requires every key, allows no others, and fixes the enums of C6."""
        schema = self.schema
        self.assertEqual(schema.get("type"), "object")
        self.assertEqual(sorted(schema.get("required", [])), ["agent", "checklist", "findings", "summary", "verdict"])
        self.assertIs(schema.get("additionalProperties"), False)
        props = schema["properties"]
        self.assertEqual(props["agent"]["enum"], ["security-reviewer", "test-engineer"])
        self.assertEqual(props["verdict"]["enum"], ["APPROVE", "CHANGES_REQUESTED"])
        item = props["checklist"]["items"]
        self.assertEqual(sorted(item["required"]), ["evidence", "item", "status"])
        self.assertEqual(item["properties"]["status"]["enum"], ["PASS", "FAIL", "N/A"])
        finding = props["findings"]["items"]
        self.assertEqual(sorted(finding["required"]), ["detail", "file", "id", "line", "severity", "title"])
        self.assertEqual(finding["properties"]["severity"]["enum"], ["BLOCKER", "MAJOR", "MINOR"])
        for node in (item, finding):
            self.assertIs(node.get("additionalProperties"), False)

    def test_valid_verdict_has_no_errors(self):
        """NFR-09.4: a complete verdict validates, including a null line number."""
        self.assertEqual(self.errors(self.valid()), [])
        verdict = self.valid()
        verdict["findings"][0]["line"] = None
        self.assertEqual(self.errors(verdict), [])

    def test_missing_extra_and_mistyped_keys_are_rejected(self):
        """NFR-09.4: a missing key, an extra key, a bad enum and a wrong type each produce an error."""
        cases = {}
        missing = self.valid()
        del missing["summary"]
        cases["missing summary"] = missing
        extra = self.valid()
        extra["extra"] = 1
        cases["extra top-level key"] = extra
        bad_enum = self.valid()
        bad_enum["verdict"] = "MAYBE"
        cases["verdict outside the enum"] = bad_enum
        bad_severity = self.valid()
        bad_severity["findings"][0]["severity"] = "CRITICAL"
        cases["severity outside the enum"] = bad_severity
        text_line = self.valid()
        text_line["findings"][0]["line"] = "12"
        cases["line as a string"] = text_line
        bool_line = self.valid()
        bool_line["findings"][0]["line"] = True
        cases["line as a boolean"] = bool_line
        extra_finding_key = self.valid()
        extra_finding_key["findings"][0]["confidence"] = 0.9
        cases["extra finding key"] = extra_finding_key
        not_a_list = self.valid()
        not_a_list["checklist"] = {}
        cases["checklist as an object"] = not_a_list
        for label, instance in cases.items():
            with self.subTest(case=label):
                self.assertTrue(self.errors(instance), f"{label} was accepted")

    def test_non_object_verdicts_are_rejected(self):
        """NFR-09.4: a list, a string or null is never a verdict."""
        for instance in ([], "APPROVE", None, 3):
            with self.subTest(instance=instance):
                self.assertTrue(self.errors(instance))


class _FakeApi(ThreadingHTTPServer):
    """A local stand-in for the GitHub and GitLab REST APIs that records every request."""

    def __init__(self, responder):
        super().__init__(("127.0.0.1", 0), _FakeApiHandler)
        self.responder = responder
        self.requests = []

    @property
    def url(self):
        return f"http://127.0.0.1:{self.server_address[1]}"


class _FakeApiHandler(BaseHTTPRequestHandler):
    def _handle(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        self.server.requests.append({"method": self.command, "path": self.path, "headers": dict(self.headers),
                                     "body": body.decode("utf-8")})
        status, payload = self.server.responder(self.command, self.path)
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    do_GET = do_POST = do_PATCH = do_PUT = _handle

    def log_message(self, *args):  # keep the test output quiet
        pass


class CommentTests(unittest.TestCase):
    """`claude_review.py comment` creates or updates one marked PR or MR comment through the platform API."""

    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="pf-ci-comment-")
        self.addCleanup(work.cleanup)
        self.work = Path(work.name)
        self.report = self.work / "claude-review.md"
        self.report.write_text("# Claude Code review\n\nVERDICT: APPROVE\nGATE: PASS (0 BLOCKER)\n", encoding="utf-8")
        event = self.work / "event.json"
        event.write_text(json.dumps({"pull_request": {"number": 7}}), encoding="utf-8")
        self.event = event

    def serve(self, responder):
        server = _FakeApi(responder)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 5)
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def github(self, server, **overrides):
        env = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(self.event),
               "GITHUB_REPOSITORY": "octo/demo", "GITHUB_API_URL": server.url, "GH_TOKEN": FAKE_TOKEN_VALUE}
        env.update(overrides)
        env = {k: v for k, v in env.items() if v is not None}
        return run_python([CLAUDE_REVIEW, "comment", "--platform", "github", "--report", self.report],
                          env=clean_env(**env))

    def gitlab(self, server, **overrides):
        env = {"CI_API_V4_URL": server.url + "/api/v4", "CI_PROJECT_ID": "42", "CI_MERGE_REQUEST_IID": "9",
               "PF_GITLAB_REVIEW_TOKEN": FAKE_TOKEN_VALUE}
        env.update(overrides)
        env = {k: v for k, v in env.items() if v is not None}
        return run_python([CLAUDE_REVIEW, "comment", "--platform", "gitlab", "--report", self.report],
                          env=clean_env(**env))

    def test_github_creates_one_comment_carrying_the_marker_and_the_report(self):
        """NFR-09.4: on a pull_request event the report is posted as a new PR comment with the marker."""
        def responder(method, path):
            return (201, {"id": 1}) if method == "POST" else (200, [])
        server = self.serve(responder)
        proc = self.github(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        posts = [r for r in server.requests if r["method"] == "POST"]
        self.assertEqual(len(posts), 1)
        self.assertEqual(posts[0]["path"], "/repos/octo/demo/issues/7/comments")
        body = json.loads(posts[0]["body"])["body"]
        self.assertIn("<!-- policyforge-claude-review -->", body)
        self.assertIn("GATE: PASS (0 BLOCKER)", body)
        self.assertEqual(posts[0]["headers"].get("Authorization"), f"Bearer {FAKE_TOKEN_VALUE}")

    def test_github_updates_the_existing_marked_comment(self):
        """NFR-09.4: a second run edits the comment that carries the marker instead of adding another."""
        def responder(method, path):
            if method == "GET":
                return 200, [{"id": 56, "body": "someone else"},
                             {"id": 55, "body": "old text <!-- policyforge-claude-review -->"}]
            return 200, {"id": 55}
        server = self.serve(responder)
        proc = self.github(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        writes = [r for r in server.requests if r["method"] != "GET"]
        self.assertEqual([(r["method"], r["path"]) for r in writes],
                         [("PATCH", "/repos/octo/demo/issues/comments/55")])

    def test_github_does_nothing_outside_a_pull_request_or_without_a_token(self):
        """NFR-09.4: on a push event or with no GH_TOKEN the comment step makes no request and exits 0."""
        server = self.serve(lambda method, path: (200, []))
        for label, overrides in (("push event", {"GITHUB_EVENT_NAME": "push"}), ("no token", {"GH_TOKEN": None})):
            with self.subTest(case=label):
                proc = self.github(server, **overrides)
                self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertEqual(server.requests, [])

    def test_github_api_failure_is_a_warning_and_never_prints_the_token(self):
        """NFR-09.4: a failed comment call warns and exits 0 (the gate is the job, not the comment)."""
        server = self.serve(lambda method, path: (403, {"message": "Resource not accessible by integration"}))
        proc = self.github(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertIn("403", proc.stderr_text)
        self.assertNotIn(FAKE_TOKEN_VALUE, proc.stdout_text + proc.stderr_text)

    def test_gitlab_creates_a_merge_request_note_with_the_private_token_header(self):
        """NFR-09.4: on GitLab the report is posted as an MR note, authenticated with PRIVATE-TOKEN."""
        def responder(method, path):
            return (201, {"id": 3}) if method == "POST" else (200, [])
        server = self.serve(responder)
        proc = self.gitlab(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        posts = [r for r in server.requests if r["method"] == "POST"]
        self.assertEqual([r["path"] for r in posts], ["/api/v4/projects/42/merge_requests/9/notes"])
        self.assertEqual(posts[0]["headers"].get("PRIVATE-TOKEN"), FAKE_TOKEN_VALUE)
        self.assertIn("<!-- policyforge-claude-review -->", json.loads(posts[0]["body"])["body"])
        self.assertNotIn(FAKE_TOKEN_VALUE, proc.stdout_text + proc.stderr_text)

    def test_gitlab_updates_the_existing_marked_note(self):
        """NFR-09.4: a rerun on GitLab edits the note with the marker."""
        def responder(method, path):
            if method == "GET":
                return 200, [{"id": 77, "body": "old <!-- policyforge-claude-review -->"}]
            return 200, {"id": 77}
        server = self.serve(responder)
        proc = self.gitlab(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        writes = [r for r in server.requests if r["method"] != "GET"]
        self.assertEqual([(r["method"], r["path"]) for r in writes],
                         [("PUT", "/api/v4/projects/42/merge_requests/9/notes/77")])

    def test_gitlab_does_nothing_without_a_token_or_a_merge_request(self):
        """NFR-09.4: without PF_GITLAB_REVIEW_TOKEN or CI_MERGE_REQUEST_IID nothing is posted and the exit is 0."""
        server = self.serve(lambda method, path: (200, []))
        for label, overrides in (("no token", {"PF_GITLAB_REVIEW_TOKEN": None}),
                                 ("no merge request", {"CI_MERGE_REQUEST_IID": None})):
            with self.subTest(case=label):
                proc = self.gitlab(server, **overrides)
                self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertEqual(server.requests, [])

    def test_a_key_in_the_report_is_redacted_before_it_is_posted(self):
        """NFR-09.3: a key-shaped string in the report never reaches the platform API."""
        self.report.write_text("leaked " + FAKE_LEAK_KEY + "\nGATE: PASS (0 BLOCKER)\n", encoding="utf-8")
        server = self.serve(lambda method, path: (201, {"id": 1}) if method == "POST" else (200, []))
        proc = self.github(server)
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        sent = " ".join(r["body"] for r in server.requests)
        self.assertNotIn(FAKE_LEAK_KEY, sent)
        self.assertIn("<redacted-api-key>", sent)


class SecretHandlingTests(ReviewTestCase):
    """NFR-09.3: the review never hands tokens to the CLI and never lets a key reach an output."""

    def test_cli_child_environment_lacks_the_platform_tokens(self):
        """NFR-09.3: the CLI child environment has none of GITHUB_TOKEN, GH_TOKEN, CI_JOB_TOKEN, GitLab token."""
        proc = self.run_review()
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        calls = pass_calls(self.log)
        self.assertEqual(len(calls), 2)
        for call in calls:
            names = {name.upper() for name in call["env"]}
            for token in ("GITHUB_TOKEN", "GH_TOKEN", "CI_JOB_TOKEN", "PF_GITLAB_REVIEW_TOKEN"):
                self.assertNotIn(token, names)
            self.assertEqual(call["env"].get("CLAUDE_CODE_SUBPROCESS_ENV_SCRUB"), "1")
            self.assertEqual(call["env"].get("ANTHROPIC_API_KEY"), FAKE_API_KEY)
            self.assertNotIn(FAKE_TOKEN_VALUE, call["env"].values())

    def test_fake_key_never_appears_in_stdout_stderr_or_any_output_file(self):
        """NFR-09.3: after a normal run the API key is in no stream and no file the run produced."""
        proc = self.run_review(security="major_minor")
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        self.assertIn("SEC-MAJOR-1", self.report())
        self.assertNotIn(FAKE_API_KEY, self.all_outputs(proc))

    def test_fake_key_never_appears_when_the_run_fails(self):
        """NFR-09.3: the failure paths (a CLI error, a missing verdict) do not print the key either."""
        for security in ("exit1", "no_output", "invalid_schema"):
            with self.subTest(scenario=security):
                proc = self.run_review(security=security)
                self.assertEqual(proc.returncode, 2)
                self.assertNotIn(FAKE_API_KEY, self.all_outputs(proc))

    def test_key_shaped_string_echoed_by_the_cli_is_redacted_in_every_output(self):
        """NFR-09.3: a key-shaped string that the CLI echoes is replaced by a marker in stdout, stderr and files."""
        proc = self.run_review(security="leak_key", env={"FAKE_CLAUDE_LEAK": FAKE_LEAK_KEY})
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        outputs = self.all_outputs(proc)
        self.assertNotIn(FAKE_LEAK_KEY, outputs)
        self.assertIn("<redacted-api-key>", self.report())
        self.assertIn("<redacted-api-key>", (self.out / "security-reviewer.json").read_text(encoding="utf-8"))

    def test_the_exact_key_value_is_redacted_even_when_it_is_not_key_shaped(self):
        """NFR-09.3: the literal ANTHROPIC_API_KEY value is redacted wherever the CLI echoes it."""
        odd_key = "plainvalue-" + "0123456789abcdef"
        proc = self.run_review(security="leak_key", key=odd_key,
                               env={"FAKE_CLAUDE_LEAK": FAKE_LEAK_KEY, "FAKE_CLAUDE_ECHO_ENV_KEY": "1"})
        self.assertEqual(proc.returncode, 0, proc.stderr_text)
        outputs = self.all_outputs(proc)
        self.assertIn("<redacted-api-key>", self.report())
        self.assertNotIn(odd_key, outputs)
        self.assertNotIn(FAKE_LEAK_KEY, outputs)

    def test_child_environment_helper_removes_tokens_in_any_case(self):
        """NFR-09.3: child_environment drops the token variables and sets the scrub flag."""
        review = load_script("claude_review")
        env = review.child_environment({"github_token": "x", "GH_TOKEN": "y", "CI_JOB_TOKEN": "z",
                                        "PF_GITLAB_REVIEW_TOKEN": "w", "ANTHROPIC_API_KEY": "k", "PATH": "p"})
        self.assertEqual(sorted(env), ["ANTHROPIC_API_KEY", "CLAUDE_CODE_SUBPROCESS_ENV_SCRUB", "PATH"])
        self.assertEqual(env["CLAUDE_CODE_SUBPROCESS_ENV_SCRUB"], "1")


if __name__ == "__main__":
    unittest.main()
