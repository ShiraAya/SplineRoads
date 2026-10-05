# One-use guarded edit; previous source remains recoverable. Not a generic migration tool.
from pathlib import Path
import hashlib
W='src/main/java/com/sora/splineroads/world/'
expected={W+'StructurePlanner.java':'183be79caa3a76227c3eb3f6f7438b6f4bbf77e248935281936dd0cb9f898f76',W+'RoadData.java':'e084725169910f585697178957d36c1301c70f3ec6a0b3981c56a9edb1d6b479','build.gradle':'55611caa6a4bd6c78651ca02e08b0cb7355601315a19e549d801cea79853087c','src/main/resources/META-INF/mods.toml':'065b3b3a70b84720b6f8789bf57d9969cfacf91974b5268549f8986010cc5b60'}
for f,sha in expected.items():assert hashlib.sha256(Path(f).read_bytes()).hexdigest()==sha,f+' changed; abort'
p=Path(W+'StructurePlanner.java');s=p.read_text()
old='    var ground = new RoadStructures.Ground() {'
assert s.count(old)==1
s=s.replace(old,'    // Piers may stand outside their owning road\'s deck bounds and record endpoints\n    // are not reliable geometric identity. Check their actual local shaft positions.\n    var pierSpacing=new RoadPierSpacing(obstacles.stream().flatMap(r->r.record.structures().stream()).toList());\n    var ground = new RoadStructures.Ground() {')
s=s.replace('              public boolean blocked(RoadStructures.Part part) {\n','              public boolean blocked(RoadStructures.Part part) {\n                if(pierSpacing.tooClose(part))return true;\n')
a=s.index('                  // Existing piers also impose a minimum spacing');b=s.index('                  if (box.maxY < other.mesh.min().y()',a);s=s[:a]+s[b:];p.write_text(s)
p=Path(W+'RoadData.java');s=p.read_text()
changes=[('      List<RoadIndex.Built> requested = new ArrayList<>(built);','      timing.stage("normalize_transitions");\n      List<RoadIndex.Built> requested = new ArrayList<>(built);'),('      AttachedPoints.reconcile(this,built,removed,deleting,deletedPoints);','      timing.stage("affected_structures");\n      AttachedPoints.reconcile(this,built,removed,deleting,deletedPoints);\n      timing.stage("attached_points");'),('      workChunks.roads(terrainRoads);\n      Map<BlockPos, List<net.minecraft.world.phys.AABB>> terrainCache','      timing.stage("caps_and_dependencies");\n      workChunks.roads(terrainRoads);\n      timing.stage("terrain_chunk_access");\n      Map<BlockPos, List<net.minecraft.world.phys.AABB>> terrainCache'),('      for (int i = 0; i < built.size(); i++) {\n        var r = built.get(i);\n        RoadRecord planned = StructurePlanner.plan','      timing.stage("furniture_phase");\n      for (int i = 0; i < built.size(); i++) {\n        var r = built.get(i);\n        RoadRecord planned = StructurePlanner.plan'),('      // Terrain-derived raised medians can change a lane center after planning.','      timing.stage("structure_plan");\n      // Terrain-derived raised medians can change a lane center after planning.'),('      var noseCaps =\n','      timing.stage("dependent_replanning");\n      var noseCaps =\n'),('      timing.stage("structures");','      timing.stage("caps_shell_and_joint_validation");')]
for old,new in changes:
 assert s.count(old)==1,old
 s=s.replace(old,new)
p.write_text(s)
assert hashlib.sha256(Path(W+'StructurePlanner.java').read_bytes()).hexdigest()=='f933e495adbcbd0b39b55af2cb91e19f00b93bcf47729a4eda3eb736271fb796'
assert hashlib.sha256(Path(W+'RoadData.java').read_bytes()).hexdigest()=='27960ef6ea22cce2d18f83afbfe75fb5ee4336c2e5b302e53a52f0cc32ae5773'
for name in ['build.gradle','src/main/resources/META-INF/mods.toml']:
 p=Path(name);s=p.read_text();assert '0.40.4-alpha' in s;p.write_text(s.replace('0.40.4-alpha','0.40.5-alpha'))
p=Path('.github/workflows/sr-checkpoint.yml');s=p.read_text();anchor='      - name: Real Forge compile and reobfuscated JAR\n';assert s.count(anchor)==1
extra='''      - name: Corrected tunnel policy tactile rows and actual shaft spacing
        run: |
          set -euo pipefail
          javac --release 17 -encoding UTF-8 -cp build/tunnel406/core/classes -d build/tunnel406/core/classes src/validation/java/com/sora/splineroads/TactileTunnel409Validation.java src/validation/java/com/sora/splineroads/PierSpacing410Validation.java
          java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp build/tunnel406/core/classes:src/main/resources com.sora.splineroads.TactileTunnel409Validation | tee build/checkpoint-logs/tactile-409.txt
          java -Dfile.encoding=UTF-8 -Xmx1500m -XX:ActiveProcessorCount=2 -cp build/tunnel406/core/classes:src/main/resources com.sora.splineroads.PierSpacing410Validation | tee build/checkpoint-logs/piers-410.txt
'''
p.write_text(s.replace(anchor,extra+anchor))
print('PASS: exact pre-reviewed shaft integration and transaction clocks saved; no world threading or safety bypass')
