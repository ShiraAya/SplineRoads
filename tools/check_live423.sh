#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_q2final422.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Live423Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -cp "$CP" com.sora.splineroads.Live423Validation | tee build/checkpoint-logs/Live423Validation.txt
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Live423ModelValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -cp "$CP" com.sora.splineroads.world.Live423ModelValidation | tee build/checkpoint-logs/Live423ModelValidation.txt
python - <<'PY' | tee build/checkpoint-logs/Live423PreviewBindings.txt
from pathlib import Path
r=Path('src/main/java/com/sora/splineroads')
s=(r/'world/RoadData.java').read_text();start=s.index('private void replaceBatch(',s.index('previewAssembly('));end=s.index('if(previewResult!=null)',start)
assert 'replaceBatch(level,player,built,removed,true' in s[s.index('previewAssembly('):start]
assert 'LaneTopology.reconcile' in s[start:end] and 'StructurePlanner.plan' in s[start:end]
assert 'level.setBlock(' not in s[start:end] and 'index.put(' not in s[start:end]
a=(r/'world/LaneRamps.java').read_text();assert 'previewAssembly(' in a and 'WorldRevision' in a
u=(r/'client/LaneRampScreen.java').read_text();fail=u[u.index('public void failed('):u.index('@Override public void onClose')]
assert 'checkedMesh=null' in fail and 'ClientRoads.preview=null' in fail and 'ClientRoads.nodePreviews=List.of()' in fail and 'token=null' in fail
assert 'FLOW(' in (r/'core/LanePoints.java').read_text()
print('PASS: shared full pre-write transaction preflight and stale/failed preview invalidation are bound in production source. STATIC only; not live server/GUI/permissions/world-write execution.')
PY
