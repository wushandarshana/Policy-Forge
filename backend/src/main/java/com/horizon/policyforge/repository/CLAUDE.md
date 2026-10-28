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
but only through intention-revealing methods (`applyTransition`, `markPaid`), never generic setters.

## Other rules
- Money columns are `NUMERIC(19,2)` (final amounts) or `NUMERIC(19,10)` (rates) and map to `BigDecimal`.
- JSON payloads (risk inputs, rule sets, endorsement payloads) are stored as `CLOB` text and mapped by the service.
- Schema changes go through Flyway only (`src/main/resources/db/migration`). Hibernate `ddl-auto=validate`.
