#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/terrain447"; CORE="$ROOT/build/ramp39-core/classes"; MODEL="$ROOT/build/ramp39-model/classes"
mkdir -p "$OUT/classes" "$OUT/logs"
mapfile -d '' SUPPORT < <(find "$ROOT/tools/perf40-index-support" "$ROOT/tools/perf40-render-support" "$ROOT/tools/hotfix401-render-support" -name '*.java' -print0)
javac --release 17 -encoding UTF-8 -cp "$CORE:$MODEL" -d "$OUT/classes" "${SUPPORT[@]}" \
 "$ROOT/src/main/java/com/sora/splineroads/world/DeferredMap.java" "$ROOT/src/main/java/com/sora/splineroads/world/RoadIndex.java" \
 "$ROOT/src/main/java/com/sora/splineroads/config/RoadClientConfig.java" "$ROOT/src/main/java/com/sora/splineroads/client/ShaderPackState.java" \
 "$ROOT/src/main/java/com/sora/splineroads/client/RoadTerrainModels.java" \
 "$ROOT/tools/perf40-validation/com/sora/splineroads/client/Terrain40ModelValidation.java" \
 "$ROOT/tools/hotfix401-validation/com/sora/splineroads/client/ShaderBackend401Validation.java" \
 "$ROOT/tools/hotfix401-validation/com/sora/splineroads/client/TerrainStreaming401Validation.java" \
 "$ROOT/tools/hotfix401-validation/com/sora/splineroads/client/Terrain447Validation.java" > "$OUT/logs/compile.log" 2>&1
for name in Terrain40ModelValidation ShaderBackend401Validation TerrainStreaming401Validation Terrain447Validation; do
 java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/classes:$CORE:$MODEL:$ROOT/src/main/resources" "com.sora.splineroads.client.$name" | tee "$OUT/logs/$name.log"
done
