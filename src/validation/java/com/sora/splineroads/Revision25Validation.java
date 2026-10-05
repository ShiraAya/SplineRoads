package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.JunctionSpec.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
import java.nio.file.*;
public final class Revision25Validation {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static String point(V p){return Math.round(p.x()*1e5)+":"+Math.round(p.y()*1e5)+":"+Math.round(p.z()*1e5);}
 static void signals(){
  for(boolean left:new boolean[]{false,true}) {
   var spec=Junction22Validation.fixture(Style.O6_RAIL,left,0,90,180,270);var arms=new ArrayList<Arm>();
   for(var arm:spec.arms()){var lanes=new ArrayList<>(arm.lanes());lanes.set(0,lanes.get(0).mask(JunctionSpec.LEFT|JunctionSpec.STRAIGHT).signals(true,-1));arms.add(arm.lanes(lanes));}
   spec=spec.arms(arms);var plan=JunctionPlanner.plan(spec);
   for(int i=0;i<4;i++) {
    var parts=plan.pieces().get(i).structures();
    check(parts.stream().filter(p->p.material()==Material.SIGNAL_VEHICLE).count()==1,"left turn retains exactly one ordinary head");
    check(parts.stream().filter(p->p.material()==Material.SIGNAL_LEFT).count()==1,"one protected left head");
    var old=new JunctionPlanner.Ref(spec,i,24);check(old.get().structures().stream().filter(p->p.material()==Material.SIGNAL_VEHICLE).count()==3,"saved 24 signal layout remains exactly reproducible");
    long near=RoadRenderMesh.structureFaces(parts,false).stream().mapToLong(RoadRenderMesh::vertexCount).sum();
    long far=RoadRenderMesh.structureFaces(parts,true).stream().mapToLong(RoadRenderMesh::vertexCount).sum();
    check(far<near*.25,"far housing vertex budget");
    for(var part:parts)if(RoadSignals.signal(part)){
     var hull=RoadRenderMesh.structureFaces(List.of(part),true);V front=part.b().sub(part.a()).horizontalUnit();
     double solid=hull.stream().flatMap(f->f.points().stream()).mapToDouble(p->p.sub(part.a()).dot(front)).max().orElseThrow();
     double lens=RoadSignalModel.faces(part,true,0).stream().flatMap(f->f.points().stream()).mapToDouble(p->p.sub(part.a()).dot(front)).min().orElseThrow();
     check(solid<lens,"far simplified housing must never occlude working lenses");
    }
   }
  }
 }
 static void curbs(){
  var s=Revision24Validation.main(Style.O4_YELLOW).options(RoadProfile.Options.DEFAULT.extras(false,false,true));Mesh m=RoadGeometry.build(new Node(new V(0,64,0),0,0),new Node(new V(120,64,0),0,0),s);
  for(boolean bridge:new boolean[]{false,true}){
   var parts=RoadStructures.plan(m,Revision24Validation.ground(p->bridge?48:64));
   // A sidewalk curb is a non-pier .2-high concrete edge. Modern bridge piers deliberately
   // use .2-high stepped capital slices, so height/material alone no longer identifies a curb.
   long curb=parts.stream().filter(p->!p.pier()&&p.material()==Material.CONCRETE&&p.height()==.2).count();
   check(bridge?curb==0:curb>0,"only ground road receives sidewalk curb");
   if(bridge)check(parts.stream().filter(p->p.material()==Material.CONCRETE&&p.height()==.2).allMatch(Part::pier),"elevated .2 concrete belongs only to pier capitals, not sidewalk curbs");
   check(parts.stream().anyMatch(p->p.material()==Material.LAMP),"road lighting retained");
  }
 }
 static void heights(){
  var s=Revision24Validation.main(Style.H4_RAIL);
  var options=new Options(Preset.DIRECTIONAL_SIX,false,1,96,20,5,2,0,true,false,0);
  for(double[] heights:List.of(new double[]{90,64,77},new double[]{64,90,77})){
   Node[] nodes=Revision24Validation.nodes(6,1000,0,0,64,0);
   for(int i=0;i<6;i++){V p=nodes[i].position();nodes[i]=new Node(new V(p.x(),heights[i/2],p.z()),0,0);}
   var plan=MultiInterchange.plan(nodes,new Settings[]{s,s,s},options,64);
   for(int axis=0;axis<3;axis++) {
    final double expected=heights[axis];var m=plan.legs().get(axis).mesh();
    check(m.samples().stream().allMatch(p->Math.abs(p.center().y()-expected)<1e-6),"unequal original mainline heights remain unchanged");
   }
  }
  Node[] nodes=Revision24Validation.nodes(6,900,0,0,64,0);double[] bad={64,65,80};
  for(int i=0;i<6;i++){V p=nodes[i].position();nodes[i]=new Node(new V(p.x(),bad[i/2],p.z()),0,0);}
  var fitted=MultiInterchange.plan(nodes,new Settings[]{s,s,s},options,64);
  double a=fitted.anchors().get(0).position().y(),b=fitted.anchors().get(2).position().y(),c=fitted.anchors().get(4).position().y();
  check(a<b&&b<c,"automatic clearance preserves unequal AB/CD/EF ordering");
  check(b-a>=6&&c-b>=6,"insufficient original clearance is widened automatically");
  check(b>65,"automatic endpoint adjustment remains enabled");
 }
 static void multi()throws Exception {
  Files.createDirectories(Path.of("build/validation25"));
  for(int n:new int[]{5,6})for(boolean left:new boolean[]{false,true})for(int lanes:new int[]{1,2}) {
   var main=Revision24Validation.main(left?Style.O4_YELLOW:Style.H4_RAIL);
   var opts=new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,lanes,96,20,5,2,0,true,true,0);
   var p=MultiInterchange.plan(Revision24Validation.nodes(n,640,0,0,64,0),new Settings[]{main,main,main},opts,64);
   check(p.anchors().stream().mapToDouble(a->a.position().y()).min().orElseThrow()==64,"automatic fit keeps the lowest original ground level");
   check(p.legs().get(0).mesh().max().y()>p.legs().get(1).mesh().max().y(),"equal-height conflicts raise AB before CD");
   if(n==6)check(p.legs().get(1).mesh().max().y()>p.legs().get(2).mesh().max().y(),"equal-height conflicts raise CD before EF");
   check(p.legs().get(2).mesh().samples().stream().allMatch(s->s.center().y()==64),"lowest-priority tied mainline stays on original ground plane");
   if(n==5)check(p.legs().get(2).mesh().last().center().sub(p.center()).horizontalLength()>=p.anchors().get(4).position().sub(p.center()).horizontalLength()*.859,"E main terminates at collector root, with no inward dead-end tail");
   Set<Long> chunks=new HashSet<>();Map<String,Set<String>> graph=new HashMap<>();Set<String> edges=new HashSet<>();List<Sample> all=new ArrayList<>();
   for(var l:p.legs()) {
    chunks.addAll(RoadCoverage.chunks(l.mesh(),2));check(RoadGrades.maximum(l.mesh())<=.150001,"bounded road grades");
    if(!l.mesh().settings().style().ramp())continue;
    var ss=l.mesh().samples();all.addAll(ss);
    for(var sample:ss)check(Double.doubleToLongBits(sample.left().y())==0,"horizontal frame uses canonical +0 for exact packed save/load equality");
    for(int k=1;k<ss.size();k++) {
     String a=point(ss.get(k-1).center()),b=point(ss.get(k).center());
     check(edges.add(a+">"+b),"one physical owner for every shared collector segment");graph.computeIfAbsent(a,x->new HashSet<>()).add(b);
    }
   }
   check(graph.values().stream().allMatch(v->v.size()<=2),"collectors have binary branches");
   check(chunks.size()<=RoadLimits.MAX_MULTI_INTERCHANGE_WORK_CHUNKS,"minimum fitted footprint is buildable within its bounded chunk budget");
   // Reachability follows physical shared collector edges, rather than leg labels.
   var roots=new ArrayList<Set<String>>();var tails=new ArrayList<Set<String>>();
   for(int i=0;i<n;i++){roots.add(new HashSet<>());tails.add(new HashSet<>());}
   for(int i=0;i<n;i++){
    V radial=p.anchors().get(i).position().sub(p.center()).horizontalUnit(),in=radial.mul(-1);double radius=p.anchors().get(i).position().sub(p.center()).horizontalLength()*(n==5&&i==4?.86:.97);
    for(Sample sample:all){V delta=sample.center().sub(p.center());if(Math.abs(delta.dot(radial)-radius)>.001)continue;double lateral=delta.dot(in.left())*(left?-1:1);if(Math.abs(delta.dot(in.left()))>main.width()/2+.5)continue;if(lateral>0)roots.get(i).add(point(sample.center()));else tails.get(i).add(point(sample.center()));}
    check(!roots.get(i).isEmpty()&&!tails.get(i).isEmpty(),"both collector endpoints exist");
   }
   for(int i=0;i<n;i++){Set<String> seen=new HashSet<>();var todo=new ArrayDeque<String>();todo.addAll(roots.get(i));while(!todo.isEmpty()){String at=todo.remove();if(!seen.add(at))continue;todo.addAll(graph.getOrDefault(at,Set.of()));}for(int j=0;j<n;j++)if(i/2!=j/2)check(tails.get(j).stream().anyMatch(seen::contains),"all destinations reachable through grouped physical branches");}
   var lines=new ArrayList<String>();lines.add("leg,from,to,x,y,z,width");int leg=0;for(var l:p.legs()){for(var s:l.mesh().samples())lines.add(leg+","+l.from()+","+l.to()+","+s.center().x()+","+s.center().y()+","+s.center().z()+","+s.halfWidth()*2);leg++;}
   if(!left&&lanes==1)Files.write(Path.of("build/validation25/multi"+n+".csv"),lines);
   System.out.printf("25 MULTI %d %s %d lanes: radius %.1f, chunks %d, physical legs %d%n",n,left?"left":"right",lanes,p.anchors().get(0).position().sub(p.center()).horizontalLength(),chunks.size(),p.legs().size());
  }
 }
 public static void main(String[] args)throws Exception{signals();curbs();heights();multi();System.out.printf("Revision 0.25 PASS: %,d checks%n",checks);}
}
