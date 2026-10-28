# AGENTS.md — Table of Contents

Index only. Rules live in the linked files.

## Guidance hierarchy (CLAUDE.md)
- [`CLAUDE.md`](CLAUDE.md): repository-wide non-negotiables, stack, workflow
- [`backend/CLAUDE.md`](backend/CLAUDE.md): backend module conventions
  - [`domain/CLAUDE.md`](backend/src/main/java/com/horizon/policyforge/domain/CLAUDE.md): pure business rules
  - [`service/CLAUDE.md`](backend/src/main/java/com/horizon/policyforge/service/CLAUDE.md): use cases, transactions, audit
  - [`repository/CLAUDE.md`](backend/src/main/java/com/horizon/policyforge/repository/CLAUDE.md): persistence, append-only
  - [`web/CLAUDE.md`](backend/src/main/java/com/horizon/policyforge/web/CLAUDE.md): controllers, auth boundary
  - [`rules/CLAUDE.md`](backend/src/main/resources/rules/CLAUDE.md): versioned product rule files
  - [`db/migration/CLAUDE.md`](backend/src/main/resources/db/migration/CLAUDE.md): Flyway migrations
  - [`architecture tests CLAUDE.md`](backend/src/test/java/com/horizon/policyforge/architecture/CLAUDE.md)
- [`frontend/CLAUDE.md`](frontend/CLAUDE.md): React UI conventions
  - [`frontend/e2e/CLAUDE.md`](frontend/e2e/CLAUDE.md): Playwright E2E and snapshots
- [`specs/CLAUDE.md`](specs/CLAUDE.md): how to write and change specs
- [`scripts/CLAUDE.md`](scripts/CLAUDE.md): Agent SDK automation

## Specifications
- [`specs/app_spec.md`](specs/app_spec.md): root spec
- [`specs/product-catalog_spec.md`](specs/product-catalog_spec.md)
- [`specs/quote-engine_spec.md`](specs/quote-engine_spec.md)
- [`specs/underwriting_spec.md`](specs/underwriting_spec.md)
- [`specs/policy-issuance_spec.md`](specs/policy-issuance_spec.md)
- [`specs/endorsement_spec.md`](specs/endorsement_spec.md)
- [`specs/renewal-cancellation_spec.md`](specs/renewal-cancellation_spec.md)
- [`specs/portfolio-dashboard_spec.md`](specs/portfolio-dashboard_spec.md)
- [`specs/reviews/`](specs/reviews/): evaluator and audit outputs

## Documentation
- [`docs/business-case.md`](docs/business-case.md)
- [`docs/architecture.md`](docs/architecture.md)
- [`docs/tdd.md`](docs/tdd.md)
- [`docs/harness-setup.md`](docs/harness-setup.md)
- [`docs/knowledge-deposits.md`](docs/knowledge-deposits.md)
- [`docs/post-mortems/`](docs/post-mortems/) · [`docs/fix-loops/`](docs/fix-loops/) · [`docs/prs/`](docs/prs/)
- [`sprint-contracts/`](sprint-contracts/)

## Agent substrate (`.claude/`)
- Agents: [`.claude/agents/`](.claude/agents/)
- Skills: [`.claude/skills/`](.claude/skills/)
- Commands: [`.claude/commands/`](.claude/commands/)
- Hooks: [`.claude/hooks/`](.claude/hooks/), wired in [`.claude/settings.json`](.claude/settings.json)
- Plugin manifest: [`plugin.json`](plugin.json) · [`.claude-plugin/plugin.json`](.claude-plugin/plugin.json)
- MCP: [`.mcp.json`](.mcp.json) (Playwright)

## CI
- [`.gitlab-ci.yml`](.gitlab-ci.yml) (added in Sprint 5)
- Hook regression tests: [`.claude/hooks/tests/test_hooks.py`](.claude/hooks/tests/test_hooks.py)
