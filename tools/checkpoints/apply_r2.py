#!/usr/bin/env python3
"""One-shot reviewed edit transport. Verify every input and output before staging.
No network, credentials, dynamic source retrieval, or arbitrary file path arguments.
The runner does real tests and Forge compilation BEFORE its normal non-force push.
"""
from pathlib import Path
import hashlib
import json
import runpy
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
manifest = json.loads((root / 'docs/checkpoints/R2-source-manifest.json').read_text())
allowed = {item['path'] for item in manifest['files']}
assert len(allowed) == 11
assert all(path.startswith('src/main/java/com/sora/splineroads/') and '..' not in Path(path).parts for path in allowed)

def verify(which):
    for item in manifest['files']:
        path = root / item['path']
        assert path.is_file() and not path.is_symlink(), item['path']
        value = hashlib.sha256(path.read_bytes()).hexdigest()
        assert value == item[which + '_sha256'], (which, item['path'], value)

if len(sys.argv) > 1 and sys.argv[1] == '--verify-after':
    verify('after')
else:
    verify('before')
    import os
    os.chdir(root)
    runpy.run_path(str(root / 'tools/checkpoints/r2_transform.py'), run_name='__main__')
    verify('after')
    changed = subprocess.check_output(['git', 'diff', '--name-only'], cwd=root, text=True).splitlines()
    assert set(changed) == allowed, changed
    subprocess.run(['git', 'add', '--', *sorted(allowed)], cwd=root, check=True)
    for item in manifest['files']:
        sha = subprocess.check_output(['git', 'rev-parse', ':' + item['path']], cwd=root, text=True).strip()
        assert sha == item['after_git_blob'], item['path']
print('Verified 11 reviewed R2 source files, byte-for-byte. This command alone does not mean tests or GitHub push passed.')
