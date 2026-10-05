package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
/** Same probe is compiled against pre-fix and post-fix production core. */
public final class TunnelMedian407Probe {
 public static void main(String[] args){
  var g=new Ground(){public double top(double x,double z,double y){return y;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  for(Style st:new Style[]{Style.O4_RAIL,Style.O4_GREEN}){
   var s=new Settings(Mode.STRAIGHT,st,RoadProfile.width(st,RoadProfile.Options.DEFAULT,4),1,.35,90).structure(Structure.TUNNEL);
   var m=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,96),0,0),s);
   var parts=RoadInfrastructure.plan(m,g);
   System.out.println(st+": requested steel="+parts.stream().filter(p->p.material()==Material.STEEL).count()+", green="+parts.stream().filter(p->p.material()==Material.GREEN).count());
  }
 }
}
