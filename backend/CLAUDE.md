# backend/ — CLAUDE.md

Spring Boot 3 / Java 21 module. Base package `com.horizon.policyforge`. Read the root `CLAUDE.md` first.

## Layout
| Package | Purpose | Folder rules |
|---|---|---|
| `domain` | Pure business rules and value types | `domain/CLAUDE.md` |
| `service` | Use cases, transactions, audit, scheduler | `service/CLAUDE.md` |
| `repository` | JPA entities + Spring Data repositories | `repository/CLAUDE.md` |
| `web` | REST controllers, DTOs, error mapping, security | `web/CLAUDE.md` |
| `config` | Clock, logging, correlation filter, rule loader wiring | — |

## Build and run
- Always use the wrapper: `./mvnw verify` (tests, ArchUnit, JaCoCo report and check), `./mvnw spring-boot:run`.
- If `java` isn't found, see `docs/post-mortems/2026-10-28-jdk-path-environment.md` (environment first).
- JaCoCo minimum line coverage is 80% for `domain` and 70% overall (`jacoco:check`). Never lower these thresholds to
  make a build pass.

## Conventions
- Java `record`s for DTOs and domain value types. Constructor injection only, with no field `@Autowired`.
- Inject `java.time.Clock`. Never call `LocalDate.now()` without it.
- Errors are `ProblemDetail` (RFC 7807), mapped centrally in `web/GlobalExceptionHandler`.
- Logging: SLF4J with placeholders, and IDs only. See the NFR-03 PII list in the root `CLAUDE.md`.
- Tests live under `src/test/java` and mirror the package. Each test class or method is tagged `@Tag("AC-NN")` when it
  covers an AC.
- Test naming: `*Test` for unit and slice tests, `*IT` for full `@SpringBootTest` integration tests (both run in
  `verify`).
