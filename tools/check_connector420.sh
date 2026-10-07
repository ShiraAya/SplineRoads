#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_rail419.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/model/classes tools/model-validation/com/sora/splineroads/world/Connector420ModelValidation.java
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Connector420ModelValidation | tee build/checkpoint-logs/Connector420ModelValidation.txt
python - <<'PY'
from pathlib import Path
c=Path('src/main/java/com/sora/splineroads/client/RoadScreen.java').read_text()
assert '自由匝道（高速）' in c and '自由匝道（普通）' in c
assert 'Style.C1_RAMP' in c and 'Style.C1_HIGHWAY_RAMP' in c
assert 'C1_RAMP' in Path('src/main/java/com/sora/splineroads/world/LaneRamps.java').read_text()
print('PASS: real editor type selection/labels and connector generator wiring; static binding check, NOT GUI interaction')
PY
