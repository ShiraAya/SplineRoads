package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Physical asymmetric lane cuts; production CPU core, not Minecraft block placement. */
public final class Bidirectional402Validation {
  private static int checks, cases;
  private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
  private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-6,why+": "+a+" vs "+b);}
  private static void denied(Runnable r){boolean denied=false;try{r.run();}catch(IllegalArgumentException expected){denied=true;}check(denied,"unsafe cut accepted");}
  private static Mesh road(Style style,boolean left,Structure structure,double rotation){
    var o=RoadProfile.Options.DEFAULT.traffic(left).extras(true,true,true);
    var s=new Settings(Mode.STRAIGHT,style,RoadProfile.width(style,o,4),1,.35,90).options(o).structure(structure);
    V a=new V(100000,100,-100000),b=a.add(new V(Math.sin(rotation),0,Math.cos(rotation)).mul(500));
    double yaw=RoadPlanner.yaw(b.sub(a));return RoadGeometry.build(new Node(a,yaw,0),new Node(b,yaw,0),s);
  }
  private static Mesh cuts(Mesh raw,List<LaneSections.Cut> cuts){return LaneSections.apply(RoadRibbon.mesh(raw.samples(),raw.settings().options(raw.settings().options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts)))));}
  public static void main(String[]args){
    for(var style:List.of(Style.O2_YELLOW,Style.O2_RAIL,Style.O2_GREEN,Style.O4_YELLOW,Style.O6_RAIL,Style.O8_GREEN,Style.H4_RAIL,Style.H6_GREEN,Style.H8_RAIL))
      for(boolean left:new boolean[]{false,true})for(var structure:List.of(Structure.GROUND,Structure.BRIDGE))for(double rotation:new double[]{0,.6}){
        var raw=road(style,left,structure,rotation);int count=RoadProfile.catalog(style).lanes(),per=count/2;
        for(int slot:new int[]{per-1,count-1}){
          int sign=LanePoints.lane(raw,250,slot).sign();double begin=sign>0?80:420,end=sign>0?420:80;
          var departure=new LaneSections.Event(new UUID(402,1),LaneSections.Kind.DEPART,slot,sign,begin,32);
          var replacement=new LaneSections.Event(new UUID(402,2),LaneSections.Kind.REPLACE,slot,sign,end,32);
          var events=LaneSections.derive(raw,List.of(replacement,departure));var mesh=cuts(raw,events);cases++;
          for(double station:new double[]{40,96,112,200,250,388,404,460}){
            var old=RoadStructures.sample(raw,station);var at=RoadStructures.sample(mesh,station);
            var a=RoadProfile.layout(raw,old);var b=RoadProfile.layout(mesh,at);int side=slot<per?-1:1;
            near(at.at(b.medianCenter(),0).distance(old.center()),0,"world median moved");
            near(at.at(b.outer(-side),0).distance(old.at(a.outer(-side),0)),0,"opposite outer edge moved");
            near(at.halfWidth()*2,old.halfWidth()*2-events.get(0).removed(station)*a.laneWidth(),"physical pavement width");
            for(int i=0;i<count;i++)if(i!=slot){var before=LanePoints.lane(raw,station,i);var after=LanePoints.lane(mesh,station,i);near(before.position().distance(after.position()),0,"retained lane axis");near(before.direction().dot(after.direction()),1,"retained lane direction");}
            check(b.dividers().size()==a.dividers().size(),"cut renumbered stripe identity");
            for(int i=0;i<b.dividers().size();i++)check((i<(count-2)/2?-1:1)*(b.dividers().get(i)-b.medianCenter())>=b.median()/2-1e-7,"stripe crosses median");
          }
          var middle=RoadStructures.sample(mesh,250);var l=RoadProfile.layout(mesh,middle);int side=slot<per?-1:1;
          check(l.lanesOnSide(side)==per-1&&l.lanesOnSide(-side)==per,"asymmetric directional counts");
          check(!LaneSections.active(mesh,250,slot),"departure remains active");
          var edge=RoadStructures.sample(raw,250);check(!RoadQueries.contains(mesh,edge.at(side*(edge.halfWidth()-.25),0),0,.1),"collision extends beyond narrowed outside edge");
          near(RoadStructures.sample(mesh,460).halfWidth(),RoadStructures.sample(raw,460).halfWidth(),"replacement did not restore full width");
          var permanent=cuts(raw,LaneSections.derive(raw,List.of(departure)));check(!LaneSections.active(permanent,sign>0?499:1,slot),"wrong downstream end");
          check(cuts(raw,List.of()).samples().equals(raw.samples()),"delete does not restore raw geometry");
          var resolved=RoadStreetscape.resolve(mesh,new RoadStructures.Ground(){public double top(double x,double z,double y){return y-20;}public boolean blocked(RoadStructures.Part p){return false;}public boolean joined(V p){return false;}});
          check(resolved.reference()!=null,"streetscape lost raw slot reference");var rp=RoadStructures.sample(resolved,250);
          near(rp.at(RoadProfile.layout(resolved,rp).medianCenter(),0).distance(RoadStructures.sample(raw,250).center()),0,"raised median recentered after streetscape resolve");
          if(per>1)denied(()->LaneSections.derive(raw,List.of(new LaneSections.Event(new UUID(402,3),LaneSections.Kind.DEPART,0,LanePoints.lane(raw,250,0).sign(),250,32))));
          denied(()->LaneSections.derive(raw,List.of(new LaneSections.Event(new UUID(402,4),LaneSections.Kind.DEPART,slot,-sign,250,32))));
        }
      }
    System.out.println("Bidirectional402Validation: "+cases+" asymmetric cut cases, "+checks+" assertions PASS; real core, NO game/GPU/world-write claim.");
  }
}
