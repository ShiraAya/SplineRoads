#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/any405"}
bash "$ROOT/tools/test_warm404.sh" "$OUT"
javac --release 17 -encoding UTF-8 -cp "$OUT/core/classes" -d "$OUT/core/classes" "$ROOT/src/validation/java/com/sora/splineroads/AnyLane405Validation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$ROOT/src/main/resources" com.sora.splineroads.AnyLane405Validation
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$OUT/model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.AnyLane405ModelValidation
