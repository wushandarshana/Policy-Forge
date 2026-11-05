"""Generates the AC-08.5 refund oracle fixture from the premium-calc-evaluator oracle (plan C10, NFR-09.6).

The fixture is `backend/src/test/resources/oracle/ac-08-5-refund-cases.csv`: 1,000 cancellation cases whose expected
amounts come from `refund()` in `.claude/skills/premium-calc-evaluator/premium_check.py` (exact `Decimal` arithmetic, the
project's sanctioned oracle, KD-003), never from the Java calculator. A JUnit test asserts that
`ProRataRefundCalculator` gives exactly each row's values. Boundary rows come first, then seeded random rows.

Usage:
    python scripts/gen_refund_oracle_cases.py            # (re)write the fixture
    python scripts/gen_refund_oracle_cases.py --check    # exit 1, naming the first differing row, if the file differs

Standard library only, Python 3.11 or later. The oracle module is imported read-only: nothing is written next to it.
The random rows use only `getrandbits` of a fixed seed, which Python guarantees to be stable across versions, so the
same file is produced on every interpreter and `--check` can run in CI.
"""
import argparse
import calendar
import importlib.util
import random
import sys
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORACLE = ROOT / ".claude" / "skills" / "premium-calc-evaluator" / "premium_check.py"
FIXTURE = ROOT / "backend" / "src" / "test" / "resources" / "oracle" / "ac-08-5-refund-cases.csv"

SEED = 20261104
ROWS = 1000
HEADER = ("case,termStart,termEnd,cancellationDate,firstTerm,coolingOffDays,premium,fee,termDays,unexpiredDays,"
          "coolingOff,amount")


def load_oracle():
    """Imports premium_check.py by path, without writing bytecode beside it (the oracle stays read-only)."""
    if not ORACLE.is_file():
        raise SystemExit(f"oracle not found: {ORACLE}")
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("premium_check_oracle", ORACLE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    for name in ("refund", "days"):
        if not hasattr(module, name):
            raise SystemExit(f"the oracle has no {name}()")
    return module


def plus_months(start, months):
    """The date `months` later, the day clamped to the end of the month (the rule of java.time.LocalDate.plusMonths)."""
    index = start.year * 12 + (start.month - 1) + months
    year, month = divmod(index, 12)
    month += 1
    return date(year, month, min(start.day, calendar.monthrange(year, month)[1]))


def shown(path):
    """The path relative to the repository when it lies inside it (for messages)."""
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def money(cents):
    """A whole number of cents as a decimal string of scale 2, built from integers only."""
    return f"{cents // 100}.{cents % 100:02d}"


def case_row(oracle, name, term_start, term_end, cancel, first_term, cooling_off_days, premium_cents, fee_cents):
    """One CSV line. Every expected value comes from the oracle's `refund()` and `days()`."""
    premium, fee = money(premium_cents), money(fee_cents)
    end_exclusive = term_end + timedelta(days=1)
    term_days = oracle.days(term_start, end_exclusive)
    unexpired_days = oracle.days(cancel, end_exclusive)
    cooling_off = first_term and oracle.days(term_start, cancel) < cooling_off_days
    amount = oracle.refund(premium, term_start, term_end, cancel, fee, effective=term_start,
                           cooling_off=cooling_off_days if first_term else 0)
    return ",".join([
        name, term_start.isoformat(), term_end.isoformat(), cancel.isoformat(),
        "true" if first_term else "false", str(cooling_off_days), premium, fee,
        str(term_days), str(unexpired_days), "true" if cooling_off else "false", str(amount),
    ])


def boundary_rows(oracle):
    """The cases that sit on an edge of the formula, in a fixed order."""
    year = (date(2026, 11, 1), date(2027, 10, 31))  # the AC-08.1 term, 365 days
    rows = []

    def add(name, term, cancel, first_term, cooling_off_days, premium_cents, fee_cents):
        rows.append(case_row(oracle, name, term[0], term[1], cancel, first_term, cooling_off_days, premium_cents,
                             fee_cents))

    # The first day and the last day of the term, pro-rata (no cooling-off).
    add("term-first-day", year, year[0], True, 0, 685000, 50000)
    add("term-last-day", year, year[1], True, 0, 685000, 50000)
    add("term-last-day-renewal", year, year[1], False, 15, 685000, 50000)
    # Cooling-off of 15 days at -1, 0 and +1 days from the edge, for a first term and then for a renewal term.
    for label, offset in (("before-edge", -1), ("on-edge", 0), ("after-edge", 1)):
        cancel = year[0] + timedelta(days=15 + offset)
        add(f"cooling-off-15-first-{label}", year, cancel, True, 15, 685000, 50000)
    for label, offset in (("before-edge", -1), ("on-edge", 0), ("after-edge", 1)):
        cancel = year[0] + timedelta(days=15 + offset)
        add(f"cooling-off-15-renewal-{label}", year, cancel, False, 15, 685000, 50000)
    # The spec examples (AC-08.1 to AC-08.3).
    add("spec-ac-08-1", year, date(2027, 2, 9), True, 15, 685000, 50000)
    add("spec-ac-08-2", year, date(2026, 11, 11), True, 15, 685000, 50000)
    add("spec-ac-08-3", year, date(2027, 10, 31), True, 15, 685000, 50000)
    # A fee above the pro-rata amount floors the refund at 0.00; fee 0.00 refunds the whole pro-rata share.
    add("fee-above-pro-rata", year, date(2027, 9, 30), False, 0, 685000, 50000)
    add("fee-equals-pro-rata-share", year, date(2027, 10, 31), False, 0, 36500, 100)
    add("fee-zero", year, date(2027, 2, 9), False, 0, 685000, 0)
    add("fee-zero-first-day", year, year[0], False, 0, 685000, 0)
    # The smallest and the largest premium.
    add("premium-0-00", year, date(2027, 2, 9), False, 0, 0, 0)
    add("premium-0-00-with-fee", year, date(2027, 2, 9), False, 0, 0, 50000)
    add("premium-0-01", year, date(2027, 2, 9), False, 0, 1, 0)
    add("premium-0-01-cooling-off", year, date(2026, 11, 2), True, 15, 1, 0)
    add("premium-9999999-99", year, date(2027, 2, 9), False, 0, 999999999, 0)
    add("premium-9999999-99-with-fee", year, date(2027, 2, 9), False, 0, 999999999, 99999999)
    add("premium-9999999-99-cooling-off", year, date(2026, 11, 2), True, 15, 999999999, 50000)
    # A 1-month term, a 24-month term and a 366-day leap term.
    one_month = (date(2026, 11, 1), date(2026, 11, 30))
    add("one-month-first-day", one_month, one_month[0], False, 0, 120000, 5000)
    add("one-month-mid", one_month, date(2026, 11, 16), False, 0, 120000, 5000)
    add("one-month-last-day", one_month, one_month[1], False, 0, 120000, 5000)
    two_years = (date(2026, 11, 1), plus_months(date(2026, 11, 1), 24) - timedelta(days=1))
    add("24-months-first-day", two_years, two_years[0], False, 0, 1370000, 50000)
    add("24-months-mid", two_years, date(2027, 11, 1), False, 0, 1370000, 50000)
    add("24-months-last-day", two_years, two_years[1], False, 0, 1370000, 50000)
    add("24-months-cooling-off", two_years, date(2026, 11, 20), True, 30, 1370000, 50000)
    leap = (date(2027, 3, 1), date(2028, 2, 29))
    add("leap-term-first-day", leap, leap[0], False, 0, 730000, 25000)
    add("leap-term-leap-day", leap, date(2028, 2, 29), False, 0, 730000, 25000)
    add("leap-term-mid", leap, date(2027, 9, 1), False, 0, 730000, 25000)
    add("leap-term-cooling-off-edge", leap, leap[0] + timedelta(days=14), True, 15, 730000, 25000)
    # Exact half-cent ties: 15 of 30 days of 0.01 is 0.005, and 15 of 30 days of 100.01 is 50.005. HALF_UP rounds both
    # up, where HALF_EVEN would round the second down (the digit before the tie is even).
    add("half-cent-tie-0-01", one_month, date(2026, 11, 16), False, 0, 1, 0)
    add("half-cent-tie-100-01", one_month, date(2026, 11, 16), False, 0, 10001, 0)
    add("half-cent-tie-100-03-fee", one_month, date(2026, 11, 16), False, 0, 10003, 1)
    return rows


def random_rows(oracle, count):
    """Seeded random cases. Money is built from integer cents, never from a binary float."""
    rng = random.Random(SEED)

    def below(bound):
        return rng.getrandbits(64) % bound

    rows = []
    for index in range(1, count + 1):
        start = date(2026, 1, 1) + timedelta(days=below(3650))
        end = plus_months(start, 1 + below(24)) - timedelta(days=1)
        term_days = (end + timedelta(days=1) - start).days
        cancel = start + timedelta(days=below(term_days))
        premium_cents = below(100_000) if below(4) == 0 else below(1_000_000_000)
        fee_kind = below(10)
        if fee_kind == 0:
            fee_cents = 0
        elif fee_kind < 6:
            fee_cents = below(100_000)
        elif fee_kind < 8:
            fee_cents = premium_cents * below(121) // 100  # often above the pro-rata share: floors at 0.00
        else:
            fee_cents = below(50_000_000)
        first_term = below(2) == 0
        rows.append(case_row(oracle, f"random-{index:04d}", start, end, cancel, first_term, below(31), premium_cents,
                             fee_cents))
    return rows


def generate():
    oracle = load_oracle()
    rows = boundary_rows(oracle)
    rows += random_rows(oracle, ROWS - len(rows))
    if len(rows) != ROWS:
        raise SystemExit(f"expected {ROWS} rows, built {len(rows)}")
    return [HEADER] + rows


def check(path):
    """0 when the file equals a fresh generation (line endings normalised), 1 with the first differing row otherwise."""
    expected = generate()
    if not path.is_file():
        print(f"oracle fixture missing: {shown(path)}", file=sys.stderr)
        return 1
    actual = path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
    if actual and actual[-1] == "":
        actual.pop()
    for number in range(max(len(expected), len(actual))):
        want = expected[number] if number < len(expected) else "<no row>"
        have = actual[number] if number < len(actual) else "<no row>"
        if want != have:
            print(f"oracle fixture differs from a fresh generation at line {number + 1}:", file=sys.stderr)
            print(f"  committed: {have}", file=sys.stderr)
            print(f"  generated: {want}", file=sys.stderr)
            return 1
    print(f"oracle fixture OK: {len(expected) - 1} rows equal a fresh generation ({shown(path)})")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--check", action="store_true", help="compare the fixture with a fresh generation")
    parser.add_argument("--file", type=Path, default=FIXTURE, help="the fixture path (default: the committed one)")
    args = parser.parse_args(argv)
    path = args.file.resolve()
    if args.check:
        return check(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(generate()) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {ROWS} rows to {shown(path)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
