#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/bidirectional402"}
bash "$ROOT/tools/test_hotfix401.sh" "$OUT"
mkdir -p "$OUT/logs"
javac --release 17 -encoding UTF-8 -cp "$OUT/core/classes" -d "$OUT/core/classes" "$ROOT/src/validation/java/com/sora/splineroads/Bidirectional402Validation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$ROOT/src/main/resources" com.sora.splineroads.Bidirectional402Validation | tee "$OUT/logs/Bidirectional402Validation.log"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$OUT/model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Bidirectional402ModelValidation | tee "$OUT/logs/Bidirectional402ModelValidation.log"
