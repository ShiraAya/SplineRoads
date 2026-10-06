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
        var bed=RoadStructures.planting(la.position(),lb.position(),a.left(),b.left(),la.width()-.12,lb.width()-.12,1,
            Math.max(.5,raw.settings().thickness()+.125));
        // Each actual solid is checked separately. Foliage intersecting a ramp at
        // the mouth must not remove the nonintersecting subgrade foundation/curbs.
        // Once the rising deck clears the bed, the complete normal-road bed resumes.
        for(var part:bed)if(!ground.blocked(part))result.add(part);
      }
    }
    return List.copyOf(result);
  }
  private LaneClosureLandscape(){}
}
