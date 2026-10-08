#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
CP="$ROOT/build/ramp39-core/classes"
javac --release 17 -encoding UTF-8 -cp "$CP" -d "$CP" "$ROOT/src/validation/java/com/sora/splineroads/Live435Validation.java" "$ROOT/src/validation/java/com/sora/splineroads/Live439Validation.java" "$ROOT/src/validation/java/com/sora/splineroads/Live440Validation.java"
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP:$ROOT/src/main/resources" com.sora.splineroads.Live440Validation
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP:$ROOT/build/ramp39-model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Live440ModelValidation
