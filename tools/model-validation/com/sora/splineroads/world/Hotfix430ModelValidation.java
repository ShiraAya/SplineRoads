package com.sora.splineroads.world;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Precise regression for the user report. Model adapters are not a Minecraft world. */
public final class Hotfix430ModelValidation {
 static int checks; static void check(boolean ok,String s){checks++;if(!ok)throw new AssertionError(s);}
 static void near(V a,V b,String s){check(a.distance(b)<1e-7,s+": "+a+" / "+b);}
 static void median(){
  for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true})for(int f=1;f<=4;f++)for(int rev=1;rev<=4;rev++){
   var r=Directional427ModelValidation.road(Directional427ModelValidation.settings(type,f,rev,left));var mesh=r.caps(0).mesh();
   near(RoadMedianAnchor.position(mesh,true),r.start().position(),"start median must be node");near(RoadMedianAnchor.position(mesh,false),r.end().position(),"end median must be node");
   for(var sample:mesh.samples())check(Math.abs(sample.at(RoadProfile.layout(mesh,sample).medianCenter(),0).x()-.5)<1e-7,"median drift along path");
   check(RoadRecord.load(r.save()).mesh().samples().equals(r.mesh().samples()),"median save/reload changed geometry");
  }
  for(boolean left:new boolean[]{false,true})for(int sign:new int[]{-1,1}){
   var h=Directional427ModelValidation.road(Directional427ModelValidation.settings(Type.ORDINARY,3,3,left));var mesh=h.caps(0).mesh();int slot=-1;double far=-1;
   for(int i=0;i<6;i++){var l=LanePoints.lane(mesh,150,i);double d=Math.abs(l.position().x()-.5);if(l.sign()==sign&&d>far){slot=i;far=d;}}
   var point=LanePoints.point(UUID.randomUUID(),LanePoints.Origin.MANUAL,mesh,150,slot).merge(24);h=h.withLanePoints(LanePoints.Data.EMPTY.points(List.of(point)));
   var all=new LinkedHashMap<UUID,RoadRecord>();all.put(h.id(),h);LaneCrossSections.reconcile(all);var changed=all.get(h.id()).caps(0).mesh();
   near(RoadMedianAnchor.position(changed,true),h.start().position(),"merge moved start median");near(RoadMedianAnchor.position(changed,false),h.end().position(),"merge moved end median");
   boolean first=sign<0;var section=RoadEndpointSections.section(changed,first,first);
   var childSettings=RoadTransitions.ends(h.settings().options(h.settings().options().lanePoints(LanePoints.Data.EMPTY)),RoadTransitions.Section.of(section),null);
   var anchor=RoadMedianAnchor.position(changed,first);var dir=new V(0,0,sign);var start=new Node(anchor,sign>0?0:180,0);var end=new Node(anchor.add(dir.mul(180)),start.yaw(),0);
   var child=new RoadRecord(UUID.randomUUID(),h.owner(),first?h.a():h.b(),net.minecraft.core.BlockPos.containing(end.position().x(),end.position().y(),end.position().z()),start,end,childSettings,true,4).caps(0);
   near(RoadMedianAnchor.position(child.mesh(),true),anchor,"3+3 continuation starts on median rather than box centre");
   near(RoadMedianAnchor.position(child.mesh(),false),end.position(),"3+3 continuation end stays on node");
   near(child.mesh().first().center(),first?changed.first().center():changed.last().center(),"taper physical seam mismatch");

  }
 }
 static String key(RoadClearance.Contact c){return c.from()+"/"+c.to()+"/"+c.ours()+"/"+c.other()+"/"+c.usableClearance();}
 static void contacts(){
  var random=new Random(430);var s=new Settings(Mode.STRAIGHT,Style.O2_YELLOW,10,1,.4,90);
  for(int i=0;i<30;i++){
   var a=RoadGeometry.build(new Node(new V(0,30,0),0,0),new Node(new V(0,30,100),0,0),s);
   double z=5+random.nextDouble()*90,y=25+random.nextDouble()*10;
   var b=RoadGeometry.build(new Node(new V(-30,y,z),-90,0),new Node(new V(30,y,z),-90,0),s);
   var original=RoadClearance.contacts(a,b).stream().map(Hotfix430ModelValidation::key).sorted().toList();
   var indexed=RoadClearance.contacts(RoadClearance.prepare(a),b).stream().map(Hotfix430ModelValidation::key).sorted().toList();
   check(original.equals(indexed),"prepared-left contacts changed exact station/clearance");
  }
 }
 static void arrows(){
  for(int side:new int[]{-1,1})for(double width:new double[]{3,4,5,6}){
   var p=RoadMergeArrow.local(width,side);var q=RoadMergeArrow.flat(new V(0,0,0),new V(1,0,0),new V(0,0,1),0,width,side);
   check(p.equals(q),"interchange and connector glyph differ");check(p.size()==3,"shared arrow lost one of its two shafts/head");
  }
 }
 static void budgets(){
  boolean stopped=false;try(var ignored=RoadPlanningBudget.open("expired test",-1)){RoadPlanningBudget.check();}catch(RoadPlanningBudget.Aborted e){stopped=true;}check(stopped,"deadline swallowed");
  var cancelled=new AtomicBoolean();stopped=false;
  try(var outer=RoadPlanningBudget.open("outer",10,cancelled::get);var nested=RoadPlanningBudget.open("nested",10)){cancelled.set(true);RoadPlanningBudget.check();}catch(RoadPlanningBudget.Aborted e){stopped=true;}
  check(stopped,"ancestor cancellation lost");
  RoadPlanningBudget.check();check(!IllegalArgumentException.class.isInstance(new RoadPlanningBudget.Aborted("test")),"route rejection catch can swallow timeout");
  try(var outer=RoadPlanningBudget.open("write boundary",10,cancelled::get)){RoadPlanningBudget.committing();RoadPlanningBudget.check();}
  check(true,"write phase cannot be cooperatively cancelled half way");
 }
 public static void main(String[] args){median();contacts();arrows();budgets();System.out.println("Hotfix430ModelValidation: "+checks+" checks PASS (median anchors, original centres, contact equivalence, exact shared arrows, nested cancellation). No GUI/world claims.");}
}
