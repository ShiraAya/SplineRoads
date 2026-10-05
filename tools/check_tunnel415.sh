#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_problem2_batch1.sh
CP=build/tunnel406/core/classes:src/main/resources
for TEST in TunnelBoundary415Validation CurvedTunnel415Validation TunnelSeam416Validation; do
  javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes "src/validation/java/com/sora/splineroads/$TEST.java"
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$TEST" | tee "build/checkpoint-logs/$TEST.txt"
done
CP=build/tunnel406/index/classes:build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/index/classes src/main/java/com/sora/splineroads/world/TunnelTerrainSpace.java tools/tunnel415-validation/com/sora/splineroads/world/TunnelTerrain415Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.TunnelTerrain415Validation | tee build/checkpoint-logs/TunnelTerrain415Validation.txt
