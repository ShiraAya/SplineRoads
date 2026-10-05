package com.sora.splineroads;
import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
/** Real two-road endpoint sections and furniture, not an isolated tunnel mirror test. */
public final class TunnelSeam416Validation {
  static int checks,cases;
  static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
  static void near(double a,double b,String m){check(Math.abs(a-b)<1e-7,m+": "+a+" / "+b);}
  static final Ground G=new Ground(){public double top(double x,double z,double y){return y;}public boolean blocked(Part p){return false;}public boolean joined(V p){return false;}};
  static Settings settings(Style st,Structure structure,boolean left){var o=RoadProfile.Options.DEFAULT.traffic(left);return new Settings(Mode.STRAIGHT,st,RoadProfile.width(st,o,4),1,.35,90).options(o).structure(structure);}
  static Mesh road(Settings settings,boolean atStart){return RoadGeometry.build(new Node(new V(0,100,0),0,0),new Node(new V(0,100,192),0,0),settings);}
  public static void main(String[]args){
    var styles=List.of(Style.O2_YELLOW,Style.O2_RAIL,Style.O2_GREEN,Style.O4_YELLOW,Style.O4_RAIL,Style.O4_GREEN,Style.O6_GREEN,Style.H4_RAIL,Style.H4_GREEN,Style.H6_GREEN);
    for(var a:styles)for(var b:styles)for(boolean left:new boolean[]{false,true})for(boolean atStart:new boolean[]{false,true}){
      var ground=settings(a,Structure.GROUND,left);var tunnel=settings(b,Structure.TUNNEL,left);if(!RoadTransitions.compatible(ground,tunnel))continue;
      var common=RoadTransitions.common(ground,tunnel);check(common.equals(RoadTransitions.common(tunnel,ground)),"order-dependent common endpoint");
      check(RoadProfile.catalog(common.style()).median()!=RoadProfile.Median.GREEN,"tunnel seam kept unsupported greenery");
      var x=road(RoadTransitions.ends(ground,atStart?common:null,atStart?null:common),atStart);var y=road(RoadTransitions.ends(tunnel,atStart?common:null,atStart?null:common),atStart);cases++;
      var lx=RoadProfile.layout(x,atStart?x.first():x.last());var ly=RoadProfile.layout(y,atStart?y.first():y.last());
      near(lx.motorMin(),ly.motorMin(),"motor minimum mismatch");near(lx.motorMax(),ly.motorMax(),"motor maximum mismatch");near(lx.median(),ly.median(),"median reserved width mismatch");near(lx.laneWidth(),ly.laneWidth(),"lane axes mismatch");
      check(lx.catalog().median()==ly.catalog().median(),"material mismatch at shared boundary");check(lx.dividers().equals(ly.dividers()),"stripe IDs differ at seam");
      var out=new ArrayList<Part>();RoadStructures.medianFurniture(y,G,out);check(out.stream().noneMatch(p->p.material()==Material.GREEN||p.material()==Material.SOIL),"tunnel contains planted material");
      if(a==b&&RoadProfile.catalog(a).median()==RoadProfile.Median.GREEN){
        var raw=RoadProfile.layout(ground,ground.width());near(lx.median(),raw.median(),"green conversion squeezed median");near(lx.laneWidth(),raw.laneWidth(),"green conversion shifted live lane axes");
        double end=atStart?0:192;near(RoadTransitions.green(x,RoadStructures.sample(x,end)),0,"ground greenery does not taper to rail at portal");near(RoadTransitions.green(x,RoadStructures.sample(x,96)),1,"far ground lost its greenery");
        check(RoadTransitions.green(x,RoadStructures.sample(x,atStart?18:174))>0&&RoadTransitions.green(x,RoadStructures.sample(x,atStart?18:174))<1,"no continuous material taper");
        var r=RoadTransitions.Section.of(common.settings(false));check(r.equals(common),"port lost when reusing shared section");
        var plain=common.settings(false).options(common.settings(false).options().ends(RoadTransitions.Ends.NONE));
        var xy=RoadTransitions.common(common.settings(false),plain);var yx=RoadTransitions.common(plain,common.settings(false));
        check(xy.equals(yx),"port tie-break changed with neighbor enumeration order");
        check(xy.port()!=null&&xy.port().equals(common.port()),"explicit planned lane axes lost to equal nominal section");
      }
    }
    System.out.println("TunnelSeam416Validation: "+cases+" real ground/tunnel endpoint pairs, "+checks+" checks PASS; no world placement/GPU.");
  }
}
