"""PreToolUse(Write|Edit|MultiEdit): protect append-only artefacts (NFR-02, NFR-05, root CLAUDE.md rule 6).

- Published Flyway migrations and rule-set versions (present on main) may not be edited.
- Append-only JPA entities may not gain setters; their repositories may not expose deletes.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import hookio  # noqa: E402

data = hookio.load()
path = hookio.rel_path(data)
cwd = data.get("cwd")
text = hookio.strip_comments(hookio.new_text(data))

PUBLISHED = [
    (r"backend/src/main/resources/db/migration/V[^/]+\.sql$", "Flyway migration", "add a new V<n+1>__*.sql"),
    (r"backend/src/main/resources/rules/[^/]+/v\d+\.json$", "published rule-set version", "publish v<N+1>.json"),
]
for pattern, kind, remedy in PUBLISHED:
    if hookio.path_matches(path, pattern) and hookio.tracked_on_main(path, cwd):
        hookio.block("NFR-05", f"{path} is a {kind} already merged to main and is immutable. Instead, {remedy}.")

APPEND_ONLY = ("RuleSetVersion", "Quote", "UnderwritingDecision", "PolicyVersion", "Endorsement",
               "PolicyStatusTransition", "RefundRecord", "AuditEntry")
name = Path(path).stem
if path.endswith(".java") and "/repository/" in path:
    base = re.sub(r"(Entity|Repository)$", "", name)
    if base in APPEND_ONLY:
        if name.endswith("Entity") and re.search(r"public\s+void\s+set[A-Z]\w*\s*\(", text):
            hookio.block("NFR-02", f"{name} is append-only; remove public setters (construct via constructor/factory).")
        if name.endswith("Repository"):
            if re.search(r"\b(JpaRepository|CrudRepository|ListCrudRepository)\b", text):
                hookio.block("NFR-02", f"{name} must extend Repository<T, ID>, not a CRUD repository that exposes delete.")
            if re.search(r"\b(delete|remove)\w*\s*\(", text) or "@Modifying" in text:
                hookio.block("NFR-02", f"{name} is append-only; delete/update methods are not allowed.")

sys.exit(0)
