"""Tests for the Anthropic API key pattern of scripts/redact_evidence.py (and that its old patterns still work)."""
import base64
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import FAKE_API_KEY, ROOT, SCRIPTS, clean_env, run_python  # noqa: E402

REDACT = SCRIPTS / "redact_evidence.py"


class RedactApiKeyTests(unittest.TestCase):
    def setUp(self):
        work = tempfile.TemporaryDirectory(prefix="pf-ci-redact-")
        self.addCleanup(work.cleanup)
        self.work = Path(work.name)

    def evidence(self, text, name="evidence.log"):
        path = self.work / name
        path.write_text(text, encoding="utf-8", newline="")
        return path

    def test_redact_replaces_an_anthropic_key_with_a_marker(self):
        """NFR-09.3: redact_evidence.py rewrites a sk-ant- key as <redacted-api-key> and exits 0."""
        path = self.evidence(f"before {FAKE_API_KEY} after\n")
        proc = run_python([REDACT, path], env=clean_env())
        self.assertEqual(proc.returncode, 0, proc.stdout_text + proc.stderr_text)
        self.assertEqual(path.read_text(encoding="utf-8"), "before <redacted-api-key> after\n")
        self.assertNotIn(FAKE_API_KEY, path.read_text(encoding="utf-8"))

    def test_check_reports_the_key_and_exits_one_without_changing_the_file(self):
        """NFR-09.3: --check finds the key, exits 1 and leaves the file untouched."""
        path = self.evidence(f"key={FAKE_API_KEY}\n")
        proc = run_python([REDACT, "--check", path], env=clean_env())
        self.assertEqual(proc.returncode, 1, proc.stdout_text)
        self.assertIn("found", proc.stdout_text)
        self.assertIn(path.name, proc.stdout_text)
        self.assertIn(FAKE_API_KEY, path.read_text(encoding="utf-8"))
        self.assertNotIn(FAKE_API_KEY, proc.stdout_text + proc.stderr_text)

    def test_every_key_in_a_file_is_counted(self):
        """NFR-09.3: two keys in one file are both found and counted."""
        path = self.evidence(f"{FAKE_API_KEY}\nsecond {FAKE_API_KEY}-extra_part\n")
        proc = run_python([REDACT, "--check", path], env=clean_env())
        self.assertEqual(proc.returncode, 1)
        self.assertIn("  2  ", proc.stdout_text)

    def test_keys_in_json_and_markdown_reports_are_covered(self):
        """NFR-09.3: the review outputs (.json verdicts and the .md report) are scanned as well as .log files."""
        for name in ("security-reviewer.json", "claude-review.md"):
            with self.subTest(file=name):
                path = self.evidence('{"detail": "' + FAKE_API_KEY + '"}', name)
                proc = run_python([REDACT, "--check", path], env=clean_env())
                self.assertEqual(proc.returncode, 1, proc.stdout_text)

    def test_short_look_alikes_are_not_keys(self):
        """NFR-09.3: sk-ant- followed by fewer than 20 key characters is prose, not a key."""
        path = self.evidence("The prefix sk-ant-api03 and sk-ant-short are only examples, sk-ant-… too.\n")
        proc = run_python([REDACT, "--check", path], env=clean_env())
        self.assertEqual(proc.returncode, 0, proc.stdout_text)

    def test_existing_patterns_keep_their_behaviour(self):
        """NFR-09.3: Basic, Bearer and generated-password redaction are unchanged by the new pattern."""
        basic = base64.b64encode(b"asha:test-password").decode("ascii")
        text = ("Authorization: Basic " + basic + "\n"
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345\n"
                "Using generated security password: 123e4567-e89b-12d3-a456-426614174000\n"
                "HTTP Basic security stays as prose\n")
        path = self.evidence(text)
        proc = run_python([REDACT, path], env=clean_env())
        self.assertEqual(proc.returncode, 0)
        result = path.read_text(encoding="utf-8")
        self.assertIn("Basic <redacted-credentials>", result)
        self.assertIn("Bearer <redacted-token>", result)
        self.assertIn("<redacted-ephemeral-dev-password>", result)
        self.assertIn("HTTP Basic security stays as prose", result)

    def test_committed_evidence_directories_are_still_clean(self):
        """NFR-09.3: --check over sprint-contracts, specs/reviews and docs/fix-loops exits 0."""
        proc = run_python([REDACT, "--check", "sprint-contracts", "specs/reviews", "docs/fix-loops"],
                          env=clean_env(), cwd=ROOT)
        self.assertEqual(proc.returncode, 0, proc.stdout_text + proc.stderr_text)
        self.assertIn("total: 0", proc.stdout_text)


if __name__ == "__main__":
    unittest.main()
