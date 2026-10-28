"""Independent exact-decimal oracle for PolicyForge money math.

Mirrors specs/quote-engine_spec.md, specs/endorsement_spec.md and
specs/renewal-cancellation_spec.md. Standard library only.
"""
import json
import sys
from datetime import date, timedelta
from decimal import Decimal as D, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RULES = ROOT / "backend" / "src" / "main" / "resources" / "rules"
SLUGS = {"TERM_LIFE": "term-life", "MOTOR": "motor", "HOUSEHOLD": "household"}

# v1 fallback tables (specs/quote-engine_spec.md) used before rule files exist
FALLBACK = {
    "TERM_LIFE": {
        "baseRatePerThousand": [[18, 30, "0.80"], [31, 40, "1.20"], [41, 50, "2.50"], [51, 60, "5.00"], [61, 65, "9.00"]],
        "smokerFactor": {"true": "1.50", "false": "1.00"},
        "termFactor": {"10": "1.00", "20": "1.10", "30": "1.25"},
        "policyFee": "250.00",
    },
    "MOTOR": {
        "baseRate": "0.025",
        "vehicleAgeFactor": [[0, 3, "1.00"], [4, 7, "1.10"], [8, 15, "1.25"]],
        "driverAgeFactor": [[18, 24, "1.30"], [25, 65, "1.00"], [66, 99, "1.15"]],
        "claimLoading": "0.20",
        "ncbDiscount": {"0": "0.00", "1": "0.20", "2": "0.25", "3": "0.35", "4": "0.45", "5": "0.50"},
        "useFactor": {"PRIVATE": "1.00", "COMMERCIAL": "1.40"},
        "policyFee": "150.00",
    },
    "HOUSEHOLD": {
        "buildingRate": "0.0015",
        "contentsRate": "0.0035",
        "constructionFactor": {"BRICK": "1.00", "CONCRETE": "0.95", "TIMBER": "1.30"},
        "floodFactor": {"LOW": "1.00", "MEDIUM": "1.15", "HIGH": "1.40"},
        "securityFactor": {"true": "0.90", "false": "1.00"},
        "policyFee": "100.00",
    },
}

I10 = D("0.0000000001")
C2 = D("0.01")


def mid(x):
    return x.quantize(I10, ROUND_HALF_UP)


def fin(x):
    return x.quantize(C2, ROUND_HALF_UP)


def band(table, value):
    """Bands are [min, max, factor] (list) or {"min","max","factor"} (dict); inclusive both ends."""
    for row in table:
        lo, hi, f = (row["min"], row["max"], row["factor"]) if isinstance(row, dict) else row
        if D(str(lo)) <= D(str(value)) <= D(str(hi)):
            return D(str(f))
    raise ValueError(f"value {value} outside all bands")


def key(v):
    return str(v).lower() if isinstance(v, bool) else str(v)


def load_rating(product, version=None):
    folder = RULES / SLUGS[product]
    files = sorted(folder.glob("v*.json"), key=lambda p: int(p.stem[1:])) if folder.exists() else []
    if version is not None:
        files = [f for f in files if f.stem == f"v{version}"]
    if files:
        return json.loads(files[-1].read_text(encoding="utf-8"))["rating"], files[-1].name
    return FALLBACK[product], "spec-v1-fallback"


def quote(product, inputs, rating):
    r = rating
    if product == "TERM_LIFE":
        p = mid(D(str(inputs["sumInsured"])) / 1000)
        p = mid(p * band(r["baseRatePerThousand"], inputs["age"]))
        p = mid(p * D(r["smokerFactor"][key(inputs["smoker"])]))
        p = mid(p * D(r["termFactor"][key(inputs["termYears"])]))
    elif product == "MOTOR":
        p = mid(D(str(inputs["vehicleValue"])) * D(r["baseRate"]))
        p = mid(p * band(r["vehicleAgeFactor"], inputs["vehicleAgeYears"]))
        p = mid(p * band(r["driverAgeFactor"], inputs["driverAge"]))
        p = mid(p * (1 + D(r["claimLoading"]) * int(inputs["claimsLast3Years"])))
        p = mid(p * (1 - D(r["ncbDiscount"][key(inputs["noClaimBonusYears"])])))
        p = mid(p * D(r["useFactor"][inputs["vehicleUse"]]))
    elif product == "HOUSEHOLD":
        p = mid(D(str(inputs["buildingSumInsured"])) * D(r["buildingRate"])
                + D(str(inputs["contentsSumInsured"])) * D(r["contentsRate"]))
        p = mid(p * D(r["constructionFactor"][inputs["constructionType"]]))
        p = mid(p * D(r["floodFactor"][inputs["floodZone"]]))
        p = mid(p * D(r["securityFactor"][key(inputs["hasSecuritySystem"])]))
    else:
        raise ValueError(product)
    return fin(p + D(r["policyFee"]))


def days(a, b):
    return (b - a).days


def refund(premium, term_start, term_end, cancel, fee, effective=None, cooling_off=0):
    effective = effective or term_start
    if days(effective, cancel) < cooling_off:
        return fin(D(premium))
    end_x = term_end + timedelta(days=1)
    pro = mid(mid(D(premium) * days(cancel, end_x)) / days(term_start, end_x))
    return fin(max(D(0), pro - D(fee)))


def adjust(old, new, term_start, term_end, eff):
    end_x = term_end + timedelta(days=1)
    return fin(mid(mid((D(new) - D(old)) * days(eff, end_x)) / days(term_start, end_x)))


def spec_examples():
    d = date.fromisoformat
    tl = {"age": 35, "smoker": False, "sumInsured": "5000000.00", "termYears": 20}
    mo = {"vehicleValue": "800000.00", "vehicleAgeYears": 2, "driverAge": 30, "claimsLast3Years": 0,
          "noClaimBonusYears": 2, "vehicleUse": "PRIVATE"}
    hh = {"buildingSumInsured": "6000000.00", "contentsSumInsured": "1000000.00", "constructionType": "BRICK",
          "floodZone": "LOW", "hasSecuritySystem": True}
    mo_v2 = dict(FALLBACK["MOTOR"], baseRate="0.030")
    cases = [
        ("AC-01.1", "6850.00", quote("TERM_LIFE", tl, FALLBACK["TERM_LIFE"])),
        ("AC-01.2", "15150.00", quote("MOTOR", mo, FALLBACK["MOTOR"])),
        ("AC-01.3", "11350.00", quote("HOUSEHOLD", hh, FALLBACK["HOUSEHOLD"])),
        ("AC-01.6", "18150.00", quote("MOTOR", mo, mo_v2)),
        ("AC-06.2 new premium", "9490.00", quote("TERM_LIFE", dict(tl, sumInsured="7000000.00"), FALLBACK["TERM_LIFE"])),
        ("AC-06.2 adjustment", "1330.85", adjust("6850.00", "9490.00", d("2026-11-01"), d("2027-10-31"), d("2027-05-01"))),
        ("AC-08.1", "4473.29", refund("6850.00", d("2026-11-01"), d("2027-10-31"), d("2027-02-09"), "500.00")),
        ("AC-08.2", "6850.00", refund("6850.00", d("2026-11-01"), d("2027-10-31"), d("2026-11-11"), "500.00", cooling_off=15)),
        ("AC-08.3", "0.00", refund("6850.00", d("2026-11-01"), d("2027-10-31"), d("2027-10-31"), "500.00")),
    ]
    ok = True
    print(f"{'case':<22}{'expected':>12}{'oracle':>12}  status")
    for name, expected, got in cases:
        status = "PASS" if D(expected) == got else "FAIL"
        ok &= status == "PASS"
        print(f"{name:<22}{expected:>12}{str(got):>12}  {status}")
    return 0 if ok else 1


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd = argv[0]
    if cmd == "spec-examples":
        return spec_examples()
    if cmd == "quote":
        product, inputs = argv[1], json.loads(argv[2])
        version = int(argv[argv.index("--version") + 1]) if "--version" in argv else None
        rating, source = load_rating(product, version)
        print(f"{quote(product, inputs, rating)}  (factors: {source})")
        return 0
    if cmd == "refund":
        premium, ts, te, c, fee = argv[1:6]
        print(refund(premium, date.fromisoformat(ts), date.fromisoformat(te), date.fromisoformat(c), fee))
        return 0
    if cmd == "adjust":
        old, new, ts, te, eff = argv[1:6]
        print(adjust(old, new, date.fromisoformat(ts), date.fromisoformat(te), date.fromisoformat(eff)))
        return 0
    print(f"unknown command {cmd}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
