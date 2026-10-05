package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;

/** Reproduce screenshot defects using the same geometric output consumed by the renderer. */
public final class Revision14Validation {
  private static int checks;
  private static void check(boolean ok, String message) {
    checks++; if (!ok) throw new AssertionError(message);
  }
  private static Settings setting(Style style) {
    return new Settings(Mode.STRAIGHT, style, style.defaultWidth(), 1, .4, 90);
  }
  private static boolean inside(List<V> poly, V p) {
    double sign=0;
    for(int i=0;i<poly.size();i++) {
      V a=poly.get(i),b=poly.get((i+1)%poly.size());
      double c=(b.x()-a.x())*(p.z()-a.z())-(b.z()-a.z())*(p.x()-a.x());
      if(Math.abs(c)<1e-7)continue;
      if(sign==0)sign=Math.signum(c);else if(sign*c<0)return false;
    }
    return sign!=0;
  }
  public static void main(String[] args) throws Exception { run(); }
  public static void run() throws Exception {
    List<InterchangePlanner.Plan> pictures=new ArrayList<>();
    for(var style:List.of(Style.O4_GREEN,Style.H6_RAIL))
      for(boolean left:new boolean[]{false,true})
        for(var preset:List.of(InterchangePlanner.Preset.DIAMOND,InterchangePlanner.Preset.SPUI)) {
          var s=setting(style);
          var o=new InterchangePlanner.Options(preset,left,1,96,20,5,2);
          var plan=InterchangePlanner.plan(InterchangeValidation.raised(500,.37,
              InterchangePlanner.minimumDifference(s,s,o),true),s,s,o);
          if(style==Style.O4_GREEN&&!left)pictures.add(plan);
          var all=plan.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
          int pads=0;
          for(Mesh m:all) {
            if(m.settings().style()==Style.UNMARKED)pads++;
            var neighbors=all.stream().filter(n->n!=m).toList();
            var entries=RoadSignals.approaches(m,neighbors);
            if(entries.isEmpty())continue;
            var simplified=RoadRenderMesh.simplify(m);
            var renderEntries=RoadSignals.approaches(simplified,neighbors);
            check(entries.size()==renderEntries.size(),"stop count independent of render sampling");
            for(int i=0;i<entries.size();i++)check(entries.get(i).inner().distance(renderEntries.get(i).inner())<.08,
                "stop location independent of render sampling");
            var paint=RoadSurface.build(simplified,List.of(),neighbors).markings();
            for(var a:entries) {
              V mid=a.inner().add(a.outer()).mul(.5),f=a.forward();
              check(a.head().sub(mid).dot(f)>5,"head is beyond the intersection");
              check(Math.abs(a.head().sub(mid).dot(f.left()))<.01,"head over controlled lane");
              check(RoadSignals.furnitureClear(a.inner().add(f.mul(.4)),entries),"clear median beyond stop");
              check(RoadSignals.furnitureClear(a.post(),entries),"streetlight excludes signal post");
              for(var face:paint) {
                V p=face.points().stream().reduce(new V(0,0,0),V::add).mul(1.0/face.points().size());
                double ahead=p.sub(mid).dot(f),side=Math.abs(p.sub(mid).dot(f.left()));
                check(!(ahead>.25&&ahead<.65&&side<mid.distance(a.outer())-.3),
                    "paint must end at stop line: "+preset+" "+style+" "+ahead+" "+side);
              }
              var heads=RoadSignals.structures(List.of(a)).stream().filter(RoadSignals::signal).toList();
              check(heads.size()==1,"one CB head per approach");
              for(var p:heads) {
                check(p.b().sub(p.a()).dot(f)<0,"signal faces approaching driver");
                var housing=p.faces();
                check(housing.size()>100,"actual CB authored housing geometry present");
                check(housing.stream().noneMatch(RoadSurface.Face::emissive),"housing is not emissive");
                for(var face:housing)for(V v:face.points())
                  if(RoadQueries.horizontal(all.get(0),v).horizontalDistance()<all.get(0).first().halfWidth())
                    check(v.y()<all.get(0).first().center().y()-s.thickness()-.1,"CB head fits below SPUI bridge");
              }
              if(!a.ramp()) {
                for(double back:new double[]{3.1,7.1,13.1,21.1}) {
                  var at=RoadStructures.sample(m,a.station()-a.direction()*back);
                  for(double divider:RoadProfile.layout(m,at).dividers()) {
                    if(!RoadSignals.solid(entries,at.distance(),divider))continue;
                    V point=at.at(divider,0);
                    check(paint.stream().anyMatch(face->inside(face.points(),point)),"continuous solid divider before stop");
                  }
                }
              }
            }
          }
          check(pads==(preset==InterchangePlanner.Preset.SPUI?1:2),"SPUI is one intersection, diamond two");
          if(preset==InterchangePlanner.Preset.SPUI) {
            V axis=all.get(0).first().left();
            for(var leg:plan.legs())if(leg.mesh().settings().style().ramp()) {
              Mesh r=leg.mesh();
              var end=Math.abs(r.first().center().y()-all.get(1).first().center().y())<.01?r.first():r.last();
              check(Math.abs(end.center().sub(plan.center()).dot(axis))>s.width()/2,
                  "four SPUI ramps stay outside the main bridge");
            }
          }
        }
    var s=setting(Style.H6_GREEN);
    var o=new InterchangePlanner.Options(InterchangePlanner.Preset.STACK,false,1,96,20,5,2,0,true).shrink(true);
    var nodes=InterchangeValidation.raised(700,0,40,false);
    for(double cap:new double[]{0,2,8,64}) {
      var p=InterchangePlanner.plan(nodes,s,s,o.lowering(cap));
      for(int i=0;i<nodes.length;i++)check(nodes[i].position().y()-p.anchors().get(i).position().y()<=cap+1e-7,
          "actual endpoint lowering respects slider cap "+cap);
      var fixed=InterchangePlanner.plan(p.anchors().toArray(Node[]::new),s,s,o.adjust(false).lowering(cap));
      check(fixed.legs().equals(p.legs()),"preview and fixed geometry agree after capped fit");
    }
    for(var style:List.of(Style.O4_RAIL,Style.H6_RAIL)) {
      s=setting(style);o=new InterchangePlanner.Options(InterchangePlanner.Preset.CLOVERLEAF,false,1,96,20,5,2);
      var p=InterchangePlanner.plan(InterchangeValidation.raised(500,0,16,false),s,s,o);
      var all=p.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
      for(var leg:p.legs())if(leg.name().startsWith("连续集散道")) {
        var neighbors=all.stream().filter(m->m!=leg.mesh()).toList();
        check(RoadJunction.arrows(leg.mesh(),neighbors).isEmpty(),"collector has no merge arrow");
        var paint=RoadJunction.markings(leg.mesh(),all.subList(0,2),neighbors);
        check(paint.size()>15,"shared lane has dashed separator");
        for(var f:paint)check(f.points().get(0).distance(f.points().get(2))<1.7,"shared separator contains no fake long gore");
      }
      pictures.add(p);
    }
    s=setting(Style.O4_GREEN);
    Mesh lower=RoadGeometry.build(new Node(new V(0,64,0),0,0),new Node(new V(0,64,100),0,0),s);
    Part good=new Part(new V(0,63,50),new V(0,63,50),1.5,12,true);
    check(RoadStructures.fitsMedian(good,lower,List.of(lower)),"aligned pier fits planted median");
    Part lane=new Part(new V(4,63,50),new V(4,63,50),1.5,12,true);
    check(!RoadStructures.fitsMedian(lane,lower,List.of(lower)),"pier in a driving lane rejected");
    var ground=new Ground(){public double top(double x,double z,double y){return 64;}
      public boolean joined(V p){return false;}public boolean blocked(Part p){return false;}};
    var furniture=RoadStructures.plan(lower,ground);
    for(var part:furniture) {
      if(part.material()==Material.GREEN)for(var f:part.faces())
        check(f.points().stream().anyMatch(v->v.y()>part.a().y()+1e-7),"leaf bottom face removed at soil interface");
      if(part.material()==Material.STEEL||part.material()==Material.DARK_STEEL)
        check(part.faces().stream().noneMatch(RoadSurface.Face::emissive),"metal never self illuminates");
    }
    for(var preset:List.of(InterchangePlanner.Preset.TRUMPET,InterchangePlanner.Preset.DIRECTIONAL_T,InterchangePlanner.Preset.Y)) {
      o=new InterchangePlanner.Options(preset,false,1,96,20,5,2);
      var n=Arrays.copyOf(InterchangeValidation.raised(600,0,InterchangePlanner.minimumDifference(s,s,o),false),3);
      var p=InterchangePlanner.plan(n,s,s,o);var stem=p.legs().get(1).mesh();
      check(!RoadJunction.markings(stem,List.of()).isEmpty(),"three-way terminal has taper guidance");
    }
    export(pictures);
    System.out.println("REVISION 14 PASS: "+checks+" geometry/stop/CB-signal/median/light assertions");
  }
  private static void export(List<InterchangePlanner.Plan> plans)throws Exception {
    Files.createDirectories(Path.of("validation"));
    int n=0;
    for(var p:plans) {
      StringBuilder b=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-160 -160 320 320\"><rect x=\"-160\" y=\"-160\" width=\"320\" height=\"320\" fill=\"#47654a\"/>");
      var all=p.legs().stream().map(InterchangePlanner.Leg::mesh).toList();
      var sorted=new ArrayList<Mesh>(all);sorted.sort(Comparator.comparingDouble(m->m.first().center().y()));
      record Layer(List<V> points, String color, double height) {}
      List<Layer> layers=new ArrayList<>();
      for(Mesh m:sorted) {
        var geometry=RoadSurface.build(RoadRenderMesh.simplify(m),List.of(),all.stream().filter(x->x!=m).toList());
        for(var face:geometry.pavement()) { if(face.color()!=0xDCDCDC)continue;
          layers.add(new Layer(face.points(),"#353c40",face.points().stream().mapToDouble(V::y).average().orElse(0))); }
        for(var face:geometry.markings())
          layers.add(new Layer(face.points(),"#efeee0",face.points().stream().mapToDouble(V::y).average().orElse(0)));
      }
      layers.sort(Comparator.comparingDouble(Layer::height));
      for(var layer:layers)poly(b,layer.points(),p.center(),layer.color());
      b.append("</svg>");Files.writeString(Path.of("validation/revision14-"+(n++)+".svg"),b);
    }
  }
  private static void poly(StringBuilder b,List<V> points,V c,String color) {
    if(points.stream().allMatch(v->Math.abs(v.x()-c.x())>170||Math.abs(v.z()-c.z())>170))return;
    b.append("<polygon fill=\"").append(color).append("\" points=\"");
    for(V v:points)b.append(String.format(Locale.ROOT,"%.3f,%.3f ",v.x()-c.x(),v.z()-c.z()));
    b.append("\"/>");
  }
}
