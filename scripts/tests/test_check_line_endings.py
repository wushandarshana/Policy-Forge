"""Tests for scripts/ci/check_line_endings.py: the LF-only gate for .claude/hooks/*.sh (KD-002)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import CI_DIR, ROOT, clean_env, git, init_repo, run_python, write_bytes  # noqa: E402

CHECKER = CI_DIR / "check_line_endings.py"
HOOK_GLOB = ".claude/hooks/*.sh"


class LineEndingCheckTests(unittest.TestCase):
    def setUp(self):
        holder, self.repo = init_repo()
        self.addCleanup(holder.cleanup)

    def check(self, *patterns):
        return run_python([CHECKER, "--repo", self.repo, *(patterns or (HOOK_GLOB,))], env=clean_env())

    def track(self, relative, data):
        write_bytes(self.repo, relative, data)
        git(self.repo, "add", relative)

    def test_lf_only_hook_files_pass(self):
        """NFR-09.6: hook files with LF endings in the index and the working tree pass the check."""
        self.track(".claude/hooks/good.sh", b"#!/usr/bin/env bash\nexit 0\n")
        self.track(".claude/hooks/other.sh", b"#!/usr/bin/env bash\necho ok\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 0, proc.stdout_text + proc.stderr_text)

    def test_crlf_in_the_index_fails_and_names_the_file(self):
        """NFR-09.6: a hook file that is CRLF in the index (i/crlf) fails and is listed."""
        self.track(".claude/hooks/good.sh", b"#!/usr/bin/env bash\nexit 0\n")
        self.track(".claude/hooks/bad.sh", b"#!/usr/bin/env bash\r\nexit 0\r\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 1, proc.stdout_text)
        output = proc.stdout_text + proc.stderr_text
        self.assertIn(".claude/hooks/bad.sh", output)
        self.assertNotIn(".claude/hooks/good.sh", output)

    def test_mixed_endings_in_the_index_fail(self):
        """NFR-09.6: a file that is mixed (i/mixed) in the index fails."""
        self.track(".claude/hooks/mixed.sh", b"#!/usr/bin/env bash\nexit 0\r\necho x\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 1, proc.stdout_text)
        self.assertIn(".claude/hooks/mixed.sh", proc.stdout_text + proc.stderr_text)

    def test_cr_byte_only_in_the_working_tree_fails(self):
        """NFR-09.6: a file that is LF in the index but has a CR byte in the working tree fails."""
        self.track(".claude/hooks/drift.sh", b"#!/usr/bin/env bash\nexit 0\n")
        write_bytes(self.repo, ".claude/hooks/drift.sh", b"#!/usr/bin/env bash\r\nexit 0\r\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 1, proc.stdout_text)
        self.assertIn(".claude/hooks/drift.sh", proc.stdout_text + proc.stderr_text)

    def test_every_offending_file_is_listed(self):
        """NFR-09.6: with several offenders the output lists each one, not just the first."""
        self.track(".claude/hooks/one.sh", b"a\r\n")
        self.track(".claude/hooks/two.sh", b"b\r\n")
        self.track(".claude/hooks/three.sh", b"c\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 1)
        output = proc.stdout_text + proc.stderr_text
        self.assertIn(".claude/hooks/one.sh", output)
        self.assertIn(".claude/hooks/two.sh", output)
        self.assertNotIn(".claude/hooks/three.sh", output)

    def test_files_outside_the_pattern_are_ignored(self):
        """NFR-09.6: a CRLF file that the pattern does not match does not fail the check."""
        self.track(".claude/hooks/good.sh", b"exit 0\n")
        self.track("windows/run.cmd", b"@echo off\r\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 0, proc.stdout_text + proc.stderr_text)

    def test_a_pattern_that_matches_no_file_fails(self):
        """NFR-09.6: a pattern with no matching tracked file fails closed, so a typo cannot disable the gate."""
        self.track("README.md", b"hello\n")
        proc = self.check()
        self.assertEqual(proc.returncode, 1, proc.stdout_text)
        self.assertIn("no file", (proc.stdout_text + proc.stderr_text).lower())

    def test_real_hook_files_are_lf_only(self):
        """NFR-09.6: the committed .claude/hooks/*.sh of this repository pass the check."""
        proc = run_python([CHECKER, HOOK_GLOB], env=clean_env(), cwd=ROOT)
        self.assertEqual(proc.returncode, 0, proc.stdout_text + proc.stderr_text)


if __name__ == "__main__":
    unittest.main()
