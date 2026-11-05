"""Hygiene tests for scripts/ci/*.py: they must run on the CI images' Python 3.11 with the standard library only."""
import ast
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ci_test_support import CI_DIR  # noqa: E402

EXPECTED_SCRIPTS = {"check_line_endings.py", "claude_review.py", "fetch_tools.py", "quality_gates.py"}


def ci_scripts():
    return sorted(CI_DIR.glob("*.py"))


class CiScriptHygieneTests(unittest.TestCase):
    def test_the_expected_ci_scripts_exist(self):
        """NFR-09.4: the four CI entry points of plan C1 are present in scripts/ci/."""
        self.assertEqual(EXPECTED_SCRIPTS - {p.name for p in ci_scripts()}, set())

    def test_every_ci_script_parses_as_python_3_11(self):
        """NFR-09.4: every scripts/ci/*.py parses with feature_version (3, 11), the GitLab review image's Python."""
        self.assertTrue(ci_scripts(), "no scripts/ci/*.py found")
        for path in ci_scripts():
            with self.subTest(script=path.name):
                ast.parse(path.read_text(encoding="utf-8"), filename=path.name, feature_version=(3, 11))

    def test_every_ci_script_imports_only_the_standard_library(self):
        """NFR-09.4: no scripts/ci/*.py imports a third-party or repository module."""
        self.assertTrue(ci_scripts(), "no scripts/ci/*.py found")
        stdlib = set(sys.stdlib_module_names)
        for path in ci_scripts():
            tree = ast.parse(path.read_text(encoding="utf-8"), filename=path.name)
            for node in ast.walk(tree):
                if isinstance(node, ast.Import):
                    modules = [alias.name for alias in node.names]
                elif isinstance(node, ast.ImportFrom):
                    self.assertEqual(node.level, 0, f"{path.name} uses a relative import")
                    modules = [node.module or ""]
                else:
                    continue
                for module in modules:
                    with self.subTest(script=path.name, module=module):
                        self.assertIn(module.split(".")[0], stdlib)

    def test_ci_scripts_never_use_a_shell(self):
        """NFR-09.4: no scripts/ci/*.py starts a command through a shell (shell=True, os.system, os.popen)."""
        self.assertTrue(ci_scripts(), "no scripts/ci/*.py found")
        for path in ci_scripts():
            source = path.read_text(encoding="utf-8")
            for forbidden in ("shell=True", "os.system(", "os.popen("):
                with self.subTest(script=path.name, forbidden=forbidden):
                    self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
