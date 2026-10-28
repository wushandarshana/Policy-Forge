# web/ — CLAUDE.md

REST controllers, request/response DTOs, `GlobalExceptionHandler` and `SecurityConfig`. This is the **authentication
boundary** (NFR-04).

- Every endpoint declares its role with `@PreAuthorize` (or is listed as public in `SecurityConfig`), matching the table
  in `specs/app_spec.md` §8.
- Resolve the actor ID from `Authentication#getName()` here and pass it into services explicitly.
- Ownership: customers can only access their own applications and policies. Check this in the controller or the
  service, and return 404 (not 403) for other customers' resources.
- No business logic: validate request shape with Bean Validation, call one service method, map to a DTO.
- Never inject repositories (ArchUnit `LayeredArchitectureTest`).
- Mask PII in responses for non-owners with `PiiMasker` (Aadhaar and PAN show the last 4 characters).
- Map errors with `ProblemDetail`: validation 400 (with `errors[]`), not found 404, `InvalidPolicyStateException` 409
  (`type=/problems/invalid-policy-state`), forbidden 403.
- Controller classes end in `Controller` and live only in this package.
