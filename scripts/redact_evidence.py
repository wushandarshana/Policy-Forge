"""Redact credentials from evidence logs before they are kept in the repository.

Covers HTTP Basic/Bearer Authorization values (MockMvc header dumps, curl -v output) and
Spring's ephemeral "generated security password" lines. Exit code 1 with --check if anything
would be redacted, so it can gate CI and the /sprint-archive step.

Usage:
    python scripts/redact_evidence.py <file-or-dir> [...]           # redact in place
    python scripts/redact_evidence.py --check <file-or-dir> [...]   # report only, exit 1 if found
"""
import base64
import binascii
import re
import sys
from pathlib import Path

BASIC = re.compile(r"(Basic )([A-Za-z0-9+/]{4,}={0,2})(?![A-Za-z0-9+/=])")


def _basic(match):
    """Redact only real credentials: base64 that decodes to printable `user:password`. Prose such as
    "Basic credentials" or "HTTP Basic security" is not valid base64 of that shape and is left alone."""
    token = match.group(2)
    try:
        decoded = base64.b64decode(token, validate=True).decode("utf-8")
    except (binascii.Error, ValueError, UnicodeDecodeError):
        return match.group(0)
    if ":" in decoded and decoded.isprintable():
        return match.group(1) + "<redacted-credentials>"
    return match.group(0)


PATTERNS = [
    (BASIC, _basic),
    (re.compile(r"(Bearer )[A-Za-z0-9._~+/=-]{16,}"), r"\1<redacted-token>"),
    (re.compile(r"(Using generated security password: )[0-9a-f-]{36}"), r"\1<redacted-ephemeral-dev-password>"),
]
SUFFIXES = {".log", ".txt", ".md", ".json", ".xml"}


def files(args):
    for a in args:
        p = Path(a)
        if p.is_dir():
            yield from (f for f in p.rglob("*") if f.is_file() and f.suffix in SUFFIXES)
        elif p.is_file():
            yield p


def main(argv):
    check = "--check" in argv
    targets = [a for a in argv if a != "--check"]
    if not targets:
        print(__doc__)
        return 2
    total = 0
    for f in files(targets):
        text = f.read_text(encoding="utf-8", errors="replace")
        new, count = text, 0
        for pattern, repl in PATTERNS:
            before = new
            new = pattern.sub(repl, new)
            # count actual changes, not matches: the Basic handler leaves non-credentials untouched
            count += sum(1 for m in pattern.finditer(before)
                         if (repl(m) if callable(repl) else m.expand(repl)) != m.group(0))
        if count:
            total += count
            print(f"{'found' if check else 'redacted'} {count:>3}  {f.as_posix()}")
            if not check:
                f.write_text(new, encoding="utf-8")
    print(f"total: {total}")
    return 1 if (check and total) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
