#!/usr/bin/env python3
"""Summarize SR phase timings without copying private Minecraft log contents.

Enable -Dsr.profile=true, reproduce road edits, then run:
  python tools/summarize_profile.py latest.log --output sr-profile-summary.json
Input may be UTF-8 .log/.txt or gzip. No network access or third-party modules.
"""
from __future__ import annotations
import argparse
import gzip
import json
import math
from pathlib import Path
import re
import statistics
import sys
from collections import defaultdict

NUMBER = r"[0-9][0-9,.\s\u00a0]*"
EDIT = re.compile(r"\bSR ([A-Za-z0-9_-]+): ("+NUMBER+r") ms, roads=("+NUMBER+r"), requested=("+NUMBER+r"), phases\(ms\)=\{([^}]*)\}")
LOAD = re.compile(r"\bSR load: ("+NUMBER+r") ms, roads=("+NUMBER+r")\s*$")
PHASE = re.compile(r"([A-Za-z_][A-Za-z0-9_]{0,63})=([+\-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+\-]?\d+)?)\Z")


def integer(value: str) -> int:
    # The Java logger receives Math.round(total), so comma/dot/space are grouping,
    # not decimal fractions. Phase values use Double.toString and a decimal dot.
    return int(re.sub(r"[,.\s\u00a0]", "", value))


def parse(line: str):
    match = EDIT.search(line)
    if match:
        operation, total, roads, requested, text = match.groups()
        if operation not in {"connect", "edit", "edit_node"}:
            return None
        phases = {}
        for raw in text.split(",") if text.strip() else []:
            item = PHASE.fullmatch(raw.strip())
            if item is None:
                raise ValueError("Malformed phase field")
            name, value = item.groups()
            number = float(value)
            if not math.isfinite(number) or number < 0 or name in phases:
                raise ValueError("Invalid or duplicate phase value")
            phases[name] = number
        return operation, integer(total), integer(roads), integer(requested), phases
    match = LOAD.search(line)
    if match:
        total, roads = match.groups()
        return "load", integer(total), integer(roads), None, {}
    return None


def stats(values):
    ordered = sorted(values)
    return {"count": len(ordered), "median": statistics.median(ordered),
            "p95_nearest_rank": ordered[max(0, math.ceil(.95*len(ordered))-1)],
            "min": ordered[0], "max": ordered[-1]}


def summarize(lines):
    groups = defaultdict(list)
    malformed = 0
    for line in lines:
        try:
            row = parse(line)
        except (ValueError, OverflowError):
            malformed += 1
            continue
        if row is not None:
            groups[row[0]].append(row)
    result = {}
    for operation, rows in sorted(groups.items()):
        phases = defaultdict(list)
        requested = []
        for _, _, _, count, values in rows:
            if count is not None:
                requested.append(count)
            for name, value in values.items():
                phases[name].append(value)
        result[operation] = {
            "total_ms": stats([r[1] for r in rows]),
            "world_road_count": stats([r[2] for r in rows]),
            "requested_road_count": stats(requested) if requested else None,
            "phase_ms": {name: stats(values) for name, values in
                         sorted(phases.items(), key=lambda x: statistics.median(x[1]), reverse=True)},
        }
    return {"records": sum(map(len, groups.values())), "malformed_records": malformed,
            "operations": result,
            "interpretation": [
                "Operations may be nested: do not add connect and edit totals together.",
                "Timings can include failed edits. The logger does not encode success/failure.",
                "Missing phases are not zero-duration samples. Each phase has its own count.",
                "Total milliseconds are rounded; phases are not. Percentages need not sum to 100%.",
                "Without sr.profile=true the logger preferentially emits slow operations; results are not a complete workload sample.",
                "No source file paths, usernames, coordinates or raw log lines are included."]}


def read_lines(paths):
    for path in paths:
        opener = gzip.open if path.suffix.lower() == ".gz" else open
        with opener(path, "rt", encoding="utf-8-sig", errors="replace") as handle:
            yield from handle


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", type=Path, nargs="+")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.output and args.output.resolve() in {p.resolve() for p in args.logs}:
            raise ValueError("Output must not overwrite an input log")
        report = summarize(read_lines(args.logs))
        if report["records"] == 0:
            print("No recognized SR timing records. Enable -Dsr.profile=true and reproduce an edit.", file=sys.stderr)
            return 2
        text = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        if args.output:
            args.output.write_text(text, encoding="utf-8")
            print(f"Wrote {report['records']} timing records as aggregated statistics. No raw log content copied.")
        else:
            print(text, end="")
        return 0
    except (OSError, ValueError) as exc:
        print(f"Cannot summarize: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
