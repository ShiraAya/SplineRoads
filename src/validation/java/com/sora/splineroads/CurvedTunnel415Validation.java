package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Independent oracle is actual generated roof Part.base triangles, including varying width. */
public final class CurvedTunnel415Validation {
  static int checks,cases;
  static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
  static final Ground G=new Ground(){public double top(double x,double z,double y){return y-20;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  static Mesh road(RoadInfrastructure.Tunnel type,double height,boolean variable,boolean reverse){
    var o=RoadProfile.Options.DEFAULT.infrastructure(RoadInfrastructure.Config.DEFAULT.tunnel(type));var settings=new Settings(Mode.CURVE,Style.O4_RAIL,20,1,.35,90).options(o).structure(Structure.TUNNEL);var samples=new ArrayList<Sample>();
    for(int i=0;i<=96;i++){double d=reverse?96-i:i,angle=d/90;var c=new V(-100000+90*(1-Math.cos(angle)),height+.025*d+.3*Math.sin(d/35),200000+90*Math.sin(angle));var left=new V(-Math.cos(angle),0,Math.sin(angle)).mul(reverse?-1:1);samples.add(new Sample(c,left,i,variable?9+3*Math.sin(d/70):10));}
    return RoadRibbon.mesh(samples,settings);
  }
  public static void main(String[]args){
    for(var type:RoadInfrastructure.Tunnel.values())for(double y:new double[]{-64.5,100,341.5})for(boolean variable:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}){
      var m=road(type,y,variable,reverse);cases++;int tested=0;
      for(var p:RoadInfrastructure.plan(m,G))if(p.material()==Material.TUNNEL&&!p.pier()&&Math.abs(p.height()-.85)<1e-9){
        var q=p.base();for(int k=1;k+1<q.size();k++){var point=q.get(0).add(q.get(k)).add(q.get(k+1)).mul(1.0/3);var lateral=RoadQueries.horizontal(m,point);
          if(Math.abs(lateral.lateral())>lateral.sample().halfWidth()-.3)continue;
          int x=(int)Math.floor(point.x()),z=(int)Math.floor(point.z());double roof=RoadTunnelSpace.ceiling(m,x,z);
          check(Double.isFinite(roof)&&roof>=point.y()-1e-6,"actual curved/variable roof face underestimated");
          check(!RoadTunnelSpace.intersects(m,x,(int)Math.ceil(roof+1e-7),z,1),"exterior above actual lining marked as travel");tested++;
        }
      }
      check(tested>100,"actual roof oracle didn't cover enough faces");
    }
    System.out.println("CurvedTunnel415Validation: "+cases+" curved/variable-width/reverse/high-Y fixtures; "+checks+" generated-roof-face checks PASS, no world/GPU.");
  }
}
