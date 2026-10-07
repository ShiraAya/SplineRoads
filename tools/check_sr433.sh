#!/usr/bin/env bash
set -euo pipefail
bash tools/check_sr432.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Regression433ModelValidation
