package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Full-width auxiliary lane follows its host: a taper and a genuinely parallel running lane. */
public final class LaneRampApproach {
  public static double taper(double transition){return Math.max(16,transition);}
  public static double parallel(double transition){return Math.max(16,transition);}
  public static double length(double transition){return taper(transition)+parallel(transition);}

  public static List<Sample> build(Mesh host,int lane,double station,boolean source,double width,double transition){
    return build(host,lane,station,source,width,transition,0);
  }
  public static List<Sample> build(Mesh host,int lane,double station,boolean source,Settings ramp,double transition){
    return build(host,lane,station,source,ramp.width(),transition,RoadProfile.layout(ramp,ramp.width()).motorCenter());
  }
  private static List<Sample> build(Mesh host,int lane,double station,boolean source,double width,double transition,double motorCenter){
    var selected=LanePoints.lane(host,station,lane);int sign=selected.sign();double length=length(transition);
    double from=source?station:station-sign*length,to=source?station+sign*length:station;
    if(Math.min(from,to)<-1e-6||Math.max(from,to)>host.length()+1e-6)
      throw new IllegalArgumentException((source?"汇出":"汇入")+"额外车道需要沿主路 "+(int)length+" 格（渐变段＋并行段），当前位置空间不足");
    var out=new ArrayList<Sample>();int n=(int)Math.ceil(length/.5);double distance=0;
    for(int i=0;i<=n;i++){
      double d=length*i/n,at=from+sign*d;var s=RoadStructures.sample(host,at);var l=LanePoints.lane(host,at,lane);
      var layout=RoadProfile.layout(host,s);double lateral=l.position().sub(s.center()).dot(s.left());
      int side=layout.catalog().twoWay()?(lateral<layout.medianCenter()?-1:1):layout.outside();
      // A narrow overlap seals the seam without putting the host footing
      // inside the vehicle corridor of an approaching lower ramp.
      double offset=s.halfWidth()+width/2-.02-side*lateral+side*motorCenter;
      double grow=Settings.smooth(Math.min(1,(source?d:length-d)/taper(transition)));
      V position=l.position().add(s.left().mul(side*offset*grow));
      if(i>0)distance+=position.sub(out.get(i-1).center()).horizontalLength();
      double halfWidth=(Math.min(width,l.width())+(width-Math.min(width,l.width()))*grow)/2;
      out.add(new Sample(position,l.direction().left(),distance,halfWidth));
    }
    // Tangents of the actual curved/tapered lane, with exact host headings at either join.
    var result=new ArrayList<Sample>();
    for(int i=0;i<out.size();i++){
      var s=out.get(i);V direction=i==0||i==out.size()-1?s.left().left().mul(-1):out.get(i+1).center().sub(out.get(i-1).center()).horizontalUnit();
      result.add(new Sample(s.center(),direction.left(),s.distance(),s.halfWidth()));
    }
    return List.copyOf(result);
  }
  private LaneRampApproach(){}
}
