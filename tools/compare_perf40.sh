#!/usr/bin/env bash
# Compare against an UNMODIFIED extracted 0.39.0-alpha source directory.
# Only explicit CPU/model adapters run here; this is not Minecraft benchmarking.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ "$#" -lt 1 ]; then echo 'Usage: bash tools/compare_perf40.sh /path/to/extracted/SplineRoads-0.39.0-alpha [output]' >&2; exit 2; fi
BASE=$(cd "$1" && pwd)
OUT=${2:-"$ROOT/build/perf40-comparison"}
if [ "$BASE" = "$ROOT" ] || ! grep -q "version = '0.39.0-alpha'" "$BASE/build.gradle"; then
 echo 'Expected a separate, unmodified 0.39.0-alpha source tree.' >&2; exit 2
fi
mkdir -p "$OUT/logs"
bash "$BASE/tools/test_ramp39_model.sh" "$OUT/baseline-model" "$OUT/baseline-core" | tee "$OUT/logs/baseline-regression.log"
bash "$ROOT/tools/test_perf40.sh" "$OUT/current" | tee "$OUT/logs/current-validation.log"
JVM=(-Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xmx1500m -XX:ActiveProcessorCount=2)
javac --release 17 -encoding UTF-8 -cp "$OUT/baseline-core/classes:$OUT/baseline-model/classes" -d "$OUT/baseline-model/classes" \
 "$ROOT/tools/perf40-validation/com/sora/splineroads/world/Performance40Benchmark.java" \
 "$ROOT/src/validation/java/com/sora/splineroads/Geometry40Parity.java"
java "${JVM[@]}" -cp "$OUT/baseline-core/classes:$OUT/baseline-model/classes:$BASE/src/main/resources" com.sora.splineroads.Geometry40Parity | tee "$OUT/logs/baseline-geometry.log"
diff -u "$OUT/logs/baseline-geometry.log" "$OUT/current/logs/Geometry40Parity.log"
# The same executable, heap, CPU cap, warmup and round count; timings include JVM/host variance.
java "${JVM[@]}" -cp "$OUT/baseline-core/classes:$OUT/baseline-model/classes:$BASE/src/main/resources" com.sora.splineroads.world.Performance40Benchmark | tee "$OUT/logs/benchmark-039.log"
java "${JVM[@]}" -cp "$OUT/current/core/classes:$OUT/current/model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Performance40Benchmark | tee "$OUT/logs/benchmark-040.log"
echo 'Comparison complete. Interpret CPU microbenchmarks ONLY, not full-world load or FPS.'
