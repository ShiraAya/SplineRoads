package com.sora.splineroads.core;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Identifies only the removed sign/pole system; tunnel stripes and gantry frames survive. */
public final class RetiredRoadSigns {
  public static List<Part> removed(Mesh mesh,List<Part> parts){
    Set<Part> attachments=new HashSet<>();
    for(var sign:mesh.settings().options().infrastructure().signs())for(boolean cantilever:List.of(false,true))try{
      var settings=mesh.settings().options(mesh.settings().options().infrastructure(mesh.settings().options().infrastructure().signs(List.of(sign))));
      var single=new Mesh(mesh.samples(),settings,mesh.min(),mesh.max(),mesh.length(),mesh.closed());
      attachments.addAll(RoadSigns.plan(single,new Ground(){
        public double top(double x,double z,double deck){return cantilever?Double.NaN:parts.stream().filter(p->p.pier()&&p.material()==Material.CONCRETE&&Math.abs(p.width()-.65)<1e-8&&Math.abs(p.height()-.3)<1e-8&&Math.hypot(p.a().x()-x,p.a().z()-z)<.01).mapToDouble(p->p.a().y()).findFirst().orElse(Double.NaN);}
        public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}
      }));
    }catch(IllegalArgumentException ignored){/* Invalid legacy attachments still lose their sign mesh. */}
    return parts.stream().filter(p->p.material()==Material.CB_SIGN||attachments.contains(p)||mesh.settings().structure()!=Structure.TUNNEL&&(p.material()==Material.SIGN_GREEN||p.material()==Material.SIGN_BLUE||p.material()==Material.SIGN_WHITE)).toList();
  }
  private RetiredRoadSigns(){}
}
