package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Production generation, codec, reconfigure and reversible material reservations.
 * NBT/index are named adapters. Parts use the real packed binary codec, but no
 * actual world writes or on-disk NBT persistence are executed. */
public final class Closure418ModelValidation {
 static int checks,cases;
 static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static Ground ground(){return new Ground(){public double top(double x,double z,double y){return y-.3;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};}
 public static void main(String[]args){
  for(boolean left:new boolean[]{false,true})for(var style:List.of(Style.O1_ONE,Style.O3_ONE,Style.O6_RAIL,Style.H6_RAIL)){
   int count=RoadProfile.catalog(style).lanes();for(int slot=0;slot<count;slot++){
    cases++;var all=new LinkedHashMap<UUID,RoadRecord>();var target=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,1400),style,left);
    int sign=LanePoints.lane(target.rawMesh(),700,slot).sign();var source=Arrival417ModelValidation.road(new V(-360,100,700-sign*350),new V(-280,100,700-sign*350),Style.O1_ONE,left);all.put(source.id(),source);all.put(target.id(),target);
    var from=Arrival417ModelValidation.point(all,source,60,0);var to=Arrival417ModelValidation.point(all,target,700,slot);
    var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.MERGE,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
    // Source has only 20 blocks left: TEMPORARY cannot safely reopen there. Ordinary
    // branch is the deliberate fixture, not a silently weakened production constraint.
    options=new LanePoints.Options(options.path(),LanePoints.Departure.BRANCH,options.arrival(),32,32,options.elevation(),options.landing());
    var rid=Arrival417ModelValidation.id();var ramp=LaneRamps.generate(null,all,rid,Arrival417ModelValidation.id(),new LanePoints.Link(from,to,options,null));
    check(LaneTopology.metadata(ramp).link().rectangularClosure(),"new connector missing explicit closure policy");all.put(rid,ramp);LaneCrossSections.reconcile(all);var host=all.get(target.id());
    var cut=LaneTopology.metadata(host).cuts().stream().filter(c->c.connection().equals(rid)).findFirst().orElseThrow();check(cut.arrival()&&cut.rectangular(),"target does not acquire physical rectangular reservation");
    for(double at:new double[]{cut.begin()+sign*.25,cut.end()-sign*.25})if(at>=0&&at<=host.mesh().length())check(cut.removed(at)==1,"generated target closes as taper");
    var encoded=host.header();var restored=RoadRecord.load(encoded);check(LaneTopology.metadata(restored).cuts().equals(LaneTopology.metadata(host).cuts()),"cut policy lost by codec");
    var plants=LaneClosureLandscape.plan(host.mesh(),ground());check(!plants.isEmpty(),"ground target has no classified planting");var saved=host.structures(plants);var loaded=RoadRecord.load(saved.save());
    check(loaded.structures().equals(plants),"saved real green/soil structure list lost by record codec");
    var link=LaneTopology.metadata(ramp).link();check(LanePointCodec.link(LanePointCodec.link(link)).equals(link),"new link policy lost by codec");
    var legacy=new LanePoints.Link(link.from(),link.to(),link.options(),link.junctionMouth(),link.targetOffset(),link.protectedMerge());
    check(!LanePointCodec.link(LanePointCodec.link(legacy)).rectangularClosure(),"old-link load silently upgrades geometry");
    var oldRoad=ramp.withLanePoints(LaneTopology.metadata(ramp).link(legacy));all.put(rid,oldRoad);var edited=LaneRamps.reconfigure(oldRoad,oldRoad.settings(),all);check(LaneTopology.metadata(edited).link().rectangularClosure(),"explicit editing does not upgrade shape");
    all.put(rid,edited);LaneCrossSections.reconcile(all);var before=LaneTopology.metadata(all.get(target.id())).cuts();LaneCrossSections.reconcile(all);check(before.equals(LaneTopology.metadata(all.get(target.id())).cuts()),"repeat derivation not stable");
    all.remove(rid);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(target.id())).cuts().isEmpty(),"deletion leaves reservation");check(LaneClosureLandscape.plan(all.get(target.id()).mesh(),ground()).isEmpty(),"deletion leaves planting recipe");
   }
  }
  for(boolean left:new boolean[]{false,true})for(int slot:new int[]{0,1,2}){
   cases++;var all=new LinkedHashMap<UUID,RoadRecord>();var source=Arrival417ModelValidation.road(new V(0,100,0),new V(0,100,1400),Style.O3_ONE,left);
   int sign=LanePoints.lane(source.rawMesh(),700,slot).sign();double start=sign>0?200:1200;
   var target=Arrival417ModelValidation.road(new V(280,112,start+sign*450),new V(460,112,start+sign*450),Style.O1_ONE,left);
   all.put(source.id(),source);all.put(target.id(),target);var from=Arrival417ModelValidation.point(all,source,start,slot);var to=Arrival417ModelValidation.point(all,target,140,0);
   var options=new LanePoints.Options(LanePoints.Path.AUTO,LanePoints.Departure.TEMPORARY,LanePoints.Arrival.EXTRA,32,32,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT);
   var rid=Arrival417ModelValidation.id();var ramp=LaneRamps.generate(null,all,rid,Arrival417ModelValidation.id(),new LanePoints.Link(from,to,options,null));all.put(rid,ramp);LaneCrossSections.reconcile(all);
   var host=all.get(source.id());var cut=LaneTopology.metadata(host).cuts().stream().filter(c->c.connection().equals(rid)).findFirst().orElseThrow();
   check(cut.rectangular()&&!cut.arrival(),"source temporary departure not rectangular");check(cut.removed(cut.begin()+sign*.01)==1&&cut.removed(cut.end()-sign*.01)==1,"source departure/recovery has pointed taper");
   LaneReopening.validateRestored(host.mesh(),slot,ramp.mesh(),rid);checks++;
   all.remove(rid);LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(source.id())).cuts().isEmpty(),"source deletion leaves square gap");
  }
  System.out.println("Closure418ModelValidation: "+cases+" actual generation/edit/codec/delete cases, "+checks+" checks; explicit NBT/index adapters, NO world writes.");
 }
}
