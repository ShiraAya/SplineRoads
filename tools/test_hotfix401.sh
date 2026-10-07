#!/usr/bin/env bash
# Offline evidence only. No fake API or world support is compiled into the actual mod.
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-"$ROOT/build/hotfix401"}
FLAGS=(-Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xmx1500m -XX:ActiveProcessorCount=2)
bash "$ROOT/tools/test_perf40.sh" "$OUT"
CORE="$OUT/core/classes"; MODEL="$OUT/model/classes"; INDEX="$OUT/index/classes"; RENDER="$OUT/render/classes"
java "${FLAGS[@]}" -cp "$CORE:$MODEL:$ROOT/src/main/resources" com.sora.splineroads.world.LocalEdits401Validation | tee "$OUT/logs/LocalEdits401Validation.log"
mapfile -d '' TESTS < <(find "$ROOT/tools/hotfix401-validation" "$ROOT/tools/hotfix401-render-support" -name '*.java' -print0)
mkdir -p "$OUT/hotfix/classes"
javac --release 17 -encoding UTF-8 -cp "$RENDER:$INDEX:$CORE:$MODEL" -d "$OUT/hotfix/classes" "${TESTS[@]}" > "$OUT/logs/hotfix-compile.log" 2>&1
for test in core.TerrainChunk401Validation client.ShaderBackend401Validation client.TerrainStreaming401Validation; do
  java "${FLAGS[@]}" -cp "$OUT/hotfix/classes:$RENDER:$INDEX:$CORE:$MODEL:$ROOT/src/main/resources" "com.sora.splineroads.$test" 2>&1 | tee "$OUT/logs/${test##*.}.log"
done
printf '\nPASS: scoped-road edits + per-chunk streaming + shader-state backend tests.\n'
printf 'World transactions, Forge linking, real shader switching/lighting/FPS remain unvalidated here.\n'
