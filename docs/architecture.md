# PolicyForge — Architecture

## 1. System context (C4 level 1)

```mermaid
C4Context
  title PolicyForge — System Context
  Person(customer, "Customer / Policyholder", "Quotes, applies, manages policies")
  Person(uw, "Underwriter", "Reviews referred applications")
  Person(admin, "Admin / Product Manager", "Overrides, catalog, dashboard")
  System(pf, "PolicyForge", "Spring Boot API + React SPA, H2 database")
  System_Ext(pay, "Payment (simulated)", "Out of scope — stubbed")
  System_Ext(kyc, "KYC provider (stubbed)", "Out of scope — format checks only")
  Rel(customer, pf, "Uses", "HTTPS / JSON")
  Rel(uw, pf, "Uses", "HTTPS / JSON")
  Rel(admin, pf, "Uses", "HTTPS / JSON")
  Rel(pf, pay, "Simulated premium capture")
  Rel(pf, kyc, "Stubbed validation")
```

## 2. Layered structure

```mermaid
flowchart TB
  subgraph Frontend["frontend/ — React + Vite (responsive SPA)"]
    UI[Pages: Quote · Apply · My Policies · Endorse · Renew/Cancel · Workbench · Catalog · Dashboard]
  end
  subgraph Backend["backend/ — Spring Boot (com.horizon.policyforge)"]
    direction TB
    W["web — @RestController, DTOs, ProblemDetail mapping, SecurityConfig (NFR-04)"]
    S["service — use cases, @Transactional, audit, RenewalScheduler"]
    R["repository — JPA entities, Spring Data repos (append-only)"]
    D["domain — pure Java rules: rating models, UnderwritingRuleEvaluator, PolicyStateMachine, ProRataRefundCalculator, EndorsementValidator, KycValidator, GracePeriodPolicy"]
    C["config — Clock, JSON logging, CorrelationIdFilter (NFR-06)"]
  end
  DB[(H2 + Flyway migrations)]
  RF[[rules/<product>/vN.json]]
  UI -->|REST /api| W
  W --> S
  S --> R
  S --> D
  R --> DB
  S -->|load on startup| RF
  W -.->|forbidden| R
  D -.->|forbidden: no Spring/JPA| R
```

| Layer | Package | May depend on | Must not depend on |
|---|---|---|---|
| web | `..web..` | service, domain (value types) | repository |
| service | `..service..` | repository, domain | web |
| repository | `..repository..` | domain (value types) | web, service |
| domain | `..domain..` | JDK only (`java.*`) | Spring, Jakarta, web, service, repository |
| config | `..config..` | anything | (not depended on by domain) |

ArchUnit enforces these rules (`backend/src/test/java/com/horizon/policyforge/architecture/`):
- `LayeredArchitectureTest`: the layer matrix above.
- `DomainPurityArchTest`: the domain has no `org.springframework..`, `jakarta.persistence..` or web imports.
- `MoneyPrecisionArchTest`: no `double`/`float`/`Double`/`Float` fields, parameters or returns in `domain` or
  `service` money classes (NFR-01).
- `AppendOnlyArchTest`: append-only entities (`PolicyVersionEntity`, `EndorsementEntity`,
  `PolicyStatusTransitionEntity`, `RefundRecordEntity`, `AuditEntryEntity`, `RuleSetVersionEntity`,
  `UnderwritingDecisionEntity`) have no public setters, and their repositories don't declare `delete*` methods
  (NFR-02).
- `ControllerPlacementArchTest`: `@RestController` classes live only in `..web..`, and their names end in
  `Controller`.

## 3. Quote-to-bind sequence

```mermaid
sequenceDiagram
  autonumber
  actor C as Customer
  participant UI as React SPA
  participant QC as QuoteController
  participant QS as QuoteService
  participant CAT as RuleSetService
  participant PC as PremiumCalculator (domain)
  participant AC as ApplicationController
  participant US as UnderwritingService
  participant UE as UnderwritingRuleEvaluator (domain)
  participant PS as PolicyIssuanceService
  participant SM as PolicyStateMachine (domain)
  participant DB as H2

  C->>UI: choose product, enter risk inputs
  UI->>QC: POST /api/quotes
  QC->>QS: quote(product, inputs)
  QS->>CAT: activeVersion(product, today)
  CAT-->>QS: RuleSetVersion vN
  QS->>PC: premium(inputs, vN)
  PC-->>QS: PremiumBreakdown (BigDecimal)
  QS->>DB: insert Quote (immutable)
  QS-->>UI: 201 quote + breakdown
  C->>UI: Apply + KYC stub
  UI->>AC: POST /api/applications
  AC->>US: submit(quoteId, kyc)
  US->>US: KycValidator, RiskInputValidator
  US->>UE: evaluate(inputs, vN)
  UE-->>US: AUTO_BIND / MANUAL_REVIEW / DECLINE + reason codes
  US->>DB: insert Application, UnderwritingDecision (append-only)
  US-->>UI: status APPROVED / REFERRED / DECLINED
  C->>UI: Issue policy
  UI->>AC: POST /api/applications/{id}/issue
  AC->>PS: issue(applicationId, effectiveDate)
  PS->>SM: transition(null, ACTIVE)
  PS->>DB: insert Policy, PolicyVersion v1, PolicyTerm #1, StatusTransition
  PS-->>UI: 201 PF-TL-2026-000001 ACTIVE
```

## 4. Policy state machine

```mermaid
stateDiagram-v2
  [*] --> ACTIVE: issue
  ACTIVE --> ENDORSED: endorse
  ACTIVE --> RENEWED: pay renewal
  ACTIVE --> LAPSED: grace expired
  ACTIVE --> CANCELLED: cancel
  ENDORSED --> ENDORSED: endorse
  ENDORSED --> RENEWED
  ENDORSED --> LAPSED
  ENDORSED --> CANCELLED
  RENEWED --> ENDORSED
  RENEWED --> RENEWED
  RENEWED --> LAPSED
  RENEWED --> CANCELLED
  LAPSED --> [*]
  CANCELLED --> [*]
```

## 5. Cross-cutting concerns

| Concern | Mechanism |
|---|---|
| Money (NFR-01) | `BigDecimal`. `Money` helper in domain: scale 10 intermediate, scale 2 HALF_UP final |
| Append-only (NFR-02/05) | Immutable JPA entities (no setters, `@Immutable` where applicable), no delete repo methods, Flyway with `validateOnMigrate` |
| PII (NFR-03) | PII fields are never passed to loggers. `PiiMasker` for API responses. Logback JSON encoder with no request-body logging. Hook-enforced |
| Auth (NFR-04) | Spring Security HTTP Basic, in-memory synthetic users, `@PreAuthorize` on controllers. `AuditService` writes the actor ID |
| Logging (NFR-06) | `logstash-logback-encoder` JSON. `CorrelationIdFilter` puts `correlationId` in MDC and echoes the header |
| Health (NFR-07) | Spring Boot Actuator `/actuator/health` |
| Time | Injected `Clock` bean. Tests use `Clock.fixed` |
| Rule versions | JSON files loaded into `rule_set_version` on startup if absent. Published through the API afterwards |

## 6. Deployment (local)

A single command, `cd backend && ./mvnw spring-boot:run`. `frontend-maven-plugin` builds the React app into
`backend/target/classes/static`, so one process on port 8080 serves both the SPA and `/api`. For dev, the frontend can
run on Vite (`npm run dev`, port 5173), proxying `/api` to 8080.
