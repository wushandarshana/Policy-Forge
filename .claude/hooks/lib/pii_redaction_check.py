"""PreToolUse(Write|Edit|MultiEdit): block PII in log statements (NFR-03, root CLAUDE.md rule 7).

Comments and string literals are blanked before matching, so a message like "KYC submitted" is fine.
Every identifier passed to a logger is inspected: PII stems are matched inside identifiers, so
JavaBean getters (`getAadhaar()`, `getPanNumber()`, `getDob()`) are caught. Identifiers that are
explicitly masked/redacted/hashed are allowed. Logging whole request/KYC/DTO objects is blocked
because their toString() may contain PII.
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
SAFE_PREFIX = ("masked", "redacted", "hashed", "hash")
PII_STEM = re.compile(r"aadhaar|aadhar|healthdeclaration|dateofbirth|birthdate|fullname|pannumber")
PII_WORD = re.compile(r"(get)?(pan|dob|kyc)")          # whole identifier, any case: pan, getPan, dob, getDob, kyc
PII_SUFFIX = re.compile(r"(Pan|PAN|Dob|DOB|Kyc|KYC)$")  # camelCase tail: applicantPan, customerDob, applicantKyc
WHOLE_OBJECT = re.compile(r"[(,]\s*(\w*(Request|Kyc|KYC|Dto|DTO|Application|Command)|request|application|kyc|body)\s*[,)]")


def pii_identifier(stmt):
    for ident in re.findall(r"[A-Za-z_]\w*", stmt):
        low = ident.lower()
        if low.startswith(SAFE_PREFIX):
            continue
        if PII_STEM.search(low) or PII_WORD.fullmatch(low) or PII_SUFFIX.search(ident):
            return ident
    return None


code = hookio.strip_code(hookio.new_text(data))
for stmt in code.split(";"):
    if not LOG_CALL.search(stmt):
        continue
    line = " ".join(stmt.split())[:160]
    ident = pii_identifier(stmt)
    if ident:
        hookio.block("NFR-03", f"{path} logs PII (`{ident}`): `{line}`. Log IDs only (applicationId, policyNumber, quoteId).")
    if WHOLE_OBJECT.search(stmt):
        hookio.block("NFR-03", f"{path} logs a whole request/KYC/DTO object: `{line}`. Its toString() may contain PII; "
                     "log its ID instead.")

sys.exit(0)
