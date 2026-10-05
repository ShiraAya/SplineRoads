package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
/** Real scope/delta validator and RoadInteractions. Index/BlockPos are explicit adapters. */
public final class Problem2ShellValidation {
  static int checks;static long id=1;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static RoadRecord road(V a,V b,Structure type){var s=new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90).structure(type);var n=new Node(a,RoadPlanner.yaw(b.sub(a).horizontalUnit()),(b.y()-a.y())/b.sub(a).horizontalLength());return new RoadRecord(new UUID(2,id++),new UUID(2,0),new BlockPos((int)Math.floor(a.x()),(int)Math.floor(a.y()),(int)Math.floor(a.z())),new BlockPos((int)Math.floor(b.x()),(int)Math.floor(b.y()),(int)Math.floor(b.z())),n,new Node(b,n.yaw(),n.grade()),s);}
  static void rejected(Runnable action){boolean fail=false;try{action.run();}catch(IllegalArgumentException e){fail=e.getMessage().contains("隧道墙顶侵入")&&e.getMessage().contains("结构位置");}check(fail,"new conflicts must not be excused while deleting");}
  public static void main(String[]args){
    for(double y:List.of(2.0,100.0,341.0)){
      var p=new RoadStructures.Part(new V(-4,y+1,0),new V(4,y+1,0),1,2,false,RoadStructures.Material.TUNNEL);
      var tube=road(new V(0,y,-30),new V(0,y,30),Structure.TUNNEL).structures(List.of(p));
      var crossing=road(new V(-30,y,0),new V(30,y,0),Structure.GROUND);
      var t=new RoadIndex.Built(tube);var c=new RoadIndex.Built(crossing);var old=new LinkedHashMap<UUID,RoadIndex.Built>();old.put(tube.id(),t);old.put(crossing.id(),c);
      check(RoadInteractions.invades(p,c.mesh),"old invalid saved fixture really conflicts");
      TunnelShellValidation.check(List.of(t,c),Set.of(tube.id()),old);checks++;
      rejected(()->TunnelShellValidation.check(List.of(t,c),Set.of(tube.id()),Map.of()));
      var added=tube.structures(List.of(p,new RoadStructures.Part(new V(-3,y+1,1),new V(3,y+1,1),1,2,false,RoadStructures.Material.TUNNEL)));
      rejected(()->TunnelShellValidation.check(List.of(new RoadIndex.Built(added),c),Set.of(tube.id()),old));
      var changed=crossing.settings(new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1.1,.35,90));
      rejected(()->TunnelShellValidation.check(List.of(t,new RoadIndex.Built(changed)),Set.of(crossing.id()),old));
      TunnelShellValidation.check(List.of(c),Set.of(crossing.id()),old);checks++;
      check(old.get(tube.id()).record.equals(tube),"validation doesn't mutate previous state");
    }
    System.out.println("Problem2ShellValidation: "+checks+" checks PASS; unchanged saved conflict no longer vetoes local deletion, new/changed obstruction remains rejected; no world writes.");
  }
}
