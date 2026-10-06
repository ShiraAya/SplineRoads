#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_p1review424.sh
mkdir -p build/sr425-index/classes
CP=build/tunnel406/core/classes:build/tunnel406/model/classes
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/sr425-index/classes $(find tools/perf40-index-support -name '*.java') src/main/java/com/sora/splineroads/world/{RoadIndex,DeferredMap}.java tools/sr425-validation/com/sora/splineroads/world/LiveSection425Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -cp "build/sr425-index/classes:$CP:src/main/resources" com.sora.splineroads.world.LiveSection425Validation | tee build/checkpoint-logs/LiveSection425Validation.txt
