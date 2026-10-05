#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/temporary403"}
bash "$ROOT/tools/test_bidirectional402.sh" "$OUT"
javac --release 17 -encoding UTF-8 -cp "$OUT/core/classes" -d "$OUT/core/classes" "$ROOT/src/validation/java/com/sora/splineroads/Temporary403Validation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$ROOT/src/main/resources" com.sora.splineroads.Temporary403Validation
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/core/classes:$OUT/model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Temporary403ModelValidation
