#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_closure418.sh
CP=build/tunnel406/core/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Rail419Validation.java src/validation/java/com/sora/splineroads/Rail419MarkingValidation.java
for TEST in Rail419Validation Rail419MarkingValidation; do
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$TEST" | tee "build/checkpoint-logs/$TEST.txt"
done
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Arrival417ModelValidation.java tools/model-validation/com/sora/splineroads/world/Rail419ModelValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Rail419ModelValidation | tee build/checkpoint-logs/Rail419ModelValidation.txt
python - <<'PY'
from pathlib import Path
core=Path('src/main/java/com/sora/splineroads/core');world=Path('src/main/java/com/sora/splineroads/world')
s=(world/'StructurePlanner.java').read_text()
assert 'new RoadRailJoin(' in s and 'RoadSurface.higherPriority' in s
assert 'railSpans' in s and 'railJoint' in s and 'railPost' in s
assert 'ground.railSpans' in (core/'RoadStructures.java').read_text()
assert 'dash || closedSlotBoundary(' in (core/'RoadSurface.java').read_text()
print('PASS: exact production rail/Part-frame/marking bindings, not a real world/renderer test')
PY
