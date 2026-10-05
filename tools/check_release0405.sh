#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/checkpoint-logs
bash tools/test_transition402.sh 2>&1 | tee build/checkpoint-logs/transitions.txt
bash tools/test_tunnel406.sh 2>&1 | tee build/checkpoint-logs/regression.txt
CP=build/tunnel406/core/classes:src/main/resources
for name in CurvedAny405Validation VisibilityMedian407Validation TunnelMedian407Probe Furniture408Validation Lamp408Validation TactileTunnel409Validation PierSpacing410Validation; do
  javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes "src/validation/java/com/sora/splineroads/$name.java"
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$name" | tee "build/checkpoint-logs/$name.txt"
done
bash gradlew --no-daemon --stacktrace --max-workers=2 compileJava compileGameTestJava jar 2>&1 | tee build/checkpoint-logs/forge.txt
