package com.sora.splineroads.core;
/** Endpoint-tangent profile with LOCAL slope correction. A long flat span must not
 * amplify a short downhill port tangent into a deep sag proportional to total length. */
public final class LaneRampProfile {
  public static double height(double from,double to,double firstGrade,double lastGrade,double length,double station){
    if(!RoadGeometry.finite(from,to,firstGrade,lastGrade,length,station)||length<=0)throw new IllegalArgumentException("无效匝道纵断面");
    double d=Math.max(0,Math.min(length,station)),g=(to-from)/length;
    // Keep existing Hermite for flat ports: its bounded peak grade is already validated.
    if(Math.abs(firstGrade)<1e-10&&Math.abs(lastGrade)<1e-10)return from+(to-from)*RoadGeometry.Settings.smooth(d/length);
    double span=Math.min(length/2,Math.max(8,Math.min(24,length/4)));
    return from+g*d+(firstGrade-g)*bump(d,span)-(lastGrade-g)*bump(length-d,span);
  }
  private static double bump(double d,double span){if(d>=span)return 0;double t=1-d/span;return d*t*t;}
  private LaneRampProfile(){}
}
