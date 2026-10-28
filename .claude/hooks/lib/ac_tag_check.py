"""PostToolUse(Write|Edit|MultiEdit): every test file must reference an AC-NN or NFR-NN id (root CLAUDE.md rule 8)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)

TEST_FILE = r"(backend/src/test/java/.+(Test|IT)\.java|frontend/src/.+\.test\.tsx?|frontend/e2e/.+\.spec\.ts)$"
if not hookio.path_matches(path, TEST_FILE) or "/fixtures/" in path:
    sys.exit(0)

full = Path(hookio.file_path(data))
try:
    content = full.read_text(encoding="utf-8")
except OSError:
    sys.exit(0)

if not re.search(r"\b(AC|NFR)-\d{2}", content):
    print(f"[PolicyForge guardrail] AC tagging: {path} has no AC-NN / NFR-NN reference. "
          "Add @Tag(\"AC-NN\") + @DisplayName(\"AC-NN.M: ...\") (JUnit) or a test title starting with 'AC-NN.M:'.",
          file=sys.stderr)
    sys.exit(2)
sys.exit(0)
