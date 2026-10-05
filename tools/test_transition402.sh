#!/usr/bin/env bash
# Only real core classes are tested; no full Forge build or GPU/client claim.
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/transition402"}
bash "$ROOT/tools/test_ramp39.sh" "$OUT/core"
mkdir -p "$OUT/logs"
javac --release 17 -encoding UTF-8 -cp "$OUT/core/classes" -d "$OUT/core/classes" \
  "$ROOT/src/validation/java/com/sora/splineroads/Transition402Validation.java"
java -Xmx1500m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8 \
  -cp "$OUT/core/classes:$ROOT/src/main/resources" com.sora.splineroads.Transition402Validation \
  | tee "$OUT/logs/Transition402Validation.log"
