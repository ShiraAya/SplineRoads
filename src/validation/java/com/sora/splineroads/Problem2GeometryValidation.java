package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;
/** Production core tests; no level writes, Minecraft client or GPU. */
public final class Problem2GeometryValidation {
  static int checks,fixtures;
  static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  static Settings settings(){return new Settings(Mode.STRAIGHT,Style.O2_ONE,9,1,.35,90);}
  static Mesh mesh(V a,V b){double grade=(b.y()-a.y())/b.sub(a).horizontalLength();var na=new Node(a,RoadPlanner.yaw(b.sub(a).horizontalUnit()),grade);return RoadGeometry.build(na,new Node(b,na.yaw(),grade),settings());}
  static boolean oldInvades(RoadStructures.Part p,Mesh road){double lo=Math.min(p.a().y(),p.b().y())-p.verticalFrame(),hi=Math.max(p.a().y(),p.b().y())+p.height()+p.verticalFrame();var q=RoadQueries.horizontal(road,p.a().add(p.b()).mul(.5));return hi>q.sample().center().y()-road.settings().thickness()+.04&&lo<q.sample().center().y()+4.25&&RoadSidewalks.overlapsDeck(p,road);}
  public static void main(String[]args){
    for(double offset:List.of(0.0,100000.0))for(double y:List.of(2.0,100.0,341.0))for(boolean reverse:new boolean[]{false,true}){
      V a=new V(offset+85,y,-30),b=new V(offset+85,y,30);var road=mesh(reverse?b:a,reverse?a:b);
      var highEnd=new RoadStructures.Part(new V(offset-100,y,0),new V(offset+100,y+20,0),1,1,false,RoadStructures.Material.TUNNEL);
      check(oldInvades(highEnd,road),"fixture must reproduce old midpoint/global-height false positive");
      check(!RoadClearance.structureInvades(highEnd,road,4.25),"non-overlapping sloped shell must not block");
      var low=new RoadStructures.Part(new V(offset+80,y+1,-20),new V(offset+90,y+1,20),1,3,false,RoadStructures.Material.TUNNEL);
      check(RoadClearance.structureInvades(low,road,4.25),"real low shell still blocks");
      var safe=new RoadStructures.Part(new V(offset+80,y+5,-20),new V(offset+90,y+5,20),1,1,false,RoadStructures.Material.TUNNEL);
      check(!RoadClearance.structureInvades(safe,road,4.25),"safe roof not rejected");fixtures++;
    }
    for(double angle:List.of(0.0,.4,1.2))for(double grade:List.of(0.0,.05,-.05))for(double y:List.of(2.0,341.0)){
      V dir=new V(Math.sin(angle),0,Math.cos(angle)),a=new V(1000,y,1000),b=a.add(dir.mul(100)).add(new V(0,grade*100,0));
      var na=new Node(a,RoadPlanner.yaw(dir),grade);var nb=new Node(b,na.yaw(),grade);
      var ha=new RoadPlanner.Hint(na,true,true,true);var hb=new RoadPlanner.Hint(nb,true,true,true);
      var plan=RoadPlanner.plan(RoadPlanner.Hint.free(na),RoadPlanner.Hint.free(nb),settings());
      RoadConnectionChecks.require(plan,ha,hb);checks++;
      boolean rejected=false;try{RoadConnectionChecks.require(plan,new RoadPlanner.Hint(new Node(a,na.yaw()+5,grade),true,true,true),hb);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("A")&&e.getMessage().contains("方向或坡度");}
      check(rejected,"manual shape with existing mismatched heading rejected before green preview");
      rejected=false;try{RoadConnectionChecks.require(plan,ha,new RoadPlanner.Hint(new Node(b,nb.yaw(),grade+.02),true,true,true));}catch(IllegalArgumentException e){rejected=e.getMessage().contains("B")&&e.getMessage().contains("方向或坡度");}
      check(rejected,"mismatched seam grade rejected");
      RoadConnectionChecks.require(plan,RoadPlanner.Hint.free(new Node(a,70,.3)),null);checks++;
      var automatic=RoadPlanner.plan(ha,hb,RoadPlanner.mode(settings(),Mode.AUTO,90));RoadConnectionChecks.require(automatic,ha,hb);checks++;fixtures++;
    }
    for(var type:List.of(Structure.TUNNEL,Structure.BRIDGE))for(var adjust:List.of(RoadTunnelFit.Adjustment.BOTH,RoadTunnelFit.Adjustment.START,RoadTunnelFit.Adjustment.END)){
      var settings=RoadPlanner.mode(settings(),Mode.AUTO,90).structure(type);
      settings=settings.options(settings.options().infrastructure(settings.options().infrastructure().adjustment(adjust)));
      var a=new RoadPlanner.Hint(new Node(new V(0,100,0),0,0),true,true,true);
      var b=new RoadPlanner.Hint(new Node(new V(0,120,12),0,0),true,true,true);
      var plan=RoadTunnelFit.plan(a,b,settings);RoadConnectionChecks.require(plan,a,b);checks++;fixtures++;
      check(plan.mesh().length()>12,"fixture really extended steep bridge/tunnel");
      if(adjust==RoadTunnelFit.Adjustment.START)check(plan.end().position().distance(b.node().position())<1e-8,"fixed B retained");
      if(adjust==RoadTunnelFit.Adjustment.END)check(plan.start().position().distance(a.node().position())<1e-8,"fixed A retained");
    }
    for(double y:List.of(2.0,100.0,341.0))for(double angle:List.of(0.0,.4)){
      V d=new V(Math.sin(angle),0,Math.cos(angle));var original=new RoadPlanner.Hint(new Node(new V(0,y+.5,0),RoadPlanner.yaw(d),.1),true,true,true);
      var a=RoadConnectionChecks.atLevel(original,y);var b=RoadConnectionChecks.atLevel(new RoadPlanner.Hint(new Node(d.mul(100).add(new V(0,y+.5,0)),RoadPlanner.yaw(d),.1),true,true,true),y);
      var plan=RoadPlanner.plan(RoadPlanner.Hint.free(a.node()),RoadPlanner.Hint.free(b.node()),settings());RoadConnectionChecks.require(plan,a,b);checks++;
      check(a.node().position().y()==y&&a.node().grade()==0&&a.gradeLocked()&&a.linked(),"client level normalization matches server contract");fixtures++;
    }
    check(RoadConnectionChecks.atLevel(null,0)==null,"missing seam remains free");
    System.out.println("Problem2GeometryValidation: "+fixtures+" geometry fixtures, "+checks+" checks; old false-positive reproduced, exact prism and shared linked-seam preflight PASS. No game/client run.");
  }
}
