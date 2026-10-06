package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadProfile.*;
import java.util.*;
/** Actual core/port snapshots, not a Minecraft client or world-write test. */
public final class Directional427Validation {
 static int checks,cases;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-6,m+": "+a+" != "+b);}
 static Settings settings(Type type,int f,int r,boolean left){
   return RoadLanes.configure(new Settings(Mode.STRAIGHT,Style.O4_RAIL,18,1,.4,90).options(Options.DEFAULT.traffic(left).route(Routing.DEFAULT.fit(false))),type,new RoadLanes.Counts(f,r),type==Type.HIGHWAY?5:4);
 }
 static Mesh mesh(Settings s){return RoadGeometry.build(new Node(new V(0,8,0),0,0),new Node(new V(0,8,240),0,0),s);}
 static void layout(Settings s){
   cases++;var m=mesh(s);var n=RoadLanes.counts(s);var l=RoadProfile.layout(m,m.first());int f=0,r=0;
   near(l.laneWidth(),RoadProfile.highway(s.style())?5:4,"lane width");
   check(l.catalog().lanes()==n.total(),"actual catalog count");
   check(l.dividers().size()==n.total()-(n.twoWay()?2:1),"actual divider count");
   for(int i=0;i<n.total();i++){var lane=LanePoints.lane(m,50,i);if(lane.sign()>0)f++;else r++;
     double o=lane.position().sub(RoadStructures.sample(m,50).center()).dot(m.first().left());
     check(o>l.motorMin()&&o<l.motorMax(),"lane inside motor pavement");
     for(double d:l.dividers())check(Math.abs(d-o)>l.laneWidth()*.49,"not on divider");
   }
   check(f==n.forward()&&r==n.reverse(),"correct forward/reverse counts");
   check(RoadLanes.counts(s.options(s.options().traffic(!s.options().leftTraffic()).outerRail(OuterRail.ON).lift(.6,2).extras(true,false,true).hideArrows(true).ends(RoadTransitions.Ends.NONE))).equals(n),"option edits retain counts");
   var snap=RoadEndpointSections.section(m,false,false);var ls=RoadProfile.layout(snap,snap.width());
   near(ls.motorMin(),l.motorMin(),"snapshot min");near(ls.motorMax(),l.motorMax(),"snapshot max");near(ls.medianCenter(),l.medianCenter(),"snapshot median");
   check(RoadLanes.counts(snap).equals(n),"snapshot counts");
   var rev=RoadEndpointSections.section(m,false,true);near(RoadProfile.layout(rev,rev.width()).medianCenter(),-l.medianCenter(),"reversed median");
   check(RoadLanes.counts(rev).equals(n.mirrored()),"reversed counts");
   check(RoadEndpointSections.orient(RoadEndpointSections.orient(snap,true),true).equals(snap),"reverse twice");
 }
 static void merge(int f,int r,boolean left,int sign){
   cases++;Settings s=settings(Type.ORDINARY,f,r,left);Mesh raw=mesh(s);var l=RoadProfile.layout(raw,raw.first());
   int slot=-1;double furthest=-1;
   for(int i=0;i<f+r;i++){var lane=LanePoints.lane(raw,120,i);if(lane.sign()!=sign)continue;double d=Math.abs(lane.position().sub(RoadStructures.sample(raw,120).center()).dot(raw.first().left())-l.medianCenter());if(d>furthest){furthest=d;slot=i;}}
   var cuts=LaneSections.derive(raw,List.of(new LaneSections.Event(new UUID(427,cases),LaneSections.Kind.DEPART,slot,sign,120,24)));
   Mesh host=LaneSections.apply(mesh(s.options(s.options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts)))));
   boolean first=sign<0;var port=RoadEndpointSections.section(host,first,first);var n=RoadLanes.counts(port);
   int ef=f-(sign>0?1:0),er=r-(sign<0?1:0);
   check(n.equals(first?new RoadLanes.Counts(ef,er).mirrored():new RoadLanes.Counts(ef,er)),"merged port directional counts");
   var at=first?host.first():host.last();var continuation=RoadEndpointSections.inherit(s,port);
   continuation=RoadTransitions.ends(continuation,RoadTransitions.Section.of(port),null);
   V direction=at.left().left().mul(first?1:-1);var a=new Node(at.center(),RoadPlanner.yaw(direction),0);var b=new Node(a.position().add(direction.mul(120)),a.yaw(),0);
   var built=RoadPlanner.plan(new RoadPlanner.Hint(a,true,true,true),RoadPlanner.Hint.free(b),continuation);
   near(built.mesh().first().center().distance(at.center()),0,"physical port centre");near(built.mesh().first().halfWidth(),at.halfWidth(),"physical port width");
   check(built.settings().options().lanePoints().cuts().isEmpty(),"do not copy old cuts into new road");
   near(host.first().center().distance(LaneSections.apply(mesh(s.options(s.options().lanePoints(LanePoints.Data.EMPTY.cuts(cuts))))).first().center()),0,"host axis stays unchanged");
   check(LaneSections.live(host,120+sign*70).count(sign)==(sign>0?f:r)-1,"live after merge");
 }
 public static void main(String[] args){
   for(var type:List.of(Type.ORDINARY,Type.HIGHWAY))for(boolean left:new boolean[]{false,true})for(int f=1;f<=4;f++)for(int r=0;r<=4;r++)layout(settings(type,f,r,left));
   for(boolean left:new boolean[]{false,true})for(int f=2;f<=4;f++)for(int r=2;r<=4;r++)for(int sign:new int[]{-1,1})merge(f,r,left,sign);
   var a=settings(Type.ORDINARY,4,1,false);var b=settings(Type.ORDINARY,1,4,false);
   check(!RoadTransitions.compatible(a,b),"total equal is not per-direction compatible");
   check(RoadTransitions.compatible(settings(Type.ORDINARY,2,1,false),settings(Type.ORDINARY,2,2,false)),"2+1 / 2+2 compatible");
   System.out.println("Directional427Validation: "+cases+" cases / "+checks+" checks PASS; production core, no Minecraft runtime");
 }
}
