"""PreToolUse(Write|Edit|MultiEdit): block PII in log statements (NFR-03, root CLAUDE.md rule 7).

String literals are blanked before matching, so a message like "KYC submitted" is fine;
only identifiers passed to the logger are inspected. Logging whole request/KYC/DTO objects
is blocked because their toString() may contain PII.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)

if not re.search(r"(backend/src/main/java/.+\.java|frontend/src/.+\.(ts|tsx))$", path):
    sys.exit(0)

LOG_CALL = re.compile(
    r"\b(log|logger|LOG|LOGGER)\s*\.\s*(trace|debug|info|warn|error|atTrace|atDebug|atInfo|atWarn|atError|atLevel)\s*\(|"
    r"System\.(out|err)\.print|console\.(log|info|warn|error|debug)\s*\(")
PII_FIELD = re.compile(r"\b(aadhaar\w*|pan|getPan|healthDeclarations?|getHealthDeclarations?|dateOfBirth|getDateOfBirth|"
                       r"dob|fullName|getFullName|kyc|getKyc)\b", re.I)
# whole objects whose toString() may carry PII, passed as a bare argument (not `.id()` etc.)
WHOLE_OBJECT = re.compile(r"[(,]\s*(\w*(Request|Kyc|KYC|Dto|DTO|Application|Command)|request|application|kyc|body)\s*[,)]")

code = hookio.strip_strings(hookio.strip_comments(hookio.new_text(data)))
for stmt in re.split(r";", code):
    if not LOG_CALL.search(stmt):
        continue
    line = " ".join(stmt.split())[:160]
    if PII_FIELD.search(stmt):
        hookio.block("NFR-03", f"{path} logs a PII field: `{line}`. Log IDs only (applicationId, policyNumber, quoteId).")
    if WHOLE_OBJECT.search(stmt):
        hookio.block("NFR-03", f"{path} logs a whole request/KYC/DTO object: `{line}`. Its toString() may contain PII; "
                     "log its ID instead.")

sys.exit(0)
