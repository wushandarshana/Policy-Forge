# Autonomous Fix Loops

Each file here is the trace of one loop where an agent **detected** a failure, **reproduced** it with a failing test,
**fixed** it, **validated** it (full test suite plus evaluator) and **opened a PR**.

File name: `YYYY-MM-DD-<short-slug>.md`. Before keeping any log here, run `python scripts/redact_evidence.py <dir>`
(and `--check` must exit 0). See KD-011. Template:

```markdown
# Fix loop: <title>
- Detected by: <agent / hook / CI job> · Trigger: <failing check output>
- Reproduce: <failing test name, AC id, command, output excerpt>
- Root cause: <one paragraph>
- Fix: <commit sha(s)>
- Validate: <commands + results>
- PR: docs/prs/PR-NNN.md (branch fix/…)
- Knowledge deposit: KD-NNN (if a rule/hook/skill was added)
```
