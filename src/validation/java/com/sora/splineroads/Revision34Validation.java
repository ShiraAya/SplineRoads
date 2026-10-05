package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import com.sora.splineroads.core.CorridorPlanner.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
public final class Revision34Validation {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Settings road(Style s){return new Settings(Mode.CURVE,s,s.defaultWidth(),1,.4,90);}
 static RoadAxis axis(boolean vertical,double y,double amplitude,double start,double end){var pts=new ArrayList<V>();for(int i=0;i<=800;i++){double t=i/800.0,x=start+(end-start)*t,z=amplitude*Math.sin(Math.PI*t);pts.add(vertical?new V(z,y,x):new V(x,y,z));}return new RoadAxis(pts);}
 public static void main(String[] args)throws Exception{
  var folder=java.nio.file.Path.of("validation/0.34.0");java.nio.file.Files.createDirectories(folder);
  for(boolean left:new boolean[]{false,true}){
   var main=road(Style.O4_YELLOW);var one=road(Style.O2_ONE);var walk=new RoadSidewalks.Config(true,RoadSidewalks.Side.BOTH,5,"minecraft:stone_bricks");
   main=main.options(main.options().sidewalk(walk).traffic(left).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT));one=one.options(one.options().sidewalk(walk).traffic(left).cycleFinish(RoadProfile.Options.CycleFinish.ASPHALT));
   int sign=left?-1:1;var y=YJunctionPlanner.plan(new Node(new V(0,0,0),180,0),new Node(new V(28*sign,0,-100),180,0),new Node(new V(-28*sign,0,-100),0,0),main,one,one,.4);
   var meshes=List.of(y.stem(),y.outbound(),y.inbound());var all=new ArrayList<Part>();
   for(Mesh m:meshes){for(Sample s:m.samples())check(!RoadTransitions.greenCycle(m,s),"asphalt survives full Y section including throat");var p=YJunctionWalks.apply(m,y.stem(),y.outbound(),y.inbound(),RoadSidewalks.parts(m,m.settings().options().sidewalk()));all.addAll(new SidewalkJoins(meshes,List.of()).clip(p));}
   var pocket=YJunctionWalks.plan(y.stem(),y.outbound(),y.inbound());check(pocket!=null&&!pocket.fill().isEmpty()&&!pocket.tactile().isEmpty(),"Y island has sidewalk and tactile bend");
   for(Part p:pocket.tactile())if(p.width()>.1){V center=p.a().add(p.b()).mul(.5);check(all.stream().filter(q->q.material().name().startsWith("WALK_")).anyMatch(q->JunctionPaint.inside(q.base(),center)),"Y tactile remains over sidewalk "+center);}
   var svg=new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='-55 -110 110 125'><rect x='-55' y='-110' width='110' height='125' fill='#496449'/>");
   for(Mesh m:meshes)for(var face:RoadSurface.build(m,List.of(),List.of()).pavement())Revision33Validation.polygon(svg,face.points(),"#454b50");
   for(Part p:all)if(p.material().name().startsWith("WALK_"))Revision33Validation.polygon(svg,p.base(),"#adb5b8");for(Part p:all)if(p.material()==Material.TACTILE)Revision33Validation.polygon(svg,p.base(),"#f6cb31");
   java.nio.file.Files.writeString(folder.resolve("Y-"+left+".svg"),svg.append("</svg>").toString());
  }
  var main=road(Style.H4_RAIL);var frontage=road(Style.O2_ONE);
  var config=new Config(Kind.FRONTAGE,Sides.BOTH,Access.BOTH,12,20);
  var options=new Options(Preset.CLOVERLEAF,false,1,72,16,5,1).adjust(true);
  for(boolean slope:new boolean[]{false,true})for(boolean curve:new boolean[]{false,true}){
   RoadAxis path=axis(false,20,curve?60:0,0,700);if(slope){var pts=new ArrayList<V>();for(V p:path.points())pts.add(new V(p.x(),20+p.x()/70,p.z()));path=new RoadAxis(pts);}
   Node[] n={new Node(path.at(0),-90,0),new Node(path.at(path.length()),-90,0)};
   var p=CurvedRoadPlans.corridor(n,main,frontage,options,config,List.of(path));
   check(p.movements()==(slope?0:4),"frontage ramp rule");check(p.anchors().equals(List.of(n)),"frontage endpoints retained");
   for(Leg leg:p.legs())if(leg.name().contains("辅路"))for(Sample s:leg.mesh().samples())check(Math.abs(s.center().y()-20)<1e-7,"frontage constant A height");
   for(Sample s:p.legs().get(0).mesh().samples())check(s.center().distance(path.at(path.project(s.center())))<.015,"main curve and grade preserved");
   System.out.println("Frontage PASS slope="+slope+" curve="+curve);
  }
  for(int count:new int[]{3,4})for(Preset preset:CurvedRoadPlans.presets(count,true)){
   var a=axis(false,20,12,-480,480);var b=axis(true,preset==Preset.CLOVERLEAF?27:48,-10,-480,count==3?0:480);
   Node[] n=count==3?new Node[]{new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0)}:new Node[]{new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0),new Node(b.at(b.length()),0,0)};
   var o=new Options(preset,false,1,96,16,5,1).adjust(true);
   System.out.println("Testing curved "+preset);
   var plan=CurvedRoadPlans.interchange(n,main,main,o,List.of(a,b));check(plan.movements()==(count==3?4:8),"all movements for "+preset);
   for(int axis=0;axis<2;axis++){RoadAxis source=axis==0?a:b;for(Sample s:plan.legs().get(axis).mesh().samples())check(s.center().sub(source.at(source.project(s.center()))).horizontalLength()<.03,"host curve retained for "+preset);}
   var fittedAxes=new ArrayList<RoadAxis>();var originals=List.of(a,b);
   for(int ax=0;ax<2;ax++){RoadAxis source=originals.get(ax);double start=source.project(plan.anchors().get(ax*2).position()),end=ax*2+1<count?source.project(plan.anchors().get(ax*2+1).position()):source.length();fittedAxes.add(source.slice(start,end,plan.anchors().get(ax*2).position().y()-source.at(start).y()));}
   var again=CurvedRoadPlans.interchange(plan.anchors().toArray(Node[]::new),main,main,o,fittedAxes);
   for(int k=0;k<count;k++)check(again.anchors().get(k).position().distance(plan.anchors().get(k).position())<.02,"unchanged curved layout retains fitted anchors for "+preset);
   System.out.println("Interchange PASS "+preset+" roads="+plan.legs().size()+" radius="+plan.minRadius());
  }
  {
   var ordinary=road(Style.O2_YELLOW);Node[] n={new Node(new V(-400,20,0),-88,0),new Node(new V(400,20,0),-92,0),new Node(new V(0,48,-400),2,0),new Node(new V(0,48,400),-2,0)};
   var axes=List.of(RoadAxis.of(RoadGeometry.build(n[0],n[1],ordinary)),RoadAxis.of(RoadGeometry.build(n[2],n[3],ordinary)));
   var p=CurvedRoadPlans.interchange(n,ordinary,ordinary,new Options(Preset.HYBRID,false,1,96,16,5,1).adjust(true),axes);check(p.movements()==8,"ordinary curved hybrid");System.out.println("Ordinary HYBRID PASS");
  }
  for(int curvedAxis=0;curvedAxis<2;curvedAxis++){
   RoadAxis a=axis(false,20,curvedAxis==0?18:0,-480,480),b=axis(true,48,curvedAxis==1?-18:0,-480,480);
   Node[] n={new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0),new Node(b.at(b.length()),0,0)};
   var p=CurvedRoadPlans.interchange(n,main,main,new Options(Preset.STACK,true,1,96,16,5,1).adjust(true),List.of(a,b));check(p.movements()==8,"either host may curve with left traffic");
  }
  check(CurvedRoadPlans.presets(4,true).size()==3&&CurvedRoadPlans.presets(3,true).size()==3&&CurvedRoadPlans.presets(4,false).size()==7,"exact preset menus");
  System.out.println("Revision34 PASS "+checks+" geometry checks");
 }
}
