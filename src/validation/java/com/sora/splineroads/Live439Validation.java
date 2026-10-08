package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Live439Validation {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static Mesh strip(double from,double to,double y,double rise,double x){
  var settings=new Settings(Mode.CURVE,Style.C1_RAMP,4,1,.35,90).structure(Structure.BRIDGE);
  var samples=new ArrayList<Sample>();for(double d=from;d<=to+1e-7;d+=.25)samples.add(new Sample(new V(x,y+rise*(d-from)/(to-from),d),new V(1,0,0),d-from,2));
  return RoadRibbon.mesh(samples,settings);
 }
 static void mouths(){
  for(int slot=0;slot<3;slot++)for(boolean arrival:new boolean[]{false,true})for(double dy:new double[]{0,-5,5}){
   var raw=Live435Validation.road(Style.O3_ONE,Structure.BRIDGE);var host=Live435Validation.cut(raw,slot,50,150,arrival);
   var lane=LanePoints.lane(raw,arrival?150:50,slot);double x=lane.position().x();
   var ramp=arrival?strip(100,149.5,20+dy,0,x):strip(50.5,100,20+dy,0,x);
   var join=new RoadRailJoin(List.of(new RoadRailJoin.Neighbor(ramp,false)));var mouths=RoadRailJoin.mouths(List.of(ramp));
   Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return RoadClearance.structureInvades(p,ramp,4.25);}
    public List<RoadRailJoin.Span> railSpans(V a,V b,V outside){return join.exposed(a,b,outside);}
    public List<RoadRailJoin.Span> capRailSpans(V a,V b,V outside){var out=new ArrayList<RoadRailJoin.Span>();for(var span:railSpans(a,b,outside))out.addAll(mouths.exposedMouth(span.a(),span.b()));return out;}};
   var parts=RoadStructures.plan(host,ground);
   double z=(arrival?150:50)+(arrival?-.34:.34);
   var barriers=parts.stream().filter(p->p.material()==Material.STEEL&&Math.abs(p.a().z()-z)<.02&&Math.abs(p.b().z()-z)<.02).toList();
   if(dy==0)for(var p:barriers)check(Math.max(p.a().x(),p.b().x())<=x-1.8||Math.min(p.a().x(),p.b().x())>=x+1.8,"crossbar blocks a declared ramp mouth");
   else check(!barriers.isEmpty(),"height-separated ramp erased a real hole cap");
   double opposite=arrival?50.34:149.66;check(parts.stream().anyMatch(p->p.material()==Material.STEEL&&Math.abs(p.a().z()-opposite)<.02&&Math.abs(p.b().z()-opposite)<.02),"unconnected cap lost protection slot="+slot+" arrival="+arrival+" dy="+dy+" rails="+parts.stream().filter(p->p.material()==Material.STEEL&&Math.abs(p.a().z()-p.b().z())<.02).limit(8).toList());
  }
 }
 static void grades(){
  for(double rise:new double[]{-12,12}){
   Mesh base=strip(0,160,rise<0?32:20,rise,0);
   var bounds=List.of(new LaneRampCorridor.Bound(60,70,rise>0?1.5:0,rise>0));
   var solved=LaneRampCorridor.solveMixed(base,0,base.length(),bounds,.2,true);
   for(int i=1;i<solved.samples().size();i++)check(Math.signum(rise)*(solved.samples().get(i).center().y()-solved.samples().get(i-1).center().y())>=-1e-6,"avoidable grade reversal");
   LaneRampGrade.validate(solved,.2);
  }
  var flat=strip(0,160,20,0,0);boolean rejected=false;
  var bound=List.of(new LaneRampCorridor.Bound(70,80,6,true));
  try{LaneRampCorridor.solveMixed(flat,0,flat.length(),bound,.2,true);}catch(IllegalArgumentException e){rejected=true;}
  check(rejected,"strict phase silently accepted a crest");
  var crest=LaneRampCorridor.solveMixed(flat,0,flat.length(),bound,.2);check(crest.max().y()>=26,"necessary overpass removed");
 }
 static void solids(){
  var ramp=strip(0,100,20,0,0);
  for(double y:new double[]{10,19,20,23,30}){
   var pier=new Part(new V(-4,y,50),new V(4,y+1,50),1,2,false,Material.CONCRETE);
   var contacts=RoadClearance.structureContacts(pier,ramp,4.25);
   check(!contacts.isEmpty(),"missing solid contact");
   check(contacts.stream().anyMatch(RoadClearance.Contact::blocked)==RoadClearance.structureInvades(pier,ramp,4.25),"solver and validator disagree on saved furniture");
  }
 }
 static void ids(){var id=new UUID(439,1);String text="与道路 "+id+" 冲突；道路 "+id;check(RoadConflictIds.read(text).size()==1,"duplicate conflict id");check(!RoadConflictIds.display(text).contains(id.toString()),"UUID exposed in user diagnostic");}
 public static void main(String[]args){mouths();grades();solids();ids();System.out.println("Live439Validation: "+checks+" checks PASS; mouths, monotone-first profiles, solid contacts and scene diagnostics");}
}
