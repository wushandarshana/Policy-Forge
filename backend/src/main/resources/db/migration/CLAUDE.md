# db/migration/ — CLAUDE.md

Flyway migrations `V<n>__<description>.sql` (H2, PostgreSQL-compatible SQL where possible).

- **Append-only** (NFR-05). Never edit or delete a migration once it is merged. Fix forward with a new `V<n+1>`.
  `policy-immutability-check.sh` blocks edits to existing files, and Flyway `validateOnMigrate` catches checksum drift.
- Money columns are `NUMERIC(19,2)` and rates `NUMERIC(19,10)`. **Never `DOUBLE`, `FLOAT` or `REAL`.**
- Append-only tables get no `UPDATE`/`DELETE` in application code. Where H2 supports it, add a trigger that rejects
  updates and deletes (see `migration-coherence-agent`).
- Seed data uses synthetic values only, in `V<n>__seed_*.sql` or the `SeedDataLoader` (dev profile).
