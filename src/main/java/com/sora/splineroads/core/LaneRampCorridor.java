package com.sora.splineroads.core;

import com.sora.splineroads.core.RoadGeometry.*;
import java.util.*;

/** Joint longitudinal feasibility, not a separate up-and-down bump per obstacle.
 * Bounds describe real crossing decks. The fixed host approaches remain fixed;
 * all other samples share the same horizontal-arc-length slope budget. */
public final class LaneRampCorridor {
  public static Mesh solve(Mesh base,double freeFrom,double freeTo,List<LaneRampHeights.Constraint> constraints,boolean over,double grade){
    return solveMixed(base,freeFrom,freeTo,constraints.stream().map(c->new Bound(c.from(),c.to(),c.amount(),over)).toList(),grade);
  }
  public record Bound(double from,double to,double amount,boolean over) {
    public Bound {if(!RoadGeometry.finite(from,to,amount)||from>to||amount<0)throw new IllegalArgumentException("跨越高程约束无效");}
  }
  /** AUTO may pass over a low road and under another elevated road in one route. */
  public static Mesh solveMixed(Mesh base,double freeFrom,double freeTo,List<Bound> constraints,double grade){
    LaneRampGrade.checked(grade);int n=base.samples().size();
    double[] x=new double[n],lo=new double[n],hi=new double[n],y=new double[n];
    for(int i=0;i<n;i++){
      var s=base.samples().get(i);y[i]=s.center().y();
      if(i>0)x[i]=x[i-1]+s.center().sub(base.samples().get(i-1).center()).horizontalLength();
      // An old height bump is not an obstacle. Only real crossing windows and
      // fixed ports bound the new profile; otherwise a spurious crest above B
      // makes a genuinely feasible monotone route appear impossible.
      lo[i]=Double.NEGATIVE_INFINITY;hi[i]=Double.POSITIVE_INFINITY;
    }
    // Zero-lift contacts still constrain already-clear decks: relaxing a prior
    // crest must not erase a real over/under relationship elsewhere on the route.
    for(var c:constraints){
      if(c.amount()>48)throw new IllegalArgumentException("自动跨越需要升降超过 48 格，请扩大道路间距或修改端点高度");
      for(int i=0;i<n;i++){
        double station=base.samples().get(i).distance();
        // Include the ends of every intersected surface triangle; otherwise a bound
        // can be met at sampled centres but missed at a clipped polygon corner.
        double previous=i==0?station:base.samples().get(i-1).distance();
        double next=i==n-1?station:base.samples().get(i+1).distance();
        if(next<c.from()-1e-7||previous>c.to()+1e-7)continue;
        if(c.over())lo[i]=Math.max(lo[i],y[i]+c.amount());else hi[i]=Math.min(hi[i],y[i]-c.amount());
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
    // A feasible envelope is not a feasible profile: independently clamping an
    // oscillating input can still reverse the slope inside a monotone corridor.
    // Select forward inside the backwards-propagated bounds. Each selected sample
    // therefore has a feasible continuation to the fixed destination, including its
    // actual tangent. Do this before smoothing, rather than hoping relaxation will
    // eventually remove a 300-metre sag.
    // Fit through the feasible corridor, not through the old local bump/plateau
    // samples. The old samples are only evidence for obstacle bounds and locked
    // ports; using them as the objective preserves long, unnecessary level steps.
    double[] desired=tautProfile(x,lo,hi,y[0],y[n-1]);
    y[0]=clamp(y[0],lo[0],hi[0]);
    for(int i=1;i<n;i++){
      double dx=x[i]-x[i-1];
      double down=monotone==1||monotone==0?0:grade*dx;
      double up=monotone==-1||monotone==0?0:grade*dx;
      double low=Math.max(lo[i],y[i-1]-down),high=Math.min(hi[i],y[i-1]+up);
      if(low>high+1e-7)throw failure(base,freeFrom,freeTo,grade,i,"坡线连续解不可达");
      y[i]=clamp(desired[i],low,high);
    }
    // Relax curvature while staying inside both the physical corridor and
    // neighbouring grade/direction limits; feasibility is already established.
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
      if(i>0&&monotone!=2&&(monotone==0?Math.abs(y[i]-y[i-1])>1e-6:monotone*(y[i]-y[i-1])< -1e-6))
        throw failure(base,freeFrom,freeTo,grade,i,"无障碍反向起伏未消除");
      result.add(new Sample(new V(s.center().x(),y[i],s.center().z()),s.left(),s.distance(),s.halfWidth()));
    }
    Mesh mesh=RoadRibbon.mesh(result,base.settings());RoadRibbon.checkSelfIntersections(mesh,4);LaneRampPaths.checkVolume(mesh);return mesh;
  }
  /** Piecewise linear whole-span target, split only at a binding corridor wall.
   * Stack-based to bound recursion depth. The following reachability pass still
   * enforces every grade and direction constraint; this is not a safety bypass. */
  private static double[] tautProfile(double[] x,double[] lo,double[] hi,double first,double last){
    int n=x.length;double[] result=new double[n];result[0]=first;result[n-1]=last;
    var spans=new ArrayDeque<int[]>();spans.push(new int[]{0,n-1});
    while(!spans.isEmpty()){
      var span=spans.pop();int a=span[0],b=span[1],split=-1;double worst=1e-7,value=0;
      double length=x[b]-x[a];
      for(int i=a+1;i<b;i++){
        double t=length<1e-9?0:(x[i]-x[a])/length;
        double target=result[a]+t*(result[b]-result[a]);
        double bound=clamp(target,lo[i],hi[i]),violation=Math.abs(target-bound);
        if(violation>worst){worst=violation;split=i;value=bound;}
        result[i]=target;
      }
      if(split>=0){result[split]=value;spans.push(new int[]{split,b});spans.push(new int[]{a,split});}
    }
    return result;
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
    var metrics=LaneRampGrade.report(m,from,to,grade);
    double rise=Math.abs(m.last().center().y()-m.first().center().y());
    var sample=m.samples().get(i);var point=sample.center();
    boolean locked=i<=1||i>=m.samples().size()-2||sample.distance()<=from+1e-7||sample.distance()>=to-1e-7;
    return new IllegalArgumentException(String.format(Locale.ROOT,
        "%s；冲突在%s X=%.1f Y=%.1f Z=%.1f（冲突站位／距 A 沿线 %.1f 格，距 B %.1f 格）。%s输入最大坡比 %.2f%% / 上限 %s；总水平路径 %.1f 格，可布坡 %.1f 格，端点变高至少需 %.1f 格。%s",
        reason,locked?"固定接头":"可调坡段",point.x(),point.y(),point.z(),sample.distance(),Math.max(0,m.length()-sample.distance()),
        metrics.maximum()<=grade+1e-7?"这不是输入坡比超限，而是接头／障碍所需高程与局部可达范围冲突。":"输入路线自身也存在坡比超限。",
        metrics.maximum()*100,LaneRampGrade.label(grade),metrics.horizontal(),metrics.available(),rise/grade,
        locked?"此处必须保持原车道高度；请改变跨越位置或接头位置，单纯提高坡比不能解除固定接头碰撞。":"请检查该位置的上下跨越关系及距固定接头的升降空间；总长度足够不代表局部净空可达。"));
  }
  private LaneRampCorridor(){}
}
