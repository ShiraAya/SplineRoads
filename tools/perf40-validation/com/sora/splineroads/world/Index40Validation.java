package com.sora.splineroads.world;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Uses the PRODUCTION RoadIndex, RoadRaster and RoadRecord, not the older model-test RoadIndex. */
public final class Index40Validation {
  private static int checks;
  private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  private static void near(double a,double b,String why){check(a==b||Math.abs(a-b)<1e-6,why+": "+a+"/"+b);}
  private static RoadRecord record(boolean slope,Structure structure){
    V a=new V(-7.2,100,-16),b=new V(19.7,slope?104.1:100,48);V d=b.sub(a);
    var s=new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90).structure(structure);
    return new RoadRecord(UUID.randomUUID(),UUID.randomUUID(),new BlockPos(-7,99,-16),new BlockPos(19,104,48),
        new Node(a,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength()),new Node(b,RoadPlanner.yaw(d.horizontalUnit()),d.y()/d.horizontalLength()),s,true,5);
  }
  public static void main(String[] args){
    for(boolean slope:new boolean[]{false,true})for(Structure structure:new Structure[]{Structure.GROUND,Structure.TUNNEL})run(record(slope,structure));
    var withFurniture=record(true,Structure.GROUND).structures(List.of(
        new RoadStructures.Part(new V(-11,100,-10),new V(13,104,40),2,.5,false,RoadStructures.Material.WALK_STONE_BRICKS),
        new RoadStructures.Part(new V(3,103,8),new V(3,103,9),.5,1,false,RoadStructures.Material.LAMP),
        new RoadStructures.Part(new V(-13,99,-4),new V(-13,99,20),.6,5,false,RoadStructures.Material.TUNNEL)));
    run(withFurniture);
    testReuse();System.out.println("Index40Validation: "+checks+" checks passed (real RoadIndex/DeferredMap/RoadRaster; test BlockPos/AABB only; no world-write or VoxelShape claim)");
  }
  private static void run(RoadRecord record){
    var local=new RoadIndex.Built(record);check(!local.rasterized(),"new planned Built defers full raster");
    var full=new RoadIndex.Built(record);check(full.cells.size()>0&&full.rasterized(),"enumeration materializes full authoritative raster");
    List<BlockPos> points=new ArrayList<>();
    for(int x=-14;x<=28;x+=3)for(int z=-19;z<=51;z+=3)for(int y=97;y<=111;y++)points.add(new BlockPos(x,y,z));
    Collections.shuffle(points,new Random(40));
    for(var p:points){
      var a=local.boxes(p);var b=full.cells.getOrDefault(p.asLong(),List.of());check(a.isEmpty()==b.isEmpty(),"local body membership "+p);
      check(local.clearanceAt(p)==full.clearanceCells.contains(p.asLong()),"local clearance matches full, including widened tunnel "+p);
      var ca=local.column(p);var cb=full.columns.get(BlockPos.asLong(p.getX(),0,p.getZ()));check((ca==null)==(cb==null),"column exists");
      if(ca!=null){near(ca.minTop(),cb.minTop(),"column low");near(ca.maxTop(),cb.maxTop(),"column high");}
      if(!a.isEmpty()){
        double va=a.stream().mapToDouble(q->(q.x1()-q.x0())*(q.y1()-q.y0())*(q.z1()-q.z0())).sum();
        double vb=b.stream().mapToDouble(q->(q.x1()-q.x0())*(q.y1()-q.y0())*(q.z1()-q.z0())).sum();near(va,vb,"collision volume");
      }
      check(local.lightAt(p)==full.lightCells.contains(p.asLong()),"local emissive structure");
      check(local.shellAt(p)==full.shellCells.contains(p.asLong()),"local tunnel structure");
      near(local.walkTopAt(p),full.walkTops.getOrDefault(p.asLong(),Double.NEGATIVE_INFINITY),"sidewalk top");
    }
    check(!local.rasterized(),"thousands of local queries must not allocate a full road raster");
    full.releaseEditRaster();check(!full.rasterized(),"release edit raster");
    for(int i=0;i<100;i++){var p=points.get(i);check(full.clearanceAt(p)==local.clearanceAt(p),"clearance after release");}
  }
  private static void testReuse(){
    var r=record(false,Structure.GROUND);check(r.mesh()==r.mesh(),"immutable record mesh identity cache");
    check(r.rawMesh()==r.rawMesh(),"raw cache");check(r.settings(r.settings())==r,"unchanged settings keep record");
    check(r.withLanePoints(r.settings().options().lanePoints())==r,"unchanged point metadata keeps record");
    var b=new RoadIndex.Built(r);check(b.structures(r.structures())==b,"unchanged structures reuse Built");
    var modified=b.phase(new RoadFurniture.Phase(3,-1));check(!modified.rasterized(),"intermediate phase planning stays deferred");
    var index=new RoadIndex();index.put(b);check(index.inChunk(new net.minecraft.world.level.ChunkPos(r.a()).toLong()).contains(r.id()),"spatial registration");index.clear();check(index.roads.isEmpty()&&index.inChunk(new net.minecraft.world.level.ChunkPos(r.a()).toLong()).isEmpty(),"clear removes indices");
  }
}
