"""PostToolUse(Write|Edit|MultiEdit): after a spec edit, remind the agent that code must follow (Spec-Is-Truth)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)
if not hookio.path_matches(path, r"^specs/[^/]+_spec\.md$"):
    sys.exit(0)

text = hookio.new_text(data)
acs = sorted(set(re.findall(r"AC-\d{2}(?:\.\d+)?", text)))
touched = ", ".join(acs) if acs else "no AC ids in the changed text"
hookio.context("PostToolUse",
               f"Spec changed: {path} ({touched}). Spec is truth: commit this spec change separately (`spec: ...`), "
               "run /ac-coverage, update or add the AC-tagged tests (red) and regenerate the affected code (green). "
               "Recompute any money example with the premium-calc-evaluator skill.")
