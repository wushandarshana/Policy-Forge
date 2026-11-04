# repository/ — CLAUDE.md

JPA entities (`*Entity`) and Spring Data repositories (`*Repository`).

## Append-only entities (NFR-02, NFR-05)
`RuleSetVersionEntity`, `QuoteEntity`, `UnderwritingDecisionEntity`, `PolicyVersionEntity`, `EndorsementEntity`,
`PolicyStatusTransitionEntity`, `RefundRecordEntity`, `AuditEntryEntity`:
- `@Immutable` (Hibernate), all fields set through the constructor or a static factory, **no public setters**.
- The repository extends `Repository<T, ID>` (not `JpaRepository`) and declares only `save` and finder methods. **No
  `delete*`, no `@Modifying` update queries.** `AppendOnlyArchTest` enforces this.

## Mutable aggregates
`PolicyEntity` (status, currentVersionNo), `ApplicationEntity` (status) and `PolicyTermEntity` (paid flag) may change,
but only through intention-revealing methods (`applyTransition`, `markPaid`, and `repriceOffer` for an unpaid renewal term only, AC-07.7), never generic setters.

## Other rules
- Money columns are `NUMERIC(19,2)` (final amounts) or `NUMERIC(19,10)` (rates) and map to `BigDecimal`.
- JSON payloads (risk inputs, rule sets, endorsement payloads) are stored as `CLOB` text and mapped by the service.
- Schema changes go through Flyway only (`src/main/resources/db/migration`). Hibernate `ddl-auto=validate`.
- A transaction that adds a row under a policy without changing the policy row (for example a renewal offer term) must still bump the policy version through `PolicyLocking.bumpVersion` (PESSIMISTIC_FORCE_INCREMENT), so a concurrent cancel or endorsement conflicts. A Spring Data `@Lock(OPTIMISTIC_FORCE_INCREMENT)` finder did not bump the version (Sprint 4 review MINOR-2).
