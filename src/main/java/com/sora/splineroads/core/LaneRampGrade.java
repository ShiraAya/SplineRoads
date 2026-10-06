package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.Locale;

/** Connector-only grade policy. Legacy overloads and automatic interchanges retain 15%. */
public final class LaneRampGrade {
  public static final double LEGACY=.15;
  public static double limit(boolean involvesHighway,boolean override){
    return involvesHighway?(override?.20:.15):(override?.25:.20);
  }
  public static double checked(double limit){
    if(!Double.isFinite(limit)||limit<.01||limit>.25)throw new IllegalArgumentException("匝道坡比上限无效");
    return limit;
  }
  public static boolean exceeds(double rise,double run,double limit){
    return !RoadGeometry.finite(rise,run,limit)||run<0||Math.abs(rise)>(limit+.000001)*run+1e-9;
  }
  public static String label(double limit){return String.format(Locale.ROOT,"%.0f%%",limit*100);}
  public static void validate(Mesh mesh,double limit){
    checked(limit);
    for(int i=1;i<mesh.samples().size();i++){
      V d=mesh.samples().get(i).center().sub(mesh.samples().get(i-1).center());
      if(exceeds(d.y(),d.horizontalLength(),limit))throw new IllegalArgumentException("匝道实际路面坡度超过 "+label(limit)+"，请扩大间距或降低高差");
    }
  }
  private LaneRampGrade(){}
}
