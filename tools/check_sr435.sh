#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
CP="$ROOT/build/ramp39-core/classes"
javac --release 17 -encoding UTF-8 -cp "$CP" -d "$CP" "$ROOT/src/validation/java/com/sora/splineroads/Live435Validation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP:$ROOT/src/main/resources" com.sora.splineroads.Live435Validation | tee "$ROOT/build/ramp39-core/logs/Live435Validation.log"
for test in Closure418Validation Rail419MarkingValidation; do
  javac --release 17 -encoding UTF-8 -cp "$CP" -d "$CP" "$ROOT/src/validation/java/com/sora/splineroads/$test.java"
  java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP:$ROOT/src/main/resources" "com.sora.splineroads.$test" | tee "$ROOT/build/ramp39-core/logs/$test.log"
done
javac --release 17 -encoding UTF-8 -cp "$CP:$ROOT/build/ramp39-model/classes" -d "$ROOT/build/ramp39-model/classes" "$ROOT/tools/model-validation/com/sora/splineroads/world/Live435ModelValidation.java"
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP:$ROOT/build/ramp39-model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Live435ModelValidation | tee "$ROOT/build/ramp39-model/logs/Live435ModelValidation.log"
