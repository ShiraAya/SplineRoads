#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_sr427.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Finish428{,Interchange}Validation.java
for test in Finish428Validation Finish428InterchangeValidation; do
 java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$test" | tee "build/checkpoint-logs/$test.txt"
done
# Use production RoadIndex/DeferredMap, not the simpler planning map adapter.
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "build/sr425-index/classes:$CP" com.sora.splineroads.world.Finish428ModelValidation | tee build/checkpoint-logs/Finish428ModelValidation.txt
python - <<'PY'
from pathlib import Path
r=Path('src/main/java/com/sora/splineroads')
s=(r/'world/RoadData.java').read_text()
assert s.count('terrainFill,terrainOriginal,terrainCache,lookup')==2
assert 'RoadFoundation.source' in (r/'world/StructurePlanner.java').read_text()
assert 'RoadContinuations.reconcile(records(data),all,scope)' in (r/'world/LaneTopology.java').read_text()
assert 'RoadContinuations' in Path('tools/test_ramp39_model.sh').read_text()
for f in ['client/YJunctionScreen.java','client/InterchangeScreen.java']:
 assert 'new RoadLaneConfigScreen' in (r/f).read_text()
y=(r/'world/YJunctionTool.java').read_text()
assert '!t.getBoolean("LockedProfile"+i)&&command.contains("Profile"+i)' in y
print('PASS: terrain-history, transaction-local continuation and both specialized lane editors wired in production. Static binding only, not live GUI/network execution.')
PY
