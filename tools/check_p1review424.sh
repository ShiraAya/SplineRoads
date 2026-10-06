#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_live423.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/P1Review424Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.P1Review424Validation | tee build/checkpoint-logs/P1Review424Validation.txt
python - <<'CHECK'
from pathlib import Path
r=Path('src/main/java/com/sora/splineroads')
server=(r/'world/LaneRamps.java').read_text();client=(r/'client/LaneRampScreen.java').read_text()
for key in ['GradeHorizontal','GradeAvailable','GradeMinimum','ActualGrade']:
    assert 'putDouble("'+key+'"' in server and 'getDouble("'+key+'"' in client
assert 'length,d[i],maxGrade)' in (r/'core/LaneRampPaths.java').read_text()
print('PASS: real capped-profile call and final-preview grade metrics are wired; static bindings, NOT live client/server execution.')
CHECK
