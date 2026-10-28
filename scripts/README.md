# scripts/

| Script | What it does | Claude usage |
|---|---|---|
| `ac_audit.py` | AC → test traceability matrix plus semantic drift review; writes `specs/reviews/ac-audit-*.md`; exits 1 if any AC lacks a test | **Claude Agent SDK** (`claude_agent_sdk.query`) with read-only tools (`Read`, `Glob`, `Grep`) |

```bash
pip install -r scripts/requirements.txt
python scripts/ac_audit.py            # full audit (ANTHROPIC_API_KEY or Claude Code login)
python scripts/ac_audit.py --offline  # deterministic matrix only
```

CI runs `--offline` in the `test` stage as a gate, and the full audit in the `claude-review` stage on merge requests.
