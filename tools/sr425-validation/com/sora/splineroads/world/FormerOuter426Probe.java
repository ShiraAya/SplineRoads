package com.sora.splineroads.world;
import com.sora.splineroads.core.*;import com.sora.splineroads.core.RoadGeometry.*;import java.util.*;
/** Additional local planner probe, not a replacement for the committed regression suite. */
public final class FormerOuter426Probe {
 public static void main(String[] args){int cases=0;for(boolean left:new boolean[]{false,true})for(var style:List.of(Style.O3_ONE,Style.O6_RAIL)){
  var all=new LinkedHashMap<UUID,RoadRecord>();var host=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,1400),style,left);all.put(host.id(),host);int slot=2,sign=LanePoints.lane(host.rawMesh(),700,slot).sign();double drop=sign>0?200:1200,departure=sign>0?500:900,targetAt=sign>0?1000:400;
  var ref=Arrival417ModelValidation.point(all,host,drop,slot);host=all.get(host.id());var point=LaneTopology.point(host,ref.point());var md=LaneTopology.metadata(host);var points=new ArrayList<>(md.points());points.set(points.indexOf(point),point.merge(32));all.put(host.id(),host.withLanePoints(md.points(points)));LaneCrossSections.reconcile(all);host=all.get(host.id());
  var from=Arrival417ModelValidation.point(all,host,departure,1);var to=Arrival417ModelValidation.point(all,host,departure+sign*96,0);

  var id=UUID.randomUUID();var opts=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.DETACH,LanePoints.Arrival.FLOW,24,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
  var ramp=LaneRamps.generate(null,all,id,UUID.randomUUID(),new LanePoints.Link(from,to,opts,null));all.put(id,ramp);LaneCrossSections.reconcile(all);var after=all.get(host.id());
  if(LaneSections.active(after.mesh(),departure+sign*100,1)||!LaneSections.active(after.mesh(),departure+sign*100,0)||LaneSections.active(after.mesh(),departure+sign*100,2))throw new AssertionError("wrong retained lanes");
  all.remove(id);LaneCrossSections.reconcile(all);var restored=all.get(host.id());if(!LaneSections.active(restored.mesh(),departure+sign*100,1)||LaneSections.active(restored.mesh(),departure+sign*100,2))throw new AssertionError("deletion did not preserve earlier merge");
  System.out.println("PASS "+style+" left="+left+" former middle DETACH length="+ramp.mesh().length()+" and isolated deletion");cases++;
 }System.out.println("PASS "+cases+" real planner/model cases; explicit world/NBT adapters, NOT Minecraft world execution");}
}
