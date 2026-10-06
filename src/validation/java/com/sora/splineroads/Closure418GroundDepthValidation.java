package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Actual planting volume must reach the classified support depth, not just a thin skin. */
public final class Closure418GroundDepthValidation {
 public static void main(String[]args){int checks=0;
  for(double thickness:new double[]{.25,1,2})for(boolean curve:new boolean[]{false,true}){
   var original=Closure418Validation.road(Style.O3_ONE,Structure.GROUND,false,curve,350);var s=original.settings();
   var setting=new Settings(s.mode(),s.style(),s.width(),thickness,s.tension(),s.arcDegrees()).options(s.options()).structure(Structure.GROUND);
   var raw=RoadRibbon.mesh(original.samples(),setting);double depth=Math.max(.5,thickness+.125);
   var cut=new LaneSections.Cut(new UUID(421,1),1,1,80,250,32,null,true,false,true);
   var mesh=Closure418Validation.apply(raw,List.of(cut));var parts=LaneClosureLandscape.plan(mesh,Closure418Validation.terrain(depth-.01));
   if(parts.isEmpty())throw new AssertionError("supported lane has no planter");checks++;
   for(var p:parts)if(p.material()==Material.SOIL){
    if(p.height()<depth-1e-8)throw new AssertionError("soil floats over removed slab");checks++;
    for(V v:List.of(p.a(),p.b())){var q=RoadQueries.horizontal(raw,v);var top=LanePoints.lane(raw,q.sample().distance(),1).position().y();
     if(Math.abs(v.y()+p.height()-top)>1e-5)throw new AssertionError("planting top moved off roadway");checks++;}
   }
  }
  System.out.println("Closure418GroundDepthValidation: 6 real volume cases, "+checks+" checks; NO world writes.");
 }
}
