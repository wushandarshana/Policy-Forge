# service/ — CLAUDE.md

Application services: one class per use case area (`QuoteService`, `ApplicationService`, `UnderwritingService`,
`PolicyIssuanceService`, `EndorsementService`, `RenewalService`, `CancellationService`, `RuleSetService`,
`DashboardService`, `AuditService`) plus `RenewalScheduler`.

- Own the transaction: `@Transactional` on public mutating methods. Multi-row writes (endorsement + version +
  transition, refund + transition) must be in **one** transaction (AC-06, AC-08).
- Delegate every business decision to `domain`. A service method reads like a script: load, then domain decision,
  then append records, then audit.
- Every UNDERWRITER, ADMIN or PRODUCT_MANAGER action calls `AuditService.record(actorId, action, …)` (NFR-04). The
  actor ID is passed in from the controller, never read from a static context inside domain code.
- Status changes go through `PolicyStateMachine` and then append a `PolicyStatusTransition`. Never set a status
  directly.
- Must not depend on `..web..` (ArchUnit).
- Never log PII. Log `applicationId`, `policyNumber` and `quoteId` only.
