package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;

/** Screenshot regressions against production meshes, solids and painted faces. No GPU. */
public final class Live435Validation {
  static int checks;
  static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
  static Mesh road(Style style,Structure structure){
    var options=RoadProfile.Options.DEFAULT;
    var settings=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,options,4),1,.35,90).options(options).structure(structure);
    var samples=new ArrayList<Sample>();
    for(double d=0;d<=200;d+=.25)samples.add(new Sample(new V(0,20,d),new V(1,0,0),d,settings.width()/2));
    return RoadRibbon.mesh(samples,settings);
  }
  static Mesh cut(Mesh raw,int slot,double from,double to,boolean arrival){
    var c=new LaneSections.Cut(new UUID(435,1),slot,LanePoints.lane(raw,from,slot).sign(),from,to,32,null,true,arrival,true);
    return LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(List.of(c))))));
  }
  static Ground ground(double y){return new Ground(){public double top(double x,double z,double at){return y;}public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}};}
  static void greenery(){
    var raw=road(Style.O3_ONE,Structure.GROUND);
    for(double length:new double[]{.5,8,11.9,12,30}){
      var parts=LaneClosureLandscape.plan(cut(raw,1,50,50+length,false),ground(19.8));
      check(!parts.isEmpty(),"closure foundation vanished");
      check(parts.stream().anyMatch(p->p.material()==Material.GREEN)==(length>=12),"12 m threshold not respected: "+length);
      check(parts.stream().anyMatch(p->p.material()==Material.SOIL)==(length>=12),"short closure exposes soil");
      for(var p:parts)for(var v:p.base())check(v.z()>=50-1e-6&&v.z()<=50+length+1e-6,"planter outside reserved interval");
      if(length>=12){
        var plants=parts.stream().filter(p->p.material()==Material.GREEN).toList();
        check(plants.get(0).frameA().horizontalLength()<plants.get(2).frameA().horizontalLength(),"planting front not tapered");
        check(parts.stream().filter(p->p.material()==Material.CONCRETE&&Math.abs(p.a().z()-p.b().z())<1e-7).count()==2,"missing concrete end caps");
      }
    }
    var obstacle=new Part(new V(0,19,64),new V(0,19,66),4,3,false,Material.CONCRETE);
    Ground blocked=new Ground(){public double top(double x,double z,double at){return 19.8;}public boolean joined(V p){return false;}public boolean blocked(Part p){return RoadSolidOverlap.intersects(p,obstacle);}};
    var parts=LaneClosureLandscape.plan(cut(raw,1,50,80,false),blocked);
    check(parts.stream().anyMatch(p->p.material()==Material.GREEN),"valid long planting runs discarded");
    for(var p:parts)check(!blocked.blocked(p),"planting/foundation penetrates obstacle");
  }
  static void markings(){
    var mesh=cut(road(Style.O3_ONE,Structure.GROUND),1,80,140,true);
    var geometry=RoadSurface.build(mesh,List.of(),List.of());
    for(double d:new double[]{58.5,64.5,70.5,76.5}){
      var lane=LanePoints.lane(mesh,d,1);var sample=RoadStructures.sample(mesh,d);
      for(int side:new int[]{-1,1}){
        var p=lane.position().add(sample.left().mul(side*lane.width()/2));
        check(geometry.markings().stream().anyMatch(f->f.color()==0xEDEEE2&&JunctionPaint.inside(f.points(),p)),"warning lane still has dash gap");
      }
    }
    var lane=LanePoints.lane(mesh,40.5,1);
    check(!geometry.markings().stream().anyMatch(f->JunctionPaint.inside(f.points(),lane.position().add(new V(2,0,0)))),"solid line leaked upstream");
  }
  static void structures(){
    var raw=road(Style.O3_ONE,Structure.BRIDGE);var mesh=cut(raw,0,50,150,false);
    for(double d:new double[]{60,100,140}){
      var sample=RoadStructures.sample(mesh,d);
      check(!RoadQueries.contains(mesh,sample.at(-sample.halfWidth()+.05,0),0,.01),"closed outer shoulder strip remains");
      check(RoadQueries.contains(mesh,LanePoints.lane(raw,d,1).position(),0,.01),"adjacent lane removed");
    }
    for(var p:RoadStructures.plan(mesh,ground(0)))if(p.material()==Material.STEEL&&!p.pier()&&p.height()<.2&&p.a().y()<22&&p.a().z()>60&&p.b().z()<140)
      check(p.a().x()>-raw.first().halfWidth()+.5,"useless exterior rail remains beside closed lane: "+p);
    for(var p:RoadStreetscape.plan(mesh,ground(0),RoadFurniture.Phase.DEFAULT))if(p.a().z()>60&&p.a().z()<140)
      check(p.a().x()>-raw.first().halfWidth()+.5,"exterior lamp remains over the removed shoulder");
    var ramp=road(Style.C1_RAMP,Structure.BRIDGE);
    var supports=RoadStructures.supports(ramp,ground(0),RoadFurniture.Phase.DEFAULT);
    check(!supports.isEmpty(),"ramp has no supports");
    for(var p:supports)check(p.pier()&&Math.abs(p.width()-1.5)<1e-8,"connector pier differs from interchange pier");
    var opening=new LanePoints.Opening(new UUID(435,2),List.of(new V(100,20,0),new V(100,20,200)),2);
    ramp=RoadRibbon.mesh(ramp.samples(),ramp.settings().options(ramp.settings().options().lanePoints(LanePoints.Data.EMPTY.openings(List.of(opening)))));
    Ground blockedBase=new Ground(){public double top(double x,double z,double at){return 0;}public boolean joined(V p){return false;}public boolean blocked(Part p){return p.material()==Material.CONCRETE&&p.a().y()>=19.9;}};
    var unblocked=RoadStructures.plan(ramp,ground(0));var blocked=RoadStructures.plan(ramp,blockedBase);
    check(unblocked.stream().anyMatch(p->p.material()==Material.STEEL&&Math.abs(p.a().y()-20.65)<.01),"fixture lacks elevated rails");
    check(blocked.stream().noneMatch(p->p.material()==Material.STEEL&&Math.abs(p.a().y()-20.65)<.01),"rail floats after its concrete base was removed");
  }
  static void curves(){
    var settings=road(Style.C1_RAMP,Structure.AUTO).settings();
    for(int mirror:new int[]{-1,1})for(int turn=0;turn<4;turn++){
      double angle=turn*Math.PI/2;V forward=new V(Math.sin(angle),0,Math.cos(angle)),right=forward.left().mul(mirror);
      V start=new V(300,20,500),end=start.add(forward.mul(160)).add(right.mul(160)).add(new V(0,8,0));
      var a=new LaneRampPaths.Port(start,forward,right,0,0);var b=new LaneRampPaths.Port(end,right,forward,0,0);
      var paths=LaneRampPaths.smoothTurns(a,b,settings,LanePoints.Options.DEFAULT,.2);
      check(!paths.isEmpty(),"smooth turn missing for rotation/mirror");
      for(var path:paths){var m=path.mesh();check(m.first().center().distance(start)<1e-6&&m.last().center().distance(end)<1e-6,"smooth endpoints moved");
        check(RoadRibbon.start(m).direction().dot(forward)>.999&&RoadRibbon.end(m).direction().dot(right)>.999,"smooth seam heading changed");
        check(RoadRibbon.minRadius(m)>=23.999,"smooth curve violates requested radius");LaneRampGrade.validate(m,.2);
        double maxTurn=0;int changed=0;for(int i=1;i<m.samples().size();i++){double dot=Math.max(-1,Math.min(1,m.samples().get(i-1).left().dot(m.samples().get(i).left())));double delta=Math.acos(dot);maxTurn=Math.max(maxTurn,delta);if(delta>1e-5)changed++;}
        check(maxTurn<.025,"visible hard turn in smooth curve");check(changed>m.samples().size()*.9,"most of curve is a straight stub");
      }
    }
  }
  public static void main(String[] args){
    check(RoadClearance.clearanceLabel(-1).contains("实体重叠 1.00"),"negative clearance mislabeled as free air");
    var contact=new RoadClearance.Contact(0,1,new V(0,0,0),new V(0,0,0),-1,5,5);
    check(contact.blocked()&&contact.usableClearance()==-1,"diagnostic hides a real overlap");
    greenery();markings();structures();curves();
    System.out.println("Live435Validation: "+checks+" checks PASS; production geometry/paint/solids, NO GPU.");
  }
}
