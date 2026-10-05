"""One-time exact reviewed patch; refuses stale baseline or altered bytes. Never rerun on later code."""
from pathlib import Path
import base64, hashlib, json, subprocess, zlib
root=Path(__file__).resolve().parents[2]
data=json.loads((root/'tools/changes/tunnel415-stage1.json').read_text())
for name,hashes in data['files'].items():
    p=root/name
    assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==hashes['before'], 'Unexpected baseline: '+name
patch=zlib.decompress(base64.b64decode(data['patch_zlib_base64'],validate=True))
subprocess.run(['git','apply','--unidiff-zero','--check','-'],input=patch,cwd=root,check=True)
subprocess.run(['git','apply','--unidiff-zero','-'],input=patch,cwd=root,check=True)
for name,hashes in data['files'].items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==hashes['after'], 'Output mismatch: '+name
for name,sha in {'src/main/java/com/sora/splineroads/core/RoadTunnelSpace.java':'4d42aaeaf5f245bd08649089932ff9c8960b27223030310afc9d948000e643a1','src/main/java/com/sora/splineroads/world/TunnelTerrainSpace.java':'920c96494b468b2e166bfa9bd4c692e6174d04a5d70cdeb2956e5ebb7b8cedf4'}.items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==sha, 'New source mismatch: '+name
print('All seven production source changes preserved byte-exactly; no terrain rendering or threading changes.')
