package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Joint longitudinal feasibility, not a separate up-and-down bump per obstacle.
 * Bounds describe real crossing decks. The fixed host approaches remain fixed;
 * all other samples share the same horizontal-arc-length slope budget. */
public final class LaneRampCorridor {
  public static Mesh solve(Mesh base,double freeFrom,double freeTo,List<LaneRampHeights.Constraint> constraints,boolean over,double grade){
    LaneRampGrade.checked(grade);int n=base.samples().size();
    double[] x=new double[n],lo=new double[n],hi=new double[n],y=new double[n];
    for(int i=0;i<n;i++){
      var s=base.samples().get(i);y[i]=s.center().y();
      if(i>0)x[i]=x[i-1]+s.center().sub(base.samples().get(i-1).center()).horizontalLength();
      lo[i]=over?y[i]:Double.NEGATIVE_INFINITY;hi[i]=over?Double.POSITIVE_INFINITY:y[i];
    }
    for(var c:constraints)if(c.amount()>.001){
      if(c.amount()>48)throw new IllegalArgumentException("自动跨越需要升降超过 48 格，请扩大道路间距或修改端点高度");
      for(int i=0;i<n;i++){
        double station=base.samples().get(i).distance();
        // Include the ends of every intersected surface triangle; otherwise a bound
        // can be met at sampled centres but missed at a clipped polygon corner.
        double previous=i==0?station:base.samples().get(i-1).distance();
        double next=i==n-1?station:base.samples().get(i+1).distance();
        if(next<c.from()-1e-7||previous>c.to()+1e-7)continue;
        if(over)lo[i]=Math.max(lo[i],y[i]+c.amount());else hi[i]=Math.min(hi[i],y[i]-c.amount());
      }
    }
    // Keep one real segment at each port, including its signed tangent, rather than
    // silently flattening a sloping existing lane to satisfy a solver constraint.
    for(int i=0;i<n;i++)if(i<=1||i>=n-2||base.samples().get(i).distance()<=freeFrom+1e-7||base.samples().get(i).distance()>=freeTo-1e-7){
      if(y[i]<lo[i]-1e-7||y[i]>hi[i]+1e-7)throw failure(base,freeFrom,freeTo,grade,i,"固定接头与真实障碍冲突");
      lo[i]=hi[i]=y[i];
    }
    double[] lower=lo.clone(),upper=hi.clone();
    int direction=y[n-1]>y[0]+1e-7?1:y[n-1]<y[0]-1e-7?-1:0;
    boolean tangentAllows=direction==0?Math.abs(y[1]-y[0])<1e-7&&Math.abs(y[n-1]-y[n-2])<1e-7:
        direction*(y[1]-y[0])>=-1e-7&&direction*(y[n-1]-y[n-2])>=-1e-7;
    // First try the monotone solution. A higher target alone never mandates a sag;
    // only an actual crossing bound can require a crest/valley.
    int monotone=tangentAllows?direction:2;
    if(monotone==2||!propagate(x,lo,hi,grade,monotone)){
      lo=lower;hi=upper;monotone=2;
      if(!propagate(x,lo,hi,grade,monotone))throw failure(base,freeFrom,freeTo,grade,firstConflict(lo,hi),"端口、障碍与逐段坡比约束无法同时满足");
    }
    for(int i=0;i<n;i++)y[i]=clamp(y[i],lo[i],hi[i]);
    // Clamping to propagated Lipschitz bounds is feasible. Relax curvature while
    // staying inside both the physical corridor and neighbouring grade limits.
    for(int pass=0;pass<36;pass++)for(int k=1;k<n-1;k++){
      int i=(pass&1)==0?k:n-1-k;double dl=x[i]-x[i-1],dr=x[i+1]-x[i];
      if(dl<1e-9||dr<1e-9)continue;
      double low=Math.max(lo[i],Math.max(y[i-1]-grade*dl,y[i+1]-grade*dr));
      double high=Math.min(hi[i],Math.min(y[i-1]+grade*dl,y[i+1]+grade*dr));
      if(monotone==1){low=Math.max(low,y[i-1]);high=Math.min(high,y[i+1]);}
      else if(monotone==-1){low=Math.max(low,y[i+1]);high=Math.min(high,y[i-1]);}
      else if(monotone==0){low=Math.max(low,y[0]);high=Math.min(high,y[0]);}
      if(low>high+1e-6)continue;
      double target=(y[i-1]*dr+y[i+1]*dl)/(dl+dr);
      y[i]=clamp(.55*y[i]+.45*target,low,high);
    }
    var result=new ArrayList<Sample>();
    for(int i=0;i<n;i++){
      var s=base.samples().get(i);
      if(i>0&&LaneRampGrade.exceeds(y[i]-y[i-1],x[i]-x[i-1],grade))throw failure(base,freeFrom,freeTo,grade,i,"平滑后的局部坡比未通过");
      if(y[i]<lo[i]-1e-6||y[i]>hi[i]+1e-6)throw failure(base,freeFrom,freeTo,grade,i,"平滑后的净空边界未通过");
      result.add(new Sample(new V(s.center().x(),y[i],s.center().z()),s.left(),s.distance(),s.halfWidth()));
    }
    Mesh mesh=RoadRibbon.mesh(result,base.settings());RoadRibbon.checkSelfIntersections(mesh,4);LaneRampPaths.checkVolume(mesh);return mesh;
  }
  private static boolean propagate(double[] x,double[] lo,double[] hi,double grade,int monotone){
    for(int pass=0;pass<2;pass++){
      for(int i=1;i<x.length;i++){
        double dx=x[i]-x[i-1],down=monotone==1||monotone==0?0:grade*dx,up=monotone==-1||monotone==0?0:grade*dx;
        lo[i]=Math.max(lo[i],lo[i-1]-down);hi[i]=Math.min(hi[i],hi[i-1]+up);
      }
      for(int i=x.length-2;i>=0;i--){
        double dx=x[i+1]-x[i],down=monotone==-1||monotone==0?0:grade*dx,up=monotone==1||monotone==0?0:grade*dx;
        lo[i]=Math.max(lo[i],lo[i+1]-down);hi[i]=Math.min(hi[i],hi[i+1]+up);
      }
    }
    for(int i=0;i<x.length;i++)if(lo[i]>hi[i]+1e-7)return false;return true;
  }
  private static int firstConflict(double[] lo,double[] hi){for(int i=0;i<lo.length;i++)if(lo[i]>hi[i]+1e-7)return i;return 0;}
  private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
  private static IllegalArgumentException failure(Mesh m,double from,double to,double grade,int i,String reason){
    double horizontal=0;for(int k=1;k<m.samples().size();k++)horizontal+=m.samples().get(k).center().sub(m.samples().get(k-1).center()).horizontalLength();
    return new IllegalArgumentException(String.format(Locale.ROOT,"%s：总水平路径 %.1f 格，可调整区间 %.1f–%.1f 格，冲突站位 %.1f，端点高差 %.2f 格，上限 %s；请检查接头方向/相交位置，而非仅按端点平均坡比判断",reason,horizontal,from,to,m.samples().get(i).distance(),m.last().center().y()-m.first().center().y(),LaneRampGrade.label(grade)));
  }
  private LaneRampCorridor(){}
}
