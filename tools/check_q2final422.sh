#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_grade421.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Q2Close422Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.Q2Close422Validation | tee build/checkpoint-logs/Q2Close422Validation.txt
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Q2Close422ModelValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Q2Close422ModelValidation | tee build/checkpoint-logs/Q2Close422ModelValidation.txt
python - <<'PY' | tee build/checkpoint-logs/Q2Close422Bindings.txt
from pathlib import Path
import json
r=Path('src/main/java/com/sora/splineroads')
t=(r/'world/LanePointTool.java').read_text();u=(r/'client/LanePointScreen.java').read_text();c=(r/'world/LanePointCodec.java').read_text()
assert 'MergeLength' in t and 'mergeLane()' in u and 'MergeLength' in c
assert 'Signature' in t and 'RoadData.requireOwner' in t and 'LaneMerge.sameDefinitions' in t
assert '此空位已有补入匝道' in t and 'cut.replacement()' in t and 'LaneDeletes.requireConfirmation' in t
shader=Path('src/main/resources/assets/splineroads/shaders/core/road_world_solid.vsh').read_text()
j=json.loads(Path('src/main/resources/assets/splineroads/shaders/core/road_world_solid.json').read_text())
assert j['vertex']=='splineroads:road_world_solid' and j['fragment']=='rendertype_entity_cutout_no_cull'
assert 'ModelViewMat * vec4(Position, 1.0)' in shader and 'IViewRotMat * eye.xyz' in shader
assert 'max(length(relative.xz), abs(relative.y))' in shader
assert 'ShaderPackState.current().active()' in (r/'client/RoadShaders.java').read_text()
assert 'RoadShaders::solid' in (r/'client/RoadRenderTypes.java').read_text()
assert all(len(v['values'])==v['count'] for v in j['uniforms'])
print('PASS: actual GUI/server merge action and ownership/stale/dependency guards; custom VBO shader layout/fog uniforms/resource path. Static bindings only, NOT actual UI/GPU/GLSL runtime.')
PY
