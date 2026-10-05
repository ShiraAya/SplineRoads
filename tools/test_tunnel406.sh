#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/tunnel406"}
bash "$ROOT/tools/test_any405.sh" "$OUT"
CP="$OUT/index/classes:$OUT/core/classes:$OUT/model/classes:$ROOT/src/main/resources"
javac --release 17 -encoding UTF-8 -cp "$CP" -d "$OUT/index/classes" "$ROOT/tools/tunnel406-validation/com/sora/splineroads/world/Tunnel406Validation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Tunnel406Validation
