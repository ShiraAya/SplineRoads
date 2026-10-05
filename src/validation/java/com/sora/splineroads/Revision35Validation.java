package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
public final class Revision35Validation {
 static int checks;static void check(boolean v,String s){checks++;if(!v)throw new AssertionError(s);}
 static long vertices(List<RoadSurface.Face> f){return f.stream().mapToLong(RoadRenderMesh::vertexCount).sum();}
 static Settings road(Style s){return Revision34Validation.road(s);}
 static int interior(Mesh m,Sample sample){var l=RoadTransitions.layout(m,sample);return (int)l.dividers().stream().filter(d->d>l.motorMin()+.01&&d<l.motorMax()-.01).distinct().count();}
 public static void main(String[] args){
  for(boolean left:new boolean[]{false,true})for(Style main:List.of(Style.O2_YELLOW,Style.O6_YELLOW,Style.H8_RAIL))for(Style out:List.of(Style.O1_ONE,Style.O4_ONE))for(Style in:List.of(Style.O1_ONE,Style.O3_ONE)){
   int side=left?-1:1;var a=road(main);a=a.options(a.options().traffic(left));
   var p=YJunctionPlanner.plan(new Node(new V(0,2,0),180,0),new Node(new V(side*60,2,-180),180,0),new Node(new V(-side*60,2,-180),0,0),a,road(out),road(in),.4);
   int half=RoadProfile.catalog(main).lanes()/2;
   check(RoadProfile.catalog(p.outbound().settings().style()).lanes()==RoadProfile.catalog(out).lanes(),"outbound uses B base lanes");
   check(RoadProfile.catalog(p.inbound().settings().style()).lanes()==half,"inbound uses A receiving lanes");
   check(interior(p.outbound(),p.outbound().first())==half-1,"A outbound seam count");
   check(interior(p.outbound(),p.outbound().last())==RoadProfile.catalog(out).lanes()-1,"B seam count");
   check(interior(p.inbound(),p.inbound().first())==RoadProfile.catalog(in).lanes()-1,"C seam count");
   check(interior(p.inbound(),p.inbound().last())==half-1,"A inbound seam count");
   for(Mesh m:List.of(p.stem(),p.outbound(),p.inbound())){RoadGrades.validate(m);for(Sample s:m.samples())check(Double.isFinite(s.halfWidth())&&s.halfWidth()>0,"positive continuous width");}
  }
  var s=road(Style.O6_YELLOW);s=s.options(s.options().sidewalk(new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks")));
  Mesh mesh=RoadGeometry.build(new Node(new V(0,20,0),-87,0),new Node(new V(800,20,0),-93,0),s);
  Ground ground=new Ground(){public double top(double x,double z,double y){return 0;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  var parts=RoadStructures.plan(mesh,ground);var saved=List.copyOf(parts);
  long near=vertices(RoadRenderMesh.structureFaces(parts,false)),far=vertices(RoadRenderMesh.structureFaces(parts,true));
  check(near<100_000&&far<65_000,"curved facility render budget");check(parts.equals(saved),"authoritative structures retained");
  var oldBounds=new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
  for(Part p:parts)for(var f:p.faces())for(V v:f.points()){oldBounds[0]=Math.min(oldBounds[0],v.x());oldBounds[1]=Math.min(oldBounds[1],v.y());oldBounds[2]=Math.min(oldBounds[2],v.z());oldBounds[3]=Math.max(oldBounds[3],v.x());oldBounds[4]=Math.max(oldBounds[4],v.y());oldBounds[5]=Math.max(oldBounds[5],v.z());}
  var sections=RoadRenderMesh.sections(RoadSurface.build(RoadRenderMesh.simplify(mesh),List.of(),List.of()),parts);
  for(var e:sections.entrySet())for(var list:List.of(e.getValue().detail(),e.getValue().distant()))for(var face:list)for(V p:face.points())check(p.x()>=e.getKey().origin().x()-1e-6&&p.x()<=e.getKey().origin().x()+64+1e-6&&p.z()>=e.getKey().origin().z()-1e-6&&p.z()<=e.getKey().origin().z()+64+1e-6,"LOD stays within spatial region");
  RenderValidation.run();
  System.out.println("800-block curve structures near="+near+" far="+far);
  for(double amplitude:new double[]{12,40})for(boolean upperAB:new boolean[]{false,true}){
   var a=Revision34Validation.axis(false,upperAB?30:20,amplitude,-350,350);var b=Revision34Validation.axis(true,upperAB?20:30,-amplitude,-350,350);
   var n=new Node[]{new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0),new Node(b.at(b.length()),0,0)};
   var options=new Options(Preset.STACK,false,1,96,20,5,1).adjust(true);var h=road(Style.H4_RAIL);
   var plan=CurvedRoadPlans.interchange(n,h,h,options,List.of(a,b));
   double ya=plan.anchors().get(0).position().y(),yb=plan.anchors().get(2).position().y();
   check((ya>yb)==upperAB,"original upper axis remains upper");check(Math.abs(ya-yb)>=21,"insufficient gap raised to fit");check(plan.minRadius()+.1>=20,"mapped radius meets request");
   for(int i=0;i<4;i++)check(plan.anchors().get(i).position().y()>=n[i].position().y(),"no unrequested lowering");
   for(var leg:plan.legs())RoadGrades.validate(leg.mesh());
   var fitted=new ArrayList<RoadAxis>();var axes=List.of(a,b);
   for(int i=0;i<2;i++){var axis=axes.get(i);double start=axis.project(plan.anchors().get(2*i).position()),end=axis.project(plan.anchors().get(2*i+1).position());fitted.add(axis.slice(start,end,plan.anchors().get(2*i).position().y()-axis.at(start).y()));}
   var again=CurvedRoadPlans.interchange(plan.anchors().toArray(Node[]::new),h,h,options,fitted);
   for(int i=0;i<4;i++)check(again.anchors().get(i).position().distance(plan.anchors().get(i).position())<.02,"reopen endpoint stability");
   System.out.println("Stack low-gap fit PASS amplitude="+amplitude+" upperAB="+upperAB+" minRadius="+plan.minRadius());
  }
  System.out.println("Revision35 PASS "+checks+" checks");
 }
}
