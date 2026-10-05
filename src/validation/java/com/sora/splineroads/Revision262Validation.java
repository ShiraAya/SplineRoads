package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.InterchangePlanner.*;
import java.util.*;

/** Vertical smoothness, compact footprint and shoulder tapers reported after 0.26.1. */
public final class Revision262Validation {
  static int checks;
  static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
  static Plan fixture(int n,Style style,int lanes,boolean left,double lead,boolean shrink){
    Settings s=new Settings(Mode.STRAIGHT,style,style.defaultWidth(),1,.4,90);
    return MultiInterchange.plan(Revision26Validation.nodes(n,900,true),new Settings[]{s,s,s},
        new Options(n==5?Preset.DIRECTIONAL_FIVE:Preset.DIRECTIONAL_SIX,left,lanes,lead,20,5,2,0,true,shrink,0),2);
  }
  public static double slew(Mesh m){
    double previous=0,max=0;boolean seen=false;var points=m.samples();
    for(int i=1;i<points.size();i++){
      V delta=points.get(i).center().sub(points.get(i-1).center());
      if(delta.horizontalLength()<1e-8)continue;
      double grade=delta.y()/delta.horizontalLength();if(seen)max=Math.max(max,Math.abs(grade-previous));
      previous=grade;seen=true;
    }return max;
  }
  public static double straight(Mesh m){
    double max=0,run=0;V previous=null;
    for(int i=1;i<m.samples().size();i++){
      V delta=m.samples().get(i).center().sub(m.samples().get(i-1).center()),direction=delta.horizontalUnit();
      double length=delta.horizontalLength();
      if(previous!=null&&direction.dot(previous)>1-1e-10)run+=length;else run=length;
      previous=direction;max=Math.max(max,run);
    }return max;
  }
  static void profile(Plan p,int n,Style style,int lanes){
    double maxSlew=0,maxStraight=0,minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
    for(Leg l:p.legs()){
      Mesh m=l.mesh();minX=Math.min(minX,m.min().x());maxX=Math.max(maxX,m.max().x());minZ=Math.min(minZ,m.min().z());maxZ=Math.max(maxZ,m.max().z());
      if(!m.settings().style().ramp())continue;
      maxSlew=Math.max(maxSlew,slew(m));maxStraight=Math.max(maxStraight,straight(m));
      check(RoadGrades.maximum(m)<=.150001,"retained 15% grade limit");
      // A smooth climb/cruise/descent has at most one reversal, even if a shared
      // trunk was removed from the saved movement segment.
      int previous=0,reversals=0;
      for(int k=1;k<m.samples().size();k++){
        double dy=m.samples().get(k).center().y()-m.samples().get(k-1).center().y();
        int sign=Math.abs(dy)<1e-7?0:dy>0?1:-1;
        if(sign!=0){if(previous!=0&&previous!=sign)reversals++;previous=sign;}
      }
      check(reversals<=1,"no repeated vertical humps");
    }
    check(maxSlew<.025,"no abrupt sampled grade kinks: "+maxSlew);
    check(Math.max(maxX-minX,maxZ-minZ)<1350,"default footprint stays compact");
    check(maxStraight<210,"no long parallel waiting runway: "+maxStraight);
    System.out.printf(Locale.ROOT,"COMPACT n=%d style=%s lanes=%d box=%.1fx%.1f height=%.3f maxStraight=%.2f maxGradeChange=%.6f%n",n,style,lanes,maxX-minX,maxZ-minZ,p.highest(),maxStraight,maxSlew);
  }
  static void shoulder(Plan p){
    var meshes=p.legs().stream().map(Leg::mesh).toList();int inspected=0;
    for(Mesh ramp:meshes)if(ramp.settings().style().ramp())for(boolean first:new boolean[]{true,false}){
      Sample end=first?ramp.first():ramp.last();
      if(end.halfWidth()*2>=ramp.settings().width()*.7)continue;
      boolean attached=meshes.stream().filter(m->!m.settings().style().ramp()).anyMatch(main->{
        var q=RoadQueries.horizontal(main,end.center());return Math.abs(q.sample().center().y()-end.center().y())<.1&&q.horizontalDistance()<q.sample().halfWidth()+end.halfWidth();});
      if(!attached)continue;
      inspected++;var peers=new ArrayList<>(meshes);peers.remove(ramp);var zones=RoadJunction.dividerZones(ramp,peers);
      for(double d=0;d<Math.min(90,ramp.length()*.6);d+=.5){
        double station=first?d:ramp.length()-d;var at=RoadStructures.sample(ramp,station);
        if(at.halfWidth()*2>=ramp.settings().width()*.94)break;
        check(zones.stream().anyMatch(z->z.contains(station)),"no lane divider inside unfinished shoulder taper");
      }
    }
    check(inspected>=8,"test covers arrival and departure highway mouths");
  }
  public static void main(String[] args){
    for(int n:new int[]{5,6})for(Style style:new Style[]{Style.H4_RAIL,Style.H6_RAIL})for(int lanes:new int[]{1,2}){
      Plan p=fixture(n,style,lanes,false,96,true);profile(p,n,style,lanes);if(lanes==2)shoulder(p);
    }
    for(boolean left:new boolean[]{false,true})shoulder(fixture(6,Style.H6_RAIL,2,left,192,false));
    System.out.println("Revision262 PASS "+checks+" checks");
  }
}
