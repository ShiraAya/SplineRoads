#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_sr426.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
javac --release 17 -encoding UTF-8 -cp "$CP" -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/Directional427Validation.java
java -Dfile.encoding=UTF-8 -Xmx1500m -cp "$CP" com.sora.splineroads.Directional427Validation | tee build/checkpoint-logs/Directional427Validation.txt
java -Dfile.encoding=UTF-8 -Xmx1500m -cp "$CP" com.sora.splineroads.world.Directional427ModelValidation | tee build/checkpoint-logs/Directional427ModelValidation.txt
