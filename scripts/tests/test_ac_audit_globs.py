"""Tests that scripts/ac_audit.py counts the Python tests under scripts/tests/ (their docstring IDs)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import ROOT, SCRIPTS, load_script  # noqa: E402


class AcAuditGlobTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audit = load_script("ac_audit", SCRIPTS)

    def test_test_globs_include_the_python_script_tests(self):
        """NFR-09.6: ac_audit.py scans scripts/tests/test_*.py, so the CI script tests count towards coverage."""
        self.assertIn("scripts/tests/test_*.py", self.audit.TEST_GLOBS)

    def test_a_docstring_id_in_a_script_test_counts_as_exact_coverage(self):
        """NFR-09.6: a test whose docstring starts with NFR-09.6 is reported as covering NFR-09.6."""
        criterion = self.audit.Criterion("NFR-09.6", "specs/app_spec.md", "quality gates")
        self.audit.collect_references({"NFR-09.6": criterion})
        self.assertTrue([loc for loc in criterion.exact if loc.startswith("scripts/tests/test_")],
                        "no scripts/tests reference was counted for NFR-09.6")

    def test_fixtures_directory_is_not_scanned(self):
        """NFR-09.6: the stub CLI under scripts/tests/fixtures/ is never counted as a test."""
        criterion = self.audit.Criterion("NFR-09.4", "specs/app_spec.md", "review")
        self.audit.collect_references({"NFR-09.4": criterion})
        self.assertFalse([loc for loc in criterion.exact if "/fixtures/" in loc])

    def test_existing_globs_are_unchanged(self):
        """NFR-09.6: the five pre-existing globs are still there, in order, before the new one."""
        self.assertEqual(self.audit.TEST_GLOBS[:5], [
            "backend/src/test/java/**/*Test.java",
            "backend/src/test/java/**/*IT.java",
            "frontend/src/**/*.test.ts",
            "frontend/src/**/*.test.tsx",
            "frontend/e2e/**/*.spec.ts",
        ])
        self.assertEqual(self.audit.ROOT, ROOT)


if __name__ == "__main__":
    unittest.main()
