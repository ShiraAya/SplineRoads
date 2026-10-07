#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_sr431.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/core/*432*.java
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.core.Performance432Validation
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.core.Clearance432Validation
