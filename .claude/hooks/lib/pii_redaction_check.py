"""PreToolUse(Write|Edit|MultiEdit): block PII in log statements (NFR-03, root CLAUDE.md rule 7)."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)

if not re.search(r"(backend/src/main/java/.+\.java|frontend/src/.+\.(ts|tsx))$", path):
    sys.exit(0)

LOG_CALL = re.compile(r"\b(log|logger|LOG|LOGGER)\s*\.\s*(trace|debug|info|warn|error)\s*\(|"
                      r"System\.(out|err)\.print|console\.(log|info|warn|error|debug)\s*\(")
PII = re.compile(r"aadhaar|\bpan\b|getPan\s*\(|\.pan\s*\(|healthDeclaration|dateOfBirth|\bdob\b|fullName|"
                 r"\bkyc\b|getKyc\s*\(|\.kyc\s*\(", re.I)

text = hookio.strip_comments(hookio.new_text(data))
# Join multi-line log statements so arguments on following lines are inspected.
for stmt in re.split(r";\s*\n", text):
    if LOG_CALL.search(stmt) and PII.search(stmt):
        line = " ".join(stmt.split())[:160]
        hookio.block("NFR-03", f"{path} logs PII: `{line}`. Log IDs only (applicationId, policyNumber, quoteId).")

sys.exit(0)
