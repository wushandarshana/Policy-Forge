"""PreToolUse(Write|Edit|MultiEdit): block floating-point money (NFR-01, root CLAUDE.md rule 5)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)
text = hookio.new_text(data)

MONEY_JAVA = r"backend/src/main/java/.+/(domain|service)/.+\.java$"
MIGRATION = r"backend/src/main/resources/db/migration/.+\.sql$"
FRONTEND = r"frontend/src/.+\.(ts|tsx)$"

if hookio.path_matches(path, MONEY_JAVA):
    code = hookio.strip_comments(text)
    hits = re.findall(r"\b(double|float|Double|Float)\b", code)
    if hits:
        hookio.block("NFR-01", f"{path} uses floating-point type(s) {sorted(set(hits))}. "
                     "Money, rates and factors must be BigDecimal (see domain/CLAUDE.md).")
    if re.search(r"new\s+BigDecimal\(\s*-?\d+\.\d+\s*\)|BigDecimal\.valueOf\(\s*-?\d+\.\d+\s*\)", code):
        hookio.block("NFR-01", f"{path} builds a BigDecimal from a double literal. "
                     "Use the String constructor, e.g. new BigDecimal(\"0.025\").")
elif hookio.path_matches(path, MIGRATION):
    if re.search(r"\b(DOUBLE|FLOAT|REAL)\b", text, flags=re.I):
        hookio.block("NFR-01", f"{path} declares a DOUBLE/FLOAT/REAL column. Use NUMERIC(19,2) or NUMERIC(19,10).")
elif hookio.path_matches(path, FRONTEND):
    code = hookio.strip_comments(text)
    if re.search(r"(parseFloat|Number)\(\s*[\w.]*(premium|refund|amount|adjustment|sumInsured)", code, flags=re.I):
        hookio.block("NFR-01", f"{path} converts a money string to a JS number. "
                     "Money is displayed from API decimal strings; no browser arithmetic (frontend/CLAUDE.md).")

sys.exit(0)
