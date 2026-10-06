#!/usr/bin/env bash
set -euo pipefail
export LANG=C.UTF-8 LC_ALL=C.UTF-8
bash tools/check_sr429.sh
CP=build/tunnel406/core/classes:build/tunnel406/model/classes:src/main/resources
java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp "$CP" com.sora.splineroads.world.Hotfix430ModelValidation | tee build/checkpoint-logs/Hotfix430ModelValidation.txt
python - <<'PY'
from pathlib import Path
r=Path('src/main/java/com/sora/splineroads')
n=(r/'net/RoadNetwork.java').read_text();jobs=(r/'net/RoadPlanningJobs.java').read_text()
assert 'RoadPlanningJobs.begin(player,tool,t)' in ''.join(n.split())
assert 'CallerRunsPolicy' not in jobs and '.join(' not in jobs and '.get()' not in jobs.replace('job.cancelled.get()','')
assert 'new ArrayBlockingQueue<>(8)' in jobs and 'AbortPolicy' in jobs and 'server.execute' in jobs
assert 'data.index.revision()!=work.revision' in (r/'world/LaneRamps.java').read_text()
assert 'value=(snapped-1)/3.0' in (r/'client/RoadLaneConfigScreen.java').read_text()
for name in ['core/RoadJunction.java','core/LaneMerge.java','core/LaneRampPaint.java']:
 assert 'RoadMergeArrow.' in (r/name).read_text()
print('SR430 production binding PASS: worker routing, bounded queue, no waiting future, stale revision, snap and shared arrows. Static checks only, NOT client GUI or network testing.')
PY
