#!/usr/bin/env bash
# PolicyForge guardrail: see lib/pii_redaction_check.py for the rule enforced.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Probe for an interpreter that really runs: on Windows `python3` may be the Microsoft Store stub (KD-005).
for c in python python3 py; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c "import sys" >/dev/null 2>&1; then PY="$c"; break; fi
done
if [ -z "${PY:-}" ]; then echo "[PolicyForge guardrail] no working Python found; hook ${BASH_SOURCE[0]##*/} cannot run" >&2; exit 2; fi
exec "$PY" "$HERE/lib/pii_redaction_check.py"
