"""One-time reviewed 0.40.8 seam candidate; all existing file hashes must match."""
from pathlib import Path
import base64,hashlib,json,subprocess,zlib
root=Path(__file__).resolve().parents[2]
data=json.loads((root/'tools/changes/tunnel415-stage2.json').read_text())
for name,h in data['files'].items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==h['before'],'Unexpected baseline: '+name
patch=zlib.decompress(base64.b64decode(data['patch_zlib_base64'],validate=True))
subprocess.run(['git','apply','--unidiff-zero','--check','-'],input=patch,cwd=root,check=True)
subprocess.run(['git','apply','--unidiff-zero','-'],input=patch,cwd=root,check=True)
for name,h in data['files'].items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==h['after'],'Output mismatch: '+name
print('Exact reviewed 0.40.8 tunnel/common-seam candidate preserved; save/protocol unchanged.')
