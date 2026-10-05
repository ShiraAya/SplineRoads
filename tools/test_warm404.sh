#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/warm404"}
bash "$ROOT/tools/test_temporary403.sh" "$OUT"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$OUT/hotfix/classes:$OUT/render/classes:$OUT/index/classes:$OUT/core/classes:$OUT/model/classes:$ROOT/src/main/resources" com.sora.splineroads.client.Warm404Validation
python3 "$ROOT/tools/test_vbo_cache404.py" "$OUT/vbo-methods" "$OUT/render/classes:$OUT/index/classes:$OUT/core/classes:$OUT/model/classes"
