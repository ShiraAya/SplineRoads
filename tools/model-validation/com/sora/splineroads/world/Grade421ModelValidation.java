package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Actual server planner/record/codec policy; explicit world/NBT adapters, NOT a world test. */
public final class Grade421ModelValidation {
 static int checks,cases;static long serial=421000;
 static UUID id(){return new UUID(421,++serial);}static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void denied(Runnable r,String m){try{r.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(m);}
 static RoadRecord road(V a,V b,Style s){return Arrival417ModelValidation.road(a,b,s,false);}
 static LanePoints.Ref point(Map<UUID,RoadRecord> all,RoadRecord r,double station){return Arrival417ModelValidation.point(all,r,station,0);}
 static LanePoints.Options options(boolean override){return new LanePoints.Options(LanePoints.Path.DIRECT,LanePoints.Departure.BRANCH,LanePoints.Arrival.MERGE,24,24,LanePoints.Elevation.AUTO,LanePoints.Landing.EXACT,override);}
 static double grade(Mesh m){double max=0;for(int i=1;i<m.samples().size();i++){V d=m.samples().get(i).center().sub(m.samples().get(i-1).center());max=Math.max(max,Math.abs(d.y())/d.horizontalLength());}return max;}
 public static void main(String[]args){
  for(boolean aHigh:new boolean[]{false,true})for(boolean bHigh:new boolean[]{false,true})for(boolean override:new boolean[]{false,true})for(int vertical:new int[]{-1,1})for(double height:new double[]{19,26,32}){
   var all=new LinkedHashMap<UUID,RoadRecord>();var a=road(new V(-100,100,0),new V(0,100,0),aHigh?Style.H1_ONE:Style.O1_ONE);var b=road(new V(210,100+vertical*height,0),new V(700,100+vertical*height,0),bHigh?Style.H1_ONE:Style.O1_ONE);all.put(a.id(),a);all.put(b.id(),b);
   var from=point(all,a,90);var to=point(all,b,30);var o=options(override);var l=new LanePoints.Link(from,to,o,null);double cap=LaneRamps.gradeLimit(all,l);cases++;
   check(cap==LaneRampGrade.limit(aHigh||bHigh,override),"target-only highway policy lost");
   // All alternatives are DIRECT on the same straight axis. Optional tails may not
   // bypass the sampled ceiling or silently move locked B to find more grade room.
   RoadRecord ramp=null;String error="";try{ramp=LaneRamps.generate(null,all,id(),id(),l);}catch(IllegalArgumentException e){error=e.getMessage();}
   boolean shouldPass=height==19||height==26&&cap>=.20||height==32&&cap>=.25;
   check((ramp!=null)==shouldPass,"real planning boundary mismatch aHigh="+aHigh+" bHigh="+bHigh+" override="+override+" h="+height+" cap="+cap+" "+error);
   if(ramp==null){check(error.contains(LaneRampGrade.label(cap)),"failure reports wrong ceiling: "+error);continue;}
   all.put(ramp.id(),ramp);LaneCrossSections.reconcile(all);
   check(grade(ramp.mesh())<=cap+.000002,"fitted final grade bypasses policy");
   if(height==26)check(grade(ramp.mesh())>.15,"fixture does not exercise increased 20 percent budget");
   if(height==32)check(grade(ramp.mesh())>.20,"fixture does not exercise override 25 percent budget");
   check(ramp.mesh().first().center().distance(LanePoints.lane(a.rawMesh(),90,0).position())<1e-6,"A moved");
   check(ramp.mesh().last().center().distance(LanePoints.lane(b.rawMesh(),30,0).position())<1e-6,"locked B moved");
   var decoded=RoadRecord.load(ramp.header());check(LaneTopology.metadata(decoded).link().options().gradeOverride()==override,"codec loses override");
   check(decoded.mesh().samples().equals(ramp.mesh().samples()),"load eagerly replans slope");
   var edited=LaneRamps.reconfigure(decoded,decoded.settings(),all);check(LaneTopology.metadata(edited).link().options().gradeOverride()==override,"road editor reset override");
   check(grade(edited.mesh())<=cap+.000002,"reconfigure bypasses limit");
   if(override&&height==32){var lower=new LanePoints.Link(from,to,options(false),null);UUID rid=ramp.id();denied(()->LaneRamps.generate(null,all,rid,id(),lower),"turning override off left previous steep alignment");}
   all.remove(ramp.id());LaneCrossSections.reconcile(all);check(LaneTopology.metadata(all.get(b.id())).cuts().isEmpty(),"delete leaves slope-related target reservation");
   System.out.printf(Locale.ROOT,"  highway=%s/%s override=%s sign=%d rise=%.0f limit=%.0f%% actual=%.3f%%%n",aHigh,bHigh,override,vertical,height,cap*100,grade(ramp.mesh())*100);
  }
  ancestryAndLegacy();
  System.out.println("Grade421ModelValidation: "+cases+" cases / "+checks+" checks; real LaneRamps, final grade, option codec, reconfigure and delete; explicit NBT/world adapters, NO Minecraft runtime.");
 }
 static void ancestryAndLegacy(){
  var all=new LinkedHashMap<UUID,RoadRecord>();var a=road(new V(0,100,0),new V(100,100,0),Style.H1_ONE);var b=road(new V(500,100,0),new V(800,100,0),Style.O1_ONE);all.put(a.id(),a);all.put(b.id(),b);var ar=point(all,a,80);var br=point(all,b,20);
  var link=new LanePoints.Link(ar,br,options(true),null);var parent=LaneRamps.generate(null,all,id(),id(),link);
  // A separately edited visible connector category cannot erase an actual highway host.
  var tag=RoadRecord.writeSettings(parent.settings());tag.putString("Style","C1_RAMP");parent=parent.settings(RoadRecord.readSettings(tag));all.put(parent.id(),parent);var pr=point(all,parent,parent.mesh().length()*.5);
  var next=new LanePoints.Link(pr,br,options(true),null);check(LaneRamps.gradeLimit(all,next)==.20,"ordinary-looking descendant erased highway ancestry");
  var data=LanePointCodec.options(options(true));check(LanePointCodec.options(data).gradeOverride(),"true option roundtrip");data.remove("GradeOverride");check(!LanePointCodec.options(data).gradeOverride(),"old saves do not default to override");
  check(!LanePointCodec.options(new net.minecraft.nbt.CompoundTag()).gradeOverride(),"empty tool default is not off");
  var malformed=new LanePoints.Link(LanePoints.Ref.lane(id(),id()),br,options(true),null);denied(()->LaneRamps.gradeLimit(all,malformed),"missing host granted ordinary override");
  cases++;
 }
}
