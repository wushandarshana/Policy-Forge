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
- **Every state-changing endpoint declares `consumes = MediaType.APPLICATION_JSON_VALUE`** (NFR-04.3). Browsers cache
  HTTP Basic credentials and attach them to cross-site requests, and a `text/plain` form POST needs no CORS preflight.
  Requiring JSON forces the preflight, and with CORS left unconfigured the preflight fails. Never take
  `@RequestBody String` without `consumes`. (KD-007)
- **Public responses are built from allow-lists, never deny-lists** (NFR-04.2). Return only the fields the spec
  declares. Removing "known bad" keys from a client-supplied map leaks every key you didn't think of. Reject unknown
  input keys at the boundary. (KD-008)
- Security matchers list public paths explicitly (no `/**` wildcards on public rules), so new endpoints default to
  authenticated.
- Problem `detail` text is fixed per error type and never echoes request input.
- **Limits and checks default-deny and match on the routed path** (NFR-04.4). Path-based security logic must use the
  decoded, normalized path that Spring routes on (e.g. `UrlPathHelper#getLookupPathForRequest` / `ServletRequestPathUtils`),
  never the raw `getRequestURI()`. Apply limits to everything and relax them explicitly, not the other way round. (KD-010)
