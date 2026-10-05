#!/usr/bin/env bash
# Complete prior regressions/Forge, then focused P1 fixtures. Adapters stay outside the mod JAR.
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
mkdir -p build/checkpoint-logs
bash tools/check_release0406.sh
CP=build/tunnel406/core/classes:src/main/resources
for test in Problem2GeometryValidation Problem2WaterPolicyValidation; do
  javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes "src/validation/java/com/sora/splineroads/$test.java"
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$test" | tee "build/checkpoint-logs/$test.txt"
done
CP=build/tunnel406/index/classes:build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/index/classes src/main/java/com/sora/splineroads/world/RoadInteractions.java src/main/java/com/sora/splineroads/world/TunnelShellValidation.java tools/problem2-validation/com/sora/splineroads/world/Problem2ShellValidation.java tools/problem2-validation/com/sora/splineroads/world/Problem2WaterCellsValidation.java
for test in Problem2ShellValidation Problem2WaterCellsValidation; do
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.world.$test" | tee "build/checkpoint-logs/$test.txt"
done
python - <<'PY'
from pathlib import Path
s=Path('src/main/java/com/sora/splineroads/world/RoadData.java').read_text()
assert 'collisionState(key, state, body.get(key), sidewalks.get(key),dry.contains(key))' in s
assert 'collisionState(source, Blocks.AIR.defaultBlockState(), body.get(source),sidewalks.get(source),dry.contains(source))' in s
assert 'boolean dryInterior=index.inChunk(new net.minecraft.world.level.ChunkPos(pos).toLong()).stream()' in s
assert 'import com.sora.splineroads.core.RoadConnectionChecks;' in s and 'import com.sora.splineroads.core.RoadWaterPolicy;' in s
tube=Path('src/main/java/com/sora/splineroads/world/TunnelAir.java').read_text()
assert 'canPlaceLiquid' in tube and 'placeLiquid' in tube and tube.count('return false;')==2
print('PASS: final-world dry union reaches water policy; TunnelAir retains liquid exclusion; static binding check only')
PY
