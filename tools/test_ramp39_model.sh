#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/ramp39-model"}
CORE=${2:-"$ROOT/build/ramp39-core"}
# Always rebuild actual core sources: an existing class file may be stale after edits.
bash "$ROOT/tools/test_ramp39.sh" "$CORE"
mkdir -p "$OUT/classes" "$OUT/logs"
mapfile -d '' SUPPORT < <(find "$ROOT/tools/model-test-support" -name '*.java' ! -name 'RampJunctions.java' -print0)
mapfile -d '' TESTS < <(find "$ROOT/tools/model-validation" -name '*.java' -print0)
SOURCES=()
for name in RoadRecord RoadContinuations LanePointCodec RoadSignCodec AttachedPointCodec JunctionCodec LaneTopology LaneRoadChain LaneCrossSections LaneRamps RampJunctions RoadInteractions; do
 SOURCES+=("$ROOT/src/main/java/com/sora/splineroads/world/$name.java")
done
for name in LanePointScreen LaneRampScreen; do SOURCES+=("$ROOT/src/main/java/com/sora/splineroads/client/$name.java"); done
javac --release 17 -encoding UTF-8 -cp "$CORE/classes" -d "$OUT/classes" "${SUPPORT[@]}" "${SOURCES[@]}" "${TESTS[@]}" > "$OUT/logs/compile.log" 2>&1
for test in world.Ramp39ModelValidation world.Ramp39CompoundValidation client.Ramp39WidgetValidation; do
 LANG=C.UTF-8 java -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CORE/classes:$OUT/classes:$ROOT/src/main/resources" "com.sora.splineroads.$test" | tee "$OUT/logs/$test.log"
done
printf '\nMODEL TESTS ONLY: NBT map adapters are not Mojang NBT binary encoding; world writes, networking and GPU drawing throw. This is not a Forge/Minecraft test.\n'
