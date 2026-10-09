#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
java -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$ROOT/build/ramp39-core/classes:$ROOT/build/ramp39-model/classes:$ROOT/src/main/resources" com.sora.splineroads.world.Live445ModelValidation | tee "$ROOT/build/ramp39-model/logs/Live445ModelValidation.log"
