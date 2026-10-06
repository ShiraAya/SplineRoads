#!/usr/bin/env bash
# Run from the repository root after tools/check_sr426.sh has populated adapters.
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
mkdir -p build/extra426/classes
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/extra426/classes tools/sr425-validation/com/sora/splineroads/world/FormerOuter426Probe.java
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "build/extra426/classes:$CP" com.sora.splineroads.world.FormerOuter426Probe
