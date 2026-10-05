#!/usr/bin/env bash
# Full pre-existing regression set plus this release; adapters remain outside production JAR.
set -euo pipefail
bash tools/check_release0405.sh
CP=build/tunnel406/core/classes:src/main/resources
for name in StructureRaster412Validation PlainStructure414Validation RoadBounds413Validation; do
  javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes "src/validation/java/com/sora/splineroads/$name.java"
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" "com.sora.splineroads.$name" | tee "build/checkpoint-logs/$name.txt"
done
CP=build/tunnel406/index/classes:build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/index/classes src/main/java/com/sora/splineroads/world/RoadPlanningIndex.java tools/perf413-validation/com/sora/splineroads/world/Planning413Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Planning413Validation | tee build/checkpoint-logs/Planning413Validation.txt

python tools/test_profile_summary.py 2>&1 | tee build/checkpoint-logs/profile-summary-tests.txt
