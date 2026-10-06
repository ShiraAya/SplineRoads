package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Q2-16/17: replace a closed ground lane with actual planting solids, never a
 * drivable painted island. Elevated slots remain the rectangular LaneDeck void.
 * Classification uses ORIGINAL terrain at the selected lane, not absolute world Y
 * or a majority vote over the unrelated half of a wide road. */
public final class LaneClosureLandscape {
  public static boolean raised(Mesh mesh,int slot,double station,Ground ground){
    if(mesh.settings().structure()==Structure.BRIDGE)return true;
    if(mesh.settings().structure()==Structure.GROUND)return false;
    if(mesh.settings().structure()==Structure.TUNNEL)return true; // no tunnel vegetation
    var raw=LaneSections.reference(mesh);var center=LanePoints.lane(raw,station,slot);
    double threshold=Math.max(.5,raw.settings().thickness()+.125);int unsupported=0;
    for(double along:new double[]{-.65,0,.65}){
      var at=RoadStructures.sample(raw,Math.max(raw.first().distance(),Math.min(raw.last().distance(),station+along)));
      var lane=LanePoints.lane(raw,at.distance(),slot);
      for(double across:new double[]{-.35,0,.35}){
        V p=lane.position().add(at.left().mul(across*center.width()));
        double top=ground.top(p.x(),p.z(),p.y());
        if(!Double.isFinite(top)||p.y()-top>threshold)unsupported++;
      }
    }
    // A one-block construction spine or a missing center block cannot classify the
    // whole lane by itself. Unknown/water columns fail closed to a non-floating gap.
    return unsupported>=5;
  }
  public static List<Part> plan(Mesh mesh,Ground ground){
    if(mesh.settings().structure()==Structure.TUNNEL)return List.of();
    var raw=LaneSections.reference(mesh);var result=new ArrayList<Part>();
    for(var cut:mesh.settings().options().lanePoints().cuts()){
      if(!cut.temporary()||!cut.rectangular())continue;
      double start=Math.max(raw.first().distance(),Math.min(cut.begin(),cut.end()));
      double end=Math.min(raw.last().distance(),Math.max(cut.begin(),cut.end()));
      for(double d=start;d<end-1e-7;d+=1){
        double next=Math.min(end,d+1),mid=(d+next)/2;
        if(raised(mesh,cut.lane(),mid,ground))continue;
        var a=RoadStructures.sample(raw,d);var b=RoadStructures.sample(raw,next);
        var la=LanePoints.lane(raw,d,cut.lane());var lb=LanePoints.lane(raw,next,cut.lane());
        // Only the chosen lane. Inset leaves slightly; neighboring lane axes and
        // available widths are never shifted to make room for this landscaping.
        double halfA=Math.max(.01,la.width()/2-.02),halfB=Math.max(.01,lb.width()/2-.02);
        // Fill to at least the removed slab underside plus the classification tolerance,
        // rather than leave a thin planted skin floating over the former one-block slab.
        double depth=Math.max(.5,raw.settings().thickness()+.125);
        var soil=new Part(la.position().add(new V(0,-depth,0)),lb.position().add(new V(0,-depth,0)),
            2*Math.max(halfA,halfB),depth,false,Material.SOIL).frames(a.left().mul(halfA),b.left().mul(halfB));
        var green=new Part(la.position(),lb.position(),2*Math.max(.01,Math.max(halfA,halfB)-.08),.20,false,Material.OAK_LEAVES)
            .frames(a.left().mul(Math.max(.01,halfA-.08)),b.left().mul(Math.max(.01,halfB-.08)));
        // Ramp and other-road solids win. Do not fill a below-grade ramp portal or
        // create vegetation intersecting the directly connected lane at A/B.
        if(ground.blocked(soil)||ground.blocked(green))continue;
        result.add(soil);result.add(green);
      }
    }
    return List.copyOf(result);
  }
  private LaneClosureLandscape(){}
}
