#!/usr/bin/env bash
# Offline test layers, NOT a replacement for the dependency-backed Forge build / real GPU tests.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/perf40"}
CORE="$OUT/core"
MODEL="$OUT/model"
INDEX="$OUT/index"
RENDER="$OUT/render"
JAVA_FLAGS=(-Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xmx1500m -XX:ActiveProcessorCount=2)
printf 'JDK / host used by this run:\n'; java -version 2>&1
java "$ROOT/tools/SourceSyntax40.java" "$ROOT/src/main/java"
bash "$ROOT/tools/test_ramp39_model.sh" "$MODEL" "$CORE"
mkdir -p "$OUT/logs" "$INDEX/classes" "$RENDER/classes"
javac --release 17 -encoding UTF-8 -cp "$CORE/classes" -d "$CORE/classes" \
 "$ROOT/src/validation/java/com/sora/splineroads/Performance40Validation.java" \
 "$ROOT/src/validation/java/com/sora/splineroads/Geometry40Parity.java" > "$OUT/logs/core-compile.log" 2>&1
for test in Performance40Validation Geometry40Parity; do
 java "${JAVA_FLAGS[@]}" -cp "$CORE/classes:$ROOT/src/main/resources" "com.sora.splineroads.$test" | tee "$OUT/logs/$test.log"
done
mapfile -d '' INDEX_SUPPORT < <(find "$ROOT/tools/perf40-index-support" -name '*.java' -print0)
javac --release 17 -encoding UTF-8 -cp "$CORE/classes:$MODEL/classes" -d "$INDEX/classes" \
 "${INDEX_SUPPORT[@]}" \
 "$ROOT/src/main/java/com/sora/splineroads/world/DeferredMap.java" \
 "$ROOT/src/main/java/com/sora/splineroads/world/RoadIndex.java" \
 "$ROOT/tools/perf40-validation/com/sora/splineroads/world/Index40Validation.java" \
 > "$OUT/logs/index-compile.log" 2>&1
java "${JAVA_FLAGS[@]}" -cp "$INDEX/classes:$CORE/classes:$MODEL/classes:$ROOT/src/main/resources" \
 com.sora.splineroads.world.Index40Validation | tee "$OUT/logs/Index40Validation.log"
mapfile -d '' RENDER_SUPPORT < <(find "$ROOT/tools/perf40-render-support" -name '*.java' -print0)
javac --release 17 -encoding UTF-8 -cp "$INDEX/classes:$CORE/classes:$MODEL/classes" -d "$RENDER/classes" \
 "${RENDER_SUPPORT[@]}" \
 "$ROOT/src/main/java/com/sora/splineroads/config/RoadClientConfig.java" \
 "$ROOT/src/main/java/com/sora/splineroads/client/ShaderPackState.java" \
 "$ROOT/src/main/java/com/sora/splineroads/client/RoadTerrainModels.java" \
 "$ROOT/tools/perf40-validation/com/sora/splineroads/client/Terrain40ModelValidation.java" \
 > "$OUT/logs/render-compile.log" 2>&1
java "${JAVA_FLAGS[@]}" -cp "$RENDER/classes:$INDEX/classes:$CORE/classes:$MODEL/classes:$ROOT/src/main/resources" \
 com.sora.splineroads.client.Terrain40ModelValidation | tee "$OUT/logs/Terrain40ModelValidation.log"
javac --release 17 -encoding UTF-8 -cp "$CORE/classes:$MODEL/classes" -d "$MODEL/classes" \
 "$ROOT/tools/perf40-validation/com/sora/splineroads/world/Performance40Benchmark.java" > "$OUT/logs/benchmark-compile.log" 2>&1
java "${JAVA_FLAGS[@]}" -cp "$CORE/classes:$MODEL/classes:$ROOT/src/main/resources" \
 com.sora.splineroads.world.Performance40Benchmark | tee "$OUT/logs/Performance40Benchmark.log"
printf '\nPASS: offline core / production-index-local-queries / terrain-publication-model tests.\n'
printf 'Forge/MC API adapters live ONLY under tools/. No real chunk renderer, GPU, Oculus/Embeddium or save-load timing was validated.\n'
printf 'Core geometry parity must be compared with the same Geometry40Parity executable compiled against the baseline source.\n'
