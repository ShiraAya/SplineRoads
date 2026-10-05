package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Pure production deck/paint/collision-queries. No Minecraft or shader world. */
public final class Arrival417GeometryValidation {
 static int checks,cases;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static double material(List<LaneDeck.Strip> strips,boolean first){double sum=0;for(var x:strips)sum+=(first?x.al().distance(x.ar()):x.bl().distance(x.br()));return sum;}
 public static void main(String[]args){
  for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O6_RAIL,Style.H6_RAIL))for(boolean left:new boolean[]{false,true}){
   var options=RoadProfile.Options.DEFAULT.traffic(left);var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).options(options);
   var raw=RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,800),0,0),settings);int count=RoadProfile.catalog(style).lanes();
   for(int slot=0;slot<count;slot++){
    var lane=LanePoints.lane(raw,400,slot);int sign=lane.sign();var cut=new LaneSections.Cut(new UUID(417,++cases),slot,sign,400-sign*100,400,32,null,true,true);
    var md=LanePoints.Data.EMPTY.cuts(List.of(cut));var changed=LaneSections.apply(RoadRibbon.mesh(raw.samples(),settings.options(options.lanePoints(md))));
    var before=RoadStructures.sample(changed,400-.5);var at=RoadStructures.sample(changed,400);var after=RoadStructures.sample(changed,400+.5);
    var approach=sign>0?LaneDeck.strips(changed,before,at):LaneDeck.strips(changed,at,after);var leave=sign>0?LaneDeck.strips(changed,at,after):LaneDeck.strips(changed,before,at);
    check(Math.abs(material(approach,sign<0)-(settings.width()-lane.width()))<1e-6,"closing strip reopens as a triangular taper before B");
    check(Math.abs(material(leave,sign>0)-settings.width())<1e-6,"strip beyond B remains a hole");
    double prior=400-sign*.25,next=400+sign*.25;
    check(!RoadQueries.contains(changed,LanePoints.lane(raw,prior,slot).position(),0,.01),"closed slot retains invisible collidable road");
    check(RoadQueries.contains(changed,LanePoints.lane(raw,next,slot).position(),0,.01),"road not solid immediately after B");
    for(int other=0;other<count;other++)if(other!=slot)check(RoadQueries.contains(changed,LanePoints.lane(raw,prior,other).position(),0,.01),"other lane unexpectedly has a gap");
    var chunks=LaneDeck.rasterPieces(changed,128);for(var chunk:chunks)for(var sample:chunk.samples())check(sample.distance()>=chunk.first().distance()&&sample.distance()<=chunk.last().distance(),"raster rebased authored stations");
    var surface=RoadSurface.build(changed,List.of(),List.of());check(!surface.pavement().isEmpty(),"actual surface disappeared");
    var protectedMesh=LaneDeck.excludingSlot(LaneDeck.motorOnly(changed),slot);check(protectedMesh.reference()==LaneSections.reference(changed),"mask changes original reference identity");
   }
  }
  System.out.println("Arrival417GeometryValidation: "+cases+" one-sided closure/material/query cases, "+checks+" checks; actual pure core, no world writes.");
 }
}
