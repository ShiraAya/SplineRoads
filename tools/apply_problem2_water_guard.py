# Final P1 water review: node cleanup and legacy repair must use the same dry exclusion.
from pathlib import Path
import hashlib
p=Path('src/main/java/com/sora/splineroads/world/RoadData.java');s=p.read_text();assert hashlib.sha256(p.read_bytes()).hexdigest()=='fd88f1828e9546d00a2b085624d5a5eea7cc71a6303f7d0c1e77641970e45a6c'
s=s.replace('return collisionState(key,previous,roads,sidewalk,roads.stream().anyMatch(r->r.record.settings().structure()==Structure.TUNNEL&&r.clearanceAt(pos)));','''boolean dryInterior=index.inChunk(new net.minecraft.world.level.ChunkPos(pos).toLong()).stream()
        .map(index.roads::get).anyMatch(r->r!=null&&r.record.settings().structure()==Structure.TUNNEL&&r.clearanceAt(pos));
    return collisionState(key,previous,roads,sidewalk,dryInterior);''')
s=s.replace('writes.put(key,body.containsKey(key)?collisionState(key,Blocks.AIR.defaultBlockState(),body.get(key)):Blocks.AIR.defaultBlockState());','writes.put(key,body.containsKey(key)?collisionState(key,Blocks.AIR.defaultBlockState(),body.get(key),sidewalks.get(key),dry.contains(key)):dry.contains(key)?SplineRoads.TUNNEL_AIR.get().defaultBlockState():Blocks.AIR.defaultBlockState());')
s=s.replace('''? collisionState(source, Blocks.AIR.defaultBlockState(), body.get(source))
                  : Blocks.AIR.defaultBlockState());''','''? collisionState(source, Blocks.AIR.defaultBlockState(), body.get(source),sidewalks.get(source),dry.contains(source))
                  : dry.contains(source)?SplineRoads.TUNNEL_AIR.get().defaultBlockState():Blocks.AIR.defaultBlockState());''')
p.write_text(s);assert hashlib.sha256(p.read_bytes()).hexdigest()=='7ffa300c2d43c0c33cc2e34c241c92cd70a0bed244862a3adc8ea843ae125992'
p=Path('tools/check_problem2_batch1.sh');s=p.read_text();s=s.replace("assert 'import com.sora.splineroads.core.RoadConnectionChecks;' in s", "assert 'collisionState(source, Blocks.AIR.defaultBlockState(), body.get(source),sidewalks.get(source),dry.contains(source))' in s\nassert 'boolean dryInterior=index.inChunk(new net.minecraft.world.level.ChunkPos(pos).toLong()).stream()' in s\nassert 'import com.sora.splineroads.core.RoadConnectionChecks;' in s");p.write_text(s)
print('PASS: all collision-state entry paths preserve relevant dry tunnel volumes; source bytes match review')
