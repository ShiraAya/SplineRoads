#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
# Existing connector/rail/closure/tunnel/caching regression includes complete Forge compilation.
bash tools/check_connector420_postreview.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Grade421Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.Grade421Validation | tee build/checkpoint-logs/Grade421Validation.txt
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Grade421ModelValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Grade421ModelValidation | tee build/checkpoint-logs/Grade421ModelValidation.txt
python - <<'PY'
from pathlib import Path
p=Path('src/main/java/com/sora/splineroads')
s=(p/'client/LaneRampScreen.java').read_text();c=(p/'world/LanePointCodec.java').read_text();world=(p/'world/LaneRamps.java').read_text()
assert 'gradeOverride=!gradeOverride;invalidate();rebuildWidgets();' in s
assert 'elevation,landing,gradeOverride)' in s and 'GradeLimit' in s
assert 't.putBoolean("GradeOverride",o.gradeOverride())' in c and 't.getBoolean("GradeOverride")' in c
assert 'LaneRampGrade.validate(mesh,gradeLimit(all,link))' in world
assert 'LaneRampHeights.solve(base,from,to,constraints,over,gradeLimit(all,link))' in world
print('PASS: real option/codec/preview/final-grade binding; UI state tested with adapters, not device/GPU interaction.')
PY
