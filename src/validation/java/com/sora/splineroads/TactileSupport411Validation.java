package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Additional actual-paving and trim continuity checks, not screenshots or GPU tests. */
public final class TactileSupport411Validation {
  static int checks,cases;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
  static boolean covered(List<Part> parts,V point){
    for(var part:parts){var polygon=part.base();if(JunctionPaint.inside(polygon,point))return true;
      for(int k=0;k<polygon.size();k++){V a=polygon.get(k),b=polygon.get((k+1)%polygon.size()),d=b.sub(a),q=point.sub(a);
        double t=Math.max(0,Math.min(1,(q.x()*d.x()+q.z()*d.z())/Math.max(1e-12,d.x()*d.x()+d.z()*d.z())));
        if(point.sub(a.add(d.mul(t))).horizontalLength()<.0001)return true;}
    }return false;
  }
  public static void main(String[]args){
    for(var angles:List.of(new double[]{0,80,285},new double[]{0,40,235},new double[]{0,30,210},new double[]{0,120,240},new double[]{0,90,180,270}))
    for(int width:new int[]{3,5})for(double radius:new double[]{2,8})for(double rotation:new double[]{0,37})for(boolean mirror:new boolean[]{false,true}){
      var spec=TactileTunnel409Validation.fixture(angles,width,radius,rotation,mirror);var plan=JunctionPlanner.plan(spec);
      var approaches=plan.pieces().subList(0,angles.length).stream().map(JunctionPlanner.Piece::mesh).toList();
      var parts=plan.pieces().stream().flatMap(p->p.structures().stream()).toList();var floor=new TactileSurface(parts);
      var rows=parts.stream().filter(p->p.material()==Material.TACTILE&&p.height()<.018).toList();
      check(!rows.isEmpty(),"no hidden tactile solution");
      for(var row:rows)for(var v:row.base()){double y=floor.height(v);check(Double.isFinite(y),"every row corner supported by actual paving");check(v.y()>=y-.002&&v.y()<y+.03,"row neither buried nor floating");}
      for(var trim:TactilePaths.trims(spec,approaches,plan.boundary()))for(double d=0;d<=2;d+=.1){
        V point=trim.point().sub(trim.forward().mul(d));
        check(covered(rows,point),"continuous approach into trimmed corner "+Arrays.toString(angles)+" width="+width+" radius="+radius+" rot="+rotation+" mirror="+mirror+" arm="+trim.arm()+" side="+trim.side()+" d="+d+" point="+point);
      }cases++;
    }
    Revision38Validation.main(new String[0]);
    System.out.println("TactileSupport411Validation: "+cases+" junctions, "+checks+" support/trim checks plus prior mixed-disabled/sloping sidewalk regression. NO game/GPU validation.");
  }
}
