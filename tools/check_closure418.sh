#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_arrival417.sh
CP=build/tunnel406/core/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Closure418Validation.java src/validation/java/com/sora/splineroads/Closure418GroundDepthValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.Closure418Validation | tee build/checkpoint-logs/Closure418Validation.txt
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.Closure418GroundDepthValidation | tee build/checkpoint-logs/Closure418GroundDepthValidation.txt
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Closure418ModelValidation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Closure418ModelValidation | tee build/checkpoint-logs/Closure418ModelValidation.txt
python - <<'PY'
from pathlib import Path
core=Path('src/main/java/com/sora/splineroads/core');world=Path('src/main/java/com/sora/splineroads/world')
assert 'out.addAll(LaneClosureLandscape.plan(mesh,ground));' in (core/'RoadStructures.java').read_text()
assert 'LaneDeck.caps(mesh)' in (core/'RoadSurface.java').read_text()
assert 'withProtectedMerge().withRectangularClosure()' in (world/'LaneRamps.java').read_text()
text=(world/'LaneRamps.java').read_text();report=text[text.index('reply.putBoolean("TemporaryClosure"'):text.index('if(LaneTopology.metadata(r).link().closesTarget())')]
assert 'staging.get(to.road())' not in report
assert 'RectangularClosure' in text and 'cut.rectangular()?length:length-cut.transition()' in text
print('PASS: production structure/cap/generation and source report bindings; static check, not GUI/world execution')
PY
