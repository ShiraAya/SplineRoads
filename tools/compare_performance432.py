#!/usr/bin/env python3
"""Evaluate all repetitions; do not confuse functional CI success with performance acceptance."""
import json, re, statistics, sys
from collections import Counter
from pathlib import Path
pattern = re.compile(r'BENCH432 kind=(\w+) run=(\d+) plan=([\d.]+) preview=([\d.]+) build=([\d.]+) total=([\d.]+) cells=(\d+) shape=(-?\d+)')
def read(path):
    text = Path(path).read_text(errors='replace')
    assert 'All 2 required tests passed' in text, f'{path}: actual tests did not pass'
    rows = {}
    for match in pattern.finditer(text):
        kind, run, *values = match.groups()
        key = (kind, int(run))
        assert key not in rows, f'duplicate benchmark {key}'
        rows[key] = dict(zip(('plan', 'preview', 'build', 'total'), map(float, values[:4]))) | {'cells': int(values[4]), 'shape': int(values[5])}
    assert len(rows) == 8 and all((k, i) in rows for k in ('ADD', 'TEMPORARY') for i in range(4)), 'Missing repeated measurements'
    shapes = Counter(re.findall(r'SHAPE432 start=.*', text))
    assert len(shapes) > 0, 'No full assembly collision signatures'
    return rows, shapes
baseline, before_shapes = read(sys.argv[1])
optimized, after_shapes = read(sys.argv[2])
errors = []
if before_shapes != after_shapes:
    errors.append('Full preview assembly collision fingerprints differ')
for key in baseline:
    for field in ('cells', 'shape'):
        if baseline[key][field] != optimized[key][field]:
            errors.append(f'{key}: {field} differs')
summary = {}
for kind in ('ADD', 'TEMPORARY'):
    summary[kind] = {}
    for stage in ('plan', 'preview', 'build', 'total'):
        # Report cold run separately, and median of all three subsequent runs.
        before = statistics.median(baseline[(kind, i)][stage] for i in (1, 2, 3))
        after = statistics.median(optimized[(kind, i)][stage] for i in (1, 2, 3))
        ratio = after / before
        summary[kind][stage] = {'baseline_ms': before, 'optimized_ms': after, 'ratio': ratio, 'cold_baseline_ms': baseline[(kind, 0)][stage], 'cold_optimized_ms': optimized[(kind, 0)][stage]}
        print(f'{kind:10s} {stage:7s} baseline={before:.3f} optimized={after:.3f} ratio={ratio:.3f}')
        threshold = 0.90 if stage == 'total' else 0.98
        if ratio >= threshold:
            errors.append(f'{kind}/{stage}: ratio {ratio:.4f} must be below {threshold}')
output = {'summary': summary, 'accepted': not errors, 'errors': errors, 'baseline': {str(k): v for k, v in baseline.items()}, 'optimized': {str(k): v for k, v in optimized.items()}}
Path(sys.argv[3]).write_text(json.dumps(output, indent=2))
for error in errors:
    print('NOT ACCEPTED:', error)
if errors:
    sys.exit(1)
print('PERFORMANCE432_ACCEPTED: route, readonly preview, build and total all improved; all collision signatures identical')
