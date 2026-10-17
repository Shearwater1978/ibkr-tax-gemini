# tools/generate_parity_fixtures.py
#
# Regenerates the expected outputs of the parity fixtures from the Python reference
# (src/parity_reference.py): parser records and source keys for every valid Flex
# Query fixture, and open FIFO lots for every scenario in fifo/scenarios.json.
#
#   python tools/generate_parity_fixtures.py [--fixtures tests/fixtures/parity] [--check]
#
# Fixtures are synthetic only. Review the diff after regenerating: expectations
# change only with an intentional fixture or reference change, never to make a
# mobile test pass.

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from src.parity_reference import (  # noqa: E402
    fifo_expectations,
    parsed_records,
    source_keys,
)


def _dump(data):
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def expected_files(fixtures: Path):
    """Maps each expectation file to the content the reference produces."""
    flex = fixtures / "flex-query"
    files = {}
    for csv in sorted(flex.glob("valid_*.csv")):
        files[flex / "expected" / f"{csv.stem}.json"] = _dump(parsed_records(csv))
        files[flex / "expected" / "source_keys" / f"{csv.stem}.json"] = _dump(
            source_keys(csv)
        )
    scenarios = json.loads(
        (fixtures / "fifo" / "scenarios.json").read_text(encoding="utf-8")
    )
    files[fixtures / "fifo" / "expected_open_lots.json"] = _dump(
        fifo_expectations(scenarios)
    )
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, default=Path("tests/fixtures/parity"))
    parser.add_argument(
        "--check",
        action="store_true",
        help="only report files whose content differs; exit 1 if any do",
    )
    args = parser.parse_args()

    differing = []
    for path, content in expected_files(args.fixtures).items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        current_data = json.loads(current) if current else None
        if current_data == json.loads(content):
            continue
        differing.append(path)
        if not args.check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content.encode("utf-8"))

    for path in differing:
        print(("differs: " if args.check else "updated: ") + str(path))
    if not differing:
        print("all parity expectations match the Python reference")
    return 1 if args.check and differing else 0


if __name__ == "__main__":
    sys.exit(main())
