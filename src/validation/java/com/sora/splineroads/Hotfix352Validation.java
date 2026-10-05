package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;
public final class Hotfix352Validation {
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static final Ground FLOOR=new Ground(){public double top(double x,double z,double y){return 1;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
 public static void main(String[] args){
  var s=new Settings(Mode.CURVE,Style.H6_RAIL,Style.H6_RAIL.defaultWidth(),1,.4,60);
  var a=RoadAxis.of(RoadGeometry.build(new Node(new V(-350,20,0),-70,0),new Node(new V(350,20,0),-110,0),s));
  var b=RoadAxis.of(RoadGeometry.build(new Node(new V(0,30,-350),-20,0),new Node(new V(0,30,350),20,0),s));
  var nodes=new Node[]{new Node(a.at(0),-90,0),new Node(a.at(a.length()),-90,0),new Node(b.at(0),0,0),new Node(b.at(b.length()),0,0)};
  var request=new Options(Preset.STACK,false,1,96,20,5,1).adjust(true);
  var plan=CurvedRoadPlans.interchange(nodes,s,s,request,List.of(a,b));
  check(plan.minRadius()+.1>=20,"20-degree curved hosts: actual radius meets request");
  check(Math.abs(plan.anchors().get(0).position().y()-plan.anchors().get(2).position().y())>=21,"required height gap");
  for(var leg:plan.legs()){RoadGrades.validate(leg.mesh());RoadRibbon.checkSelfIntersections(leg.mesh(),5);}
  System.out.println("CURVE FIT formerly 11.9, now "+plan.minRadius()+" (required 20)");
  for(var style:List.of(Style.O6_GREEN,Style.H6_GREEN))for(var mode:List.of(Mode.STRAIGHT,Mode.CURVE))for(var side:List.of(RoadProfile.OuterRail.SOUND_LEFT,RoadProfile.OuterRail.SOUND_RIGHT,RoadProfile.OuterRail.SOUND_BOTH)){
   var settings=new Settings(mode,style,style.defaultWidth(),1,.4,60);settings=settings.options(settings.options().outerRail(side));
   var mesh=RoadGeometry.build(new Node(new V(0,12,0),-90,0),new Node(new V(100,12,0),-90,0),settings);
   var parts=RoadStructures.plan(mesh,FLOOR);var panels=parts.stream().filter(p->p.material()==Material.CB_NOISE).toList();
   check(panels.size()>40,"continuous 2m CB panels");
   check(parts.stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"elevated green median becomes barrier");
   check(parts.stream().anyMatch(p->p.pier()&&p.material()==Material.CONCRETE&&p.height()>2),"barriers retain piers");
   for(var panel:panels){
    double sign=Math.signum(panel.a().z());check(side.sound((int)sign),"selected side only");
    var near=panel.faces();var far=RoadNoiseModel.faces(panel,true);check(near.size()==990,"original CB detail");check(far.size()<near.size()/4,"distant subpixel details omitted");
    for(var face:near){check(face.uv().size()==4,"original UVs");for(var p:face.points())check(p.y()>=12-1e-6&&p.y()<=12+RoadNoiseModel.HEIGHT+1e-6,"model inside collision height");}
   }
   var atGround=RoadGeometry.build(new Node(new V(0,1,0),-90,0),new Node(new V(100,1,0),-90,0),settings);
   var groundParts=RoadStructures.plan(atGround,FLOOR);check(groundParts.stream().anyMatch(p->p.material()==Material.GREEN),"real ground median stays green");check(groundParts.stream().noneMatch(p->p.material()==Material.CB_NOISE),"AUTO noise limited to elevated sections");
  }
  // Do not classify an isolated missing marker column as a bridge.
  var surface=new Settings(Mode.STRAIGHT,Style.O6_GREEN,Style.O6_GREEN.defaultWidth(),1,.4,60);
  var flat=RoadGeometry.build(new Node(new V(0,1,0),-90,0),new Node(new V(100,1,0),-90,0),surface);
  var hole=new Ground(){public double top(double x,double z,double y){return Math.abs(x-50)<.5&&Math.abs(z)<.5?0:1;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  check(!RoadStructures.elevated(flat,RoadStructures.sample(flat,50),hole),"one marker hole remains ground");
  System.out.println("Hotfix352 PASS "+checks+" checks");
 }
}
