package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
/** Production planning snapshot + RoadIndex/records; explicit Minecraft type adapters. */
public final class Planning413Validation {
  static int checks;
  static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static RoadIndex.Built road(int id,double x,double z){var s=new Settings(Mode.STRAIGHT,Style.O2_ONE,8,1,.35,90);var a=new V(x,100,z);var b=new V(x+20,100,z+30);return new RoadIndex.Built(new RoadRecord(new UUID(413,id),new UUID(0,0),new BlockPos((int)Math.floor(a.x()),(int)Math.floor(a.y()),(int)Math.floor(a.z())),new BlockPos((int)Math.floor(b.x()),(int)Math.floor(b.y()),(int)Math.floor(b.z())),new Node(a,0,0),new Node(b,0,0),s));}
  public static void main(String[]args){
    var all=new ArrayList<RoadIndex.Built>();for(int i=0;i<300;i++)all.add(road(i,i%20*80-600,i/20*80-500));
    var lookup=new RoadPlanningIndex(all,300);var small=new RoadPlanningIndex(all,1);check(lookup.spatial()&&!small.spatial(),"single-road edits avoid world-sized index setup");var random=new Random(413);
    for(int i=0;i<1500;i++){
      if(i%3==0){int slot=random.nextInt(all.size());var next=road(slot,random.nextDouble()*1600-800,random.nextDouble()*1600-800);all.set(slot,next);lookup.replace(slot,next);small.replace(slot,next);}
      if(i%7==0){int slot=random.nextInt(all.size());var old=all.get(slot);var next=old.structures(List.of(new RoadStructures.Part(new V(0,90,0),new V(0,90,0),1,8,true,RoadStructures.Material.CONCRETE)));all.set(slot,next);lookup.replace(slot,next);small.replace(slot,next);}
      var q=all.get(random.nextInt(all.size()));var two=List.of(q.mesh,all.get(random.nextInt(all.size())).mesh);double margin=i%2==0?3:82;
      var expected=all.stream().filter(b->!b.record.id().equals(q.record.id())&&RoadIndex.overlapXZ(b.mesh,q.mesh,margin)).toList();
      check(small.near(q.mesh,margin,q.record.id()).equals(expected),"small transaction exact latest references/order");
      check(lookup.near(q.mesh,margin,q.record.id()).equals(expected),"same nearby order and exact current Built references");
      var expectedUnion=all.stream().filter(b->!b.record.id().equals(q.record.id())&&two.stream().anyMatch(m->RoadIndex.overlapXZ(b.mesh,m,margin))).toList();
      check(small.nearAny(two,margin,q.record.id()).equals(expectedUnion),"small transaction support union");
      check(lookup.nearAny(two,margin,q.record.id()).equals(expectedUnion),"multi-pad support union matches brute filter and has no duplicates");
    }
    for(var r:all)check(!r.rasterized(),"broad phase must not eagerly rasterize unrelated world roads");
    System.out.println("Planning413Validation: "+checks+" checks passed; 1500 mutable planning steps, same geometry+structure references and stable order; no server placement test.");
  }
}
