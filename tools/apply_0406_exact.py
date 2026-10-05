#!/usr/bin/env python3
"""Apply saved reviewed line replacements only to their exact SHA256 baseline.
One-time checkpoint utility; never run against an arbitrary future revision."""
from pathlib import Path
import hashlib,json,sys
manifest=Path(sys.argv[1]);prepared=[]
for entry in json.loads(manifest.read_text(encoding='utf8')):
    path=Path(entry['path'])
    if path.is_absolute() or '..' in path.parts or path.parts[0] not in {'src','build.gradle'}:raise SystemExit('unsafe path')
    before=path.read_bytes()
    if hashlib.sha256(before).hexdigest()!=entry['before']:raise SystemExit('baseline changed: '+str(path))
    lines=before.decode('utf8').splitlines(keepends=True)
    for change in reversed(entry['edits']):lines[change['start']:change['end']]=change['lines']
    after=''.join(lines).encode('utf8')
    if hashlib.sha256(after).hexdigest()!=entry['after']:raise SystemExit('after checksum mismatch: '+str(path))
    prepared.append((path,after))
for path,data in prepared:path.write_bytes(data)
print('PASS: exact reviewed production changes applied; all preimages checked before writes')
