package com.sora.splineroads;

import com.sora.splineroads.core.*;
import com.sora.splineroads.core.RoadGeometry.*;
import com.sora.splineroads.core.RoadStructures.*;
import java.util.*;
import java.nio.file.*;

/** Final rendered paint, C1 shoulder peel, and CB rear mounting regressions. */
public final class Revision17Validation {
  private static int checks;
  private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
  private static Settings settings(Style s){return new Settings(Mode.STRAIGHT,s,s.defaultWidth(),1,.4,90);}
  private static boolean inside(List<V> points,V p){
    double sign=0;for(int i=0;i<points.size();i++){
      V a=points.get(i),b=points.get((i+1)%points.size());
      double cross=(b.x()-a.x())*(p.z()-a.z())-(b.z()-a.z())*(p.x()-a.x());
      if(Math.abs(cross)<1e-7)continue;
      if(sign==0)sign=Math.signum(cross);else if(sign*cross<0)return false;
    }return sign!=0;
  }
  public static void main(String[] args)throws Exception{run();}
  public static void run()throws Exception {
    mounts();
    for(Style style:List.of(Style.O6_GREEN,Style.H6_RAIL))
      for(var preset:List.of(InterchangePlanner.Preset.CLOVERLEAF,InterchangePlanner.Preset.STACK,
          InterchangePlanner.Preset.TRUMPET,InterchangePlanner.Preset.Y))
        for(int lanes:new int[]{1,2})for(boolean left:new boolean[]{false,true}) {
          var s=settings(style);var o=new InterchangePlanner.Options(preset,left,lanes,96,20,5,2);
          Node[] n=InterchangeValidation.raised(500,0,InterchangePlanner.minimumDifference(s,s,o),false);
          if(preset.three)n=Arrays.copyOf(n,3);
          System.out.println("revision17 "+style+" "+preset+" "+lanes+" "+left);
          var plan=InterchangePlanner.plan(n,s,s,o);
          var all=plan.legs().stream().map(l->RoadRenderMesh.simplify(l.mesh())).toList();
          var paint=new ArrayList<RoadSurface.Face>();var surfaces=new ArrayList<RoadSurface.Geometry>();
          // Reverse ramp order every other case, retaining the actual main-before-ramp policy.
          var order=new ArrayList<Mesh>(all.subList(2,all.size()));if(left)Collections.reverse(order);
          order.addAll(0,all.subList(0,2));
          for(int i=0;i<order.size();i++){
            Mesh m=order.get(i);var others=order.stream().filter(x->x!=m).toList();
            var geometry=RoadSurface.build(m,order.subList(0,i),others);
            surfaces.add(geometry);paint.addAll(geometry.markings());
          }
          int contacts=0;
          for(Mesh main:all.subList(0,2))for(double d=10.5;d<main.length()-12;d+=3){
            Sample at=RoadStructures.sample(main,d);var layout=RoadProfile.layout(main,at);
            if(Double.isFinite(RoadTransitions.dropBoundary(main,at)))continue;
            for(int side:new int[]{-1,1}) {
              V edge=at.at(side*at.halfWidth(),0);
              boolean contact=all.stream().filter(m->m.settings().style().ramp()).anyMatch(m->{
                var q=RoadQueries.horizontal(m,edge);
                return Math.abs(q.sample().center().y()-at.center().y())<.10
                    &&q.horizontalDistance()<q.sample().halfWidth()-.02
                    &&Math.abs(q.sample().left().dot(at.left()))>.96;
              });
              if(!contact)continue;
              V boundary=at.at(layout.catalog().type()==RoadProfile.Type.HIGHWAY
                  ?layout.outer(side):side*(at.halfWidth()-.3),0);
              // d = 3k + 1.5 is the middle of a main-owned short dash.
              check(paint.stream().anyMatch(f->Math.abs(f.points().get(0).y()-boundary.y())<.15&&inside(f.points(),boundary)),
                  "auxiliary divider missing: "+preset+" "+style+" lanes="+lanes+" left="+left+" at="+d+" side="+side);
              contacts++;
            }
          }
          check(contacts>10,"real auxiliary contacts checked");
          int chevrons=0;
          for(Mesh ramp:all)if(ramp.settings().style().ramp()) {
            var others=all.stream().filter(x->x!=ramp).toList();
            for(var p:RoadJunction.markings(ramp,others)) {
              V a=p.points().get(0).add(p.points().get(1)).mul(.5);
              V b=p.points().get(2).add(p.points().get(3)).mul(.5);
              V center=a.add(b).mul(.5),direction=b.sub(a).horizontalUnit();
              var q=RoadQueries.horizontal(ramp,center);
              double dot=Math.abs(direction.dot(q.sample().left()));
              if(dot>.15&&dot<.98&&a.distance(b)>.45)chevrons++;
            }
          }
          if(style==Style.H6_RAIL) check(chevrons>=4,"hatched merge/diverge tips exist for "+preset+style+lanes);
          if(preset==InterchangePlanner.Preset.CLOVERLEAF && style==Style.H6_RAIL)for(int i=0;i<all.size();i++) {
            if(!plan.legs().get(i).name().contains("环绕"))continue;
            Mesh loop=all.get(i);var others=all.stream().filter(x->x!=loop).toList();
            var guides=RoadJunction.markings(loop,others);
            for(Sample end:List.of(loop.first(),loop.last())) {
              long count=guides.stream().filter(g->{
                V a=g.points().get(0).add(g.points().get(1)).mul(.5);
                V b=g.points().get(2).add(g.points().get(3)).mul(.5);
                V c=a.add(b).mul(.5);double dot=Math.abs(b.sub(a).horizontalUnit().dot(end.left()));
                return c.sub(end.center()).horizontalLength()<24&&dot>.15&&dot<.98&&a.distance(b)>.45;
              }).count();
              check(count>=2,"both continuous-collector loop mouths have diagonal gore strokes: "+style+lanes+" "+count);
            }
          }
          if(style==Style.H6_RAIL) smoothTurns(plan,left);
          if(!left&&lanes==2)export(preset+"-"+style,surfaces,plan.center());
        }
    System.out.println("REVISION 17 PASS: "+checks+" final-paint/shoulder-tangent/CB-mount assertions across 32 layouts");
  }
  private static void smoothTurns(InterchangePlanner.Plan plan,boolean left){
    for(var leg:plan.legs())if(leg.name().endsWith(left?" 左转":" 右转")) {
      Mesh m=leg.mesh();
      // Locate each attached main; inspect the physical peel, including the former curve seam.
      for(boolean first:new boolean[]{true,false}) {
        Sample end=first?m.first():m.last();
        Mesh main=plan.legs().get((first?leg.from():leg.to())%2).mesh();
        if(end.halfWidth()*2>=m.settings().width()*.9)continue; // shared lead is a separate leg
        V forward=end.left().left().mul(first?-1:1),out=end.left().mul((left?-1:1)*(first?1:-1));
        double last=-1;int points=0;
        for(double d=65;d<Math.min(150,m.length()/3);d+=.5){
          Sample a=RoadStructures.sample(m,first?d:m.length()-d);
          V f=a.left().left().mul(first?-1:1);
          double heading=Math.atan2(f.dot(out),f.dot(forward));
          check(last<0||heading>=last-.003,"right turn must not straighten again after peeling from shoulder: "+leg.name());
          last=heading;points++;
        }
        check(points>20,"shoulder peel inspected");
      }
    }
  }
  private static void mounts(){
    for(double angle:new double[]{0,.37,1.57,2.4}) {
      V f=new V(Math.sin(angle),0,Math.cos(angle)),head=new V(10,71.35,30);
      V post=head.add(f.mul(.42)).add(f.left().mul(7)).add(new V(0,-7.35,0));
      var a=new RoadSignals.Approach(new V(0,64,0),new V(10,64,0),post,head,f,false,0,1,10);
      var parts=RoadSignals.structures(List.of(a));
      Part arm=parts.stream().filter(p->p.material()==Material.CB_ARM).findFirst().orElseThrow();
      check(Math.abs(arm.b().sub(arm.a()).dot(f))<1e-7,"arm is horizontal across the roadway behind housing");
      check(arm.b().sub(head).dot(f)>.4,"housing in front of arm end");
      for(var face:arm.faces())for(V p:face.points()) {
        check(p.sub(head).dot(f)>.28,"crossarm stays behind lamp body and lenses");
        check(p.y()>head.y()+.19&&p.y()<head.y()+.46,"arm meets rear lug at housing middle height");
      }
      check(parts.stream().noneMatch(p->p.material()==Material.STEEL),"no elevated suspension hanger");
    }
  }
  private static void export(String name,List<RoadSurface.Geometry> surfaces,V center)throws Exception{
    StringBuilder out=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-50 -100 560 200\" width=\"2240\" height=\"800\"><rect x=\"-50\" y=\"-100\" width=\"560\" height=\"200\" fill=\"#47654a\"/>");
    var faces=new ArrayList<RoadSurface.Face>();for(var g:surfaces)faces.addAll(g.pavement());
    for(var g:surfaces)faces.addAll(g.markings());
    for(var face:faces){
      if(face.points().stream().allMatch(p->p.x()-center.x()<-50||p.x()-center.x()>510||Math.abs(p.z()-center.z())>100))continue;
      out.append("<polygon fill=\"").append(face.color()==0xDCDCDC?"#626267":face.color()==0xEDEEE2?"#f6f6ed":"#626267").append("\" points=\"");
      for(V v:face.points())out.append(v.x()-center.x()).append(',').append(v.z()-center.z()).append(' ');
      out.append("\"/>");
    }
    out.append("</svg>");Files.createDirectories(Path.of("validation"));Files.writeString(Path.of("validation/revision17-"+name+".svg"),out);
  }
}
