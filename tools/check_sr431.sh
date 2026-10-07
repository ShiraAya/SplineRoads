#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
mkdir -p build/checkpoint-logs
bash tools/check_sr430.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/core/*431*.java
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.core.Performance431Validation
python tools/test_scheduler430.py build/tunnel406/core/classes
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Performance431Probe 6
