#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/ramp39-core"}
mkdir -p "$OUT/classes" "$OUT/logs"
mapfile -d '' CORE < <(find "$ROOT/src/main/java/com/sora/splineroads/core" -name '*.java' ! -name RoadSignCatalog.java -print0)
TESTS=(EndpointV2Validation LanePointSnapValidation LaneInteractionValidation LaneRampV2Validation LaneRampWorkflowValidation Ramp39Validation)
SOURCES=()
for test in "${TESTS[@]}"; do SOURCES+=("$ROOT/src/validation/java/com/sora/splineroads/$test.java"); done
javac --release 17 -encoding UTF-8 -d "$OUT/classes" "${CORE[@]}" "$ROOT/tools/core-test-support/com/sora/splineroads/core/RoadSignCatalog.java" "${SOURCES[@]}" > "$OUT/logs/compile.log" 2>&1
for test in "${TESTS[@]}"; do
 java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/classes:$ROOT/src/main/resources" "com.sora.splineroads.$test" | tee "$OUT/logs/$test.log"
done
printf '\nCORE TESTS ONLY: Forge, NBT, world transactions, client rendering and the sign catalog are not validated by this runner.\n'
